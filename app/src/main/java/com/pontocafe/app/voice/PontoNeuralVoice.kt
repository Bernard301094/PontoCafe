package com.pontocafe.app.voice

import android.content.Context
import android.content.res.AssetManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.delay
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.LinkedHashMap
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

internal enum class PontoNeuralSpeechDecision {
    ACCEPTED,
    SUPPRESSED,
    UNAVAILABLE,
}

internal enum class PontoNeuralVoiceAvailability {
    IDLE,
    PREPARING,
    READY,
    FAILED,
}

internal enum class PontoSpeechBackend {
    NEURAL_PONTOCAFE,
    ANDROID_TTS_FALLBACK,
    NONE,
}

internal enum class PontoNeuralVoiceFailureStage {
    PREPARATION,
    SYNTHESIS,
    PLAYBACK,
}

internal sealed interface PontoNeuralSpeechEvent {
    data object Queued : PontoNeuralSpeechEvent
    data object Synthesizing : PontoNeuralSpeechEvent
    data object SynthesisCompleted : PontoNeuralSpeechEvent
    data object PlaybackStarted : PontoNeuralSpeechEvent
    data object PlaybackCompleted : PontoNeuralSpeechEvent
    data class Failed(
        val stage: PontoNeuralVoiceFailureStage,
        val diagnosticCode: String,
    ) : PontoNeuralSpeechEvent
}

internal object PontoSpeechBackendTracker {
    @Volatile
    private var backend: PontoSpeechBackend = PontoSpeechBackend.NONE

    fun current(): PontoSpeechBackend = backend

    fun mark(value: PontoSpeechBackend) {
        backend = value
    }

    fun reset() {
        backend = PontoSpeechBackend.NONE
    }
}

internal data class PontoNeuralVoiceDiagnostics(
    val availability: PontoNeuralVoiceAvailability,
    val modelInstalled: Boolean,
    val usingAndroidFallback: Boolean,
    val lastSpeechBackend: PontoSpeechBackend,
    val lastFailureAtMillis: Long?,
    val lastFailureReason: String?,
    val retryAvailableInMillis: Long,
    val lastFailureCode: String? = null,
)

internal enum class NaturalVoiceHealthCheckStep {
    STILL_CHECKING,
    CONFIRMED_READY,
    CONFIRMED_FAILED,
    TIMED_OUT_ASSUME_READY,
}

/**
 * Pure decision used by [PontoNeuralVoiceRuntime.awaitStartupHealthCheck]. A
 * cold-start engine rebuild that is merely slow (e.g. mmap'ing the model on a
 * low-RAM kiosk) must never be misclassified as broken: only a definite
 * FAILED availability should trigger recovery, otherwise a previously
 * verified device would be re-prompted for no real reason.
 */
internal fun nextNaturalVoiceHealthCheckStep(
    availability: PontoNeuralVoiceAvailability,
    elapsedMillis: Long,
    timeoutMillis: Long,
): NaturalVoiceHealthCheckStep = when (availability) {
    PontoNeuralVoiceAvailability.READY -> NaturalVoiceHealthCheckStep.CONFIRMED_READY
    PontoNeuralVoiceAvailability.FAILED -> NaturalVoiceHealthCheckStep.CONFIRMED_FAILED
    else -> if (elapsedMillis >= timeoutMillis) {
        NaturalVoiceHealthCheckStep.TIMED_OUT_ASSUME_READY
    } else {
        NaturalVoiceHealthCheckStep.STILL_CHECKING
    }
}

private enum class NeuralVoiceState {
    IDLE,
    PREPARING,
    READY,
    FAILED,
}

private data class CachedNeuralAudio(
    val samples: ShortArray,
    val sampleRate: Int,
)

/**
 * Voz própria do Ponto, executada localmente com sherpa-onnx + Piper/VITS.
 *
 * O modelo pt-BR é empacotado dentro do APK (assets/voice/), baixado e
 * validado por SHA-256 uma única vez em tempo de build — não em cada
 * aparelho. Isso existe porque a instalação em tempo de execução (baixar do
 * GitHub em cada quiosque) já causou falhas reais de campo (VOICE_MODEL_SIZE_INVALID
 * por divergência entre o arquivo servido e o hash fixado no app, reproduzível
 * mesmo fora da rede do quiosque) e deixava o primeiro boot dependente de
 * rede. Na primeira preparação em cada processo, os arquivos são copiados do
 * APK assinado para o armazenamento privado da aplicação (necessário porque o
 * runtime nativo do sherpa-onnx exige caminhos de arquivo reais, não um
 * asset comprimido) e reutilizados sem nova cópia enquanto o hash continuar
 * válido. Quando o modelo já está instalado, uma fala solicitada enquanto o
 * engine ainda está em PREPARING fica enfileirada atrás da inicialização
 * neural em vez de cair imediatamente no Android TTS.
 */
internal object PontoNeuralVoiceRuntime {
    private const val TAG = "PontoCafeVoice"
    private const val MODEL_ASSET_DIR = "voice/vits-piper-pt_BR-faber-medium"
    private const val MODEL_DIR = "vits-piper-pt_BR-faber-medium"
    private const val MODEL_FILE = "pt_BR-faber-medium.onnx"
    // Re-pinned 2026-08-25 to match what app/build.gradle.kts's prepareVoiceModel
    // task actually bundles now — see that task's comment for why the previous
    // constants were stale.
    private const val MODEL_SHA256 = "956b4f1733903891c4ba0973d0603b2a3d8c09c8432fb3bb5203a90a7431daca"
    private const val MODEL_SIZE_BYTES = 63_201_457L
    private const val RETRY_AFTER_MILLIS = 30_000L
    private const val STARTUP_HEALTH_CHECK_TIMEOUT_MILLIS = 4_000L
    private const val STARTUP_HEALTH_CHECK_POLL_INTERVAL_MILLIS = 200L
    private const val CACHE_ENTRIES = 24
    // sherpa-onnx's GenerationConfig.speed is a rate multiplier: 1.0 is the
    // model's native pace, higher is faster, lower is slower. 1.02f (barely
    // above native) read as too fast for a kiosk announcement in practice;
    // slowed down for calmer, easier-to-follow speech at a distance. 0.85f still
    // read as rushed on the shop floor, where ambient noise and distance cost
    // intelligibility; 0.78f keeps the prosody natural without sounding dragged.
    private const val VOICE_SPEED = 0.78f
    private const val SILENCE_SCALE = 0.18f
    private const val WRITE_CHUNK_SAMPLES = 8_192

    private val lock = Any()
    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "PontoCafe-NeuralVoice").apply { isDaemon = true }
    }
    private val gate = PontoVoiceGate()
    private val cache = object : LinkedHashMap<String, CachedNeuralAudio>(CACHE_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedNeuralAudio>?): Boolean =
            size > CACHE_ENTRIES
    }

    @Volatile
    private var state = NeuralVoiceState.IDLE

    @Volatile
    private var engine: OfflineTts? = null

    @Volatile
    private var currentTrack: AudioTrack? = null

    private var lastFailureAtMillis = 0L
    private var lastFailureReason: String? = null
    private var lastFailureCode: String? = null
    private var lifecycleVersion = 0L
    private var utteranceVersion = 0L

    fun prewarm(context: Context) {
        ensurePreparing(context.applicationContext)
    }

    fun diagnostics(context: Context): PontoNeuralVoiceDiagnostics {
        val appContext = context.applicationContext
        val modelDir = modelDirectory(appContext)
        val now = System.currentTimeMillis()
        synchronized(lock) {
            val retryIn = if (state == NeuralVoiceState.FAILED && lastFailureAtMillis > 0L) {
                (RETRY_AFTER_MILLIS - (now - lastFailureAtMillis)).coerceAtLeast(0L)
            } else {
                0L
            }
            val lastBackend = PontoSpeechBackendTracker.current()
            return PontoNeuralVoiceDiagnostics(
                availability = state.toAvailability(),
                modelInstalled = modelReadyOnDisk(modelDir),
                usingAndroidFallback = lastBackend == PontoSpeechBackend.ANDROID_TTS_FALLBACK,
                lastSpeechBackend = lastBackend,
                lastFailureAtMillis = lastFailureAtMillis.takeIf { it > 0L },
                lastFailureReason = lastFailureReason,
                retryAvailableInMillis = retryIn,
                lastFailureCode = lastFailureCode,
            )
        }
    }

    fun retryNow(context: Context) {
        synchronized(lock) {
            if (state == NeuralVoiceState.FAILED) {
                state = NeuralVoiceState.IDLE
                lastFailureAtMillis = 0L
                lastFailureReason = null
                lastFailureCode = null
            }
        }
        ensurePreparing(context.applicationContext)
    }

    /**
     * Awaits a fresh-process confirmation that a previously verified engine
     * is actually usable again, instead of trusting a persisted "verified"
     * flag forever. Returns true when the engine reaches READY within
     * [timeoutMillis] (or is still merely initializing once the timeout
     * elapses — a slow load is not a broken one), false only on a definite
     * FAILED. Callers should invalidate any persisted provisioning flag and
     * re-surface setup UI when this returns false.
     */
    suspend fun awaitStartupHealthCheck(
        context: Context,
        timeoutMillis: Long = STARTUP_HEALTH_CHECK_TIMEOUT_MILLIS,
    ): Boolean {
        val appContext = context.applicationContext
        prewarm(appContext)
        val startedAtMillis = System.currentTimeMillis()
        while (true) {
            val elapsed = System.currentTimeMillis() - startedAtMillis
            when (
                nextNaturalVoiceHealthCheckStep(
                    availability = diagnostics(appContext).availability,
                    elapsedMillis = elapsed,
                    timeoutMillis = timeoutMillis,
                )
            ) {
                NaturalVoiceHealthCheckStep.CONFIRMED_READY,
                NaturalVoiceHealthCheckStep.TIMED_OUT_ASSUME_READY -> return true
                NaturalVoiceHealthCheckStep.CONFIRMED_FAILED -> return false
                NaturalVoiceHealthCheckStep.STILL_CHECKING -> delay(STARTUP_HEALTH_CHECK_POLL_INTERVAL_MILLIS)
            }
        }
    }

    /**
     * ACCEPTED means only that the utterance was admitted to the neural worker.
     * Observable completion is delivered exclusively through [onEvent].
     */
    fun speak(
        context: Context,
        prompt: PontoVoicePrompt,
        sessionKey: String?,
        onFailure: (() -> Unit)? = null,
        onEvent: ((PontoNeuralSpeechEvent) -> Unit)? = null,
    ): PontoNeuralSpeechDecision {
        val appContext = context.applicationContext
        val normalizedText = PontoVoiceTextNormalizer.normalize(prompt.text)
        if (normalizedText.isBlank()) return PontoNeuralSpeechDecision.SUPPRESSED

        var currentEngine = engine
        if (state != NeuralVoiceState.READY || currentEngine == null) {
            val installed = modelReadyOnDisk(modelDirectory(appContext))
            ensurePreparing(appContext)

            // First installation can involve a large download. Do not hold an
            // operational prompt behind that download; Android TTS remains the
            // fail-open path until the model exists locally.
            if (!installed) return PontoNeuralSpeechDecision.UNAVAILABLE

            // The preparation task and this speech task share the same single
            // worker. When PREPARING, the utterance is therefore guaranteed to
            // run only after the engine preparation ahead of it has completed.
            val canQueueBehindPreparation = synchronized(lock) {
                state == NeuralVoiceState.PREPARING ||
                    (state == NeuralVoiceState.READY && engine != null)
            }
            if (!canQueueBehindPreparation) return PontoNeuralSpeechDecision.UNAVAILABLE
            currentEngine = null
        }

        val now = System.currentTimeMillis()
        val lifecycle: Long
        val utterance: Long
        synchronized(lock) {
            if (!gate.canSpeak(prompt, now, sessionKey)) {
                return PontoNeuralSpeechDecision.SUPPRESSED
            }

            val playing = currentTrack?.playState == AudioTrack.PLAYSTATE_PLAYING
            if (playing && !prompt.interrupt) {
                return PontoNeuralSpeechDecision.SUPPRESSED
            }

            if (playing && prompt.interrupt) {
                runCatching { currentTrack?.pause() }
                runCatching { currentTrack?.flush() }
                runCatching { currentTrack?.stop() }
            }

            gate.markSpoken(prompt, now, sessionKey)
            lifecycle = lifecycleVersion
            utteranceVersion += 1L
            utterance = utteranceVersion
        }

        emitEvent(onEvent, PontoNeuralSpeechEvent.Queued)
        worker.execute {
            if (!isCurrent(lifecycle, utterance)) {
                emitEvent(
                    onEvent,
                    PontoNeuralSpeechEvent.Failed(
                        PontoNeuralVoiceFailureStage.PLAYBACK,
                        "VOICE_SUPERSEDED",
                    ),
                )
                return@execute
            }

            val executionEngine = currentEngine ?: synchronized(lock) {
                if (state == NeuralVoiceState.READY) engine else null
            }
            if (executionEngine == null) {
                emitEvent(
                    onEvent,
                    PontoNeuralSpeechEvent.Failed(
                        PontoNeuralVoiceFailureStage.PREPARATION,
                        "VOICE_ENGINE_NOT_READY",
                    ),
                )
                runCatching { onFailure?.invoke() }
                return@execute
            }

            emitEvent(onEvent, PontoNeuralSpeechEvent.Synthesizing)
            val audio = try {
                synchronized(lock) { cache[normalizedText] }
                    ?: synthesize(executionEngine, normalizedText).also { generated ->
                        synchronized(lock) { cache[normalizedText] = generated }
                    }
            } catch (error: Throwable) {
                Log.e(TAG, "VOICE_SYNTHESIS_FAILED", error)
                markEngineFailure(executionEngine, error)
                emitEvent(
                    onEvent,
                    PontoNeuralSpeechEvent.Failed(
                        PontoNeuralVoiceFailureStage.SYNTHESIS,
                        diagnosticCode(error, "VOICE_SYNTHESIS_FAILED"),
                    ),
                )
                runCatching { onFailure?.invoke() }
                return@execute
            }
            emitEvent(onEvent, PontoNeuralSpeechEvent.SynthesisCompleted)

            if (!isCurrent(lifecycle, utterance)) {
                emitEvent(
                    onEvent,
                    PontoNeuralSpeechEvent.Failed(
                        PontoNeuralVoiceFailureStage.PLAYBACK,
                        "VOICE_SUPERSEDED",
                    ),
                )
                return@execute
            }

            try {
                val completed = play(
                    audio = audio,
                    lifecycle = lifecycle,
                    utterance = utterance,
                    onStarted = { emitEvent(onEvent, PontoNeuralSpeechEvent.PlaybackStarted) },
                )
                if (completed) {
                    PontoSpeechBackendTracker.mark(PontoSpeechBackend.NEURAL_PONTOCAFE)
                    emitEvent(onEvent, PontoNeuralSpeechEvent.PlaybackCompleted)
                } else {
                    emitEvent(
                        onEvent,
                        PontoNeuralSpeechEvent.Failed(
                            PontoNeuralVoiceFailureStage.PLAYBACK,
                            "VOICE_PLAYBACK_INTERRUPTED",
                        ),
                    )
                }
            } catch (error: Throwable) {
                // Falha de AudioTrack não invalida o modelo/engine. O fallback
                // Android continua disponível para a fala operacional normal.
                Log.e(TAG, "VOICE_PLAYBACK_FAILED", error)
                emitEvent(
                    onEvent,
                    PontoNeuralSpeechEvent.Failed(
                        PontoNeuralVoiceFailureStage.PLAYBACK,
                        diagnosticCode(error, "VOICE_PLAYBACK_FAILED"),
                    ),
                )
                if (isCurrent(lifecycle, utterance)) {
                    runCatching { onFailure?.invoke() }
                }
            }
        }
        return PontoNeuralSpeechDecision.ACCEPTED
    }

    fun shutdown() {
        val engineToRelease: OfflineTts?
        synchronized(lock) {
            lifecycleVersion += 1L
            utteranceVersion += 1L
            runCatching { currentTrack?.pause() }
            runCatching { currentTrack?.flush() }
            runCatching { currentTrack?.stop() }
            currentTrack = null
            engineToRelease = engine
            engine = null
            state = NeuralVoiceState.IDLE
            cache.clear()
            gate.reset()
        }
        PontoSpeechBackendTracker.reset()
        if (engineToRelease != null) {
            worker.execute { runCatching { engineToRelease.release() } }
        }
    }

    private fun ensurePreparing(context: Context) {
        val now = System.currentTimeMillis()
        val version: Long
        synchronized(lock) {
            when (state) {
                NeuralVoiceState.READY,
                NeuralVoiceState.PREPARING -> return
                NeuralVoiceState.FAILED -> if (now - lastFailureAtMillis < RETRY_AFTER_MILLIS) return
                NeuralVoiceState.IDLE -> Unit
            }
            state = NeuralVoiceState.PREPARING
            version = lifecycleVersion
        }

        Log.i(TAG, "VOICE_PREPARING")
        worker.execute {
            var prepared: OfflineTts? = null
            try {
                val modelDir = ensureModelInstalled(context)
                Log.i(TAG, "VOICE_MODEL_READY path=${modelDir.name}")
                prepared = createEngine(modelDir)
                synchronized(lock) {
                    if (version != lifecycleVersion) {
                        return@synchronized
                    }
                    engine?.let { old -> runCatching { old.release() } }
                    engine = prepared
                    prepared = null
                    state = NeuralVoiceState.READY
                    lastFailureAtMillis = 0L
                    lastFailureReason = null
                    lastFailureCode = null
                }
                Log.i(TAG, "VOICE_ENGINE_READY")
            } catch (error: Throwable) {
                val code = diagnosticCode(error, "VOICE_PREPARE_FAILED")
                Log.e(TAG, "$code VOICE_PREPARE_FAILED", error)
                synchronized(lock) {
                    if (version == lifecycleVersion) {
                        state = NeuralVoiceState.FAILED
                        lastFailureAtMillis = System.currentTimeMillis()
                        lastFailureReason = classifyVoiceFailure(error)
                        lastFailureCode = code
                    }
                }
            } finally {
                prepared?.let { runCatching { it.release() } }
            }
        }
    }

    private fun createEngine(modelDir: File): OfflineTts {
        val model = File(modelDir, MODEL_FILE)
        val tokens = File(modelDir, "tokens.txt")
        val dataDir = File(modelDir, "espeak-ng-data")
        check(model.isFile) { "VOICE_MODEL_FILE_MISSING" }
        check(tokens.isFile) { "VOICE_TOKENS_MISSING" }
        check(dataDir.isDirectory) { "VOICE_ESPEAK_DATA_MISSING" }

        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = model.absolutePath,
                    tokens = tokens.absolutePath,
                    dataDir = dataDir.absolutePath,
                ),
                // A configuração oficial do modelo Faber usa 1 thread. Mantém
                // previsibilidade e reduz pressão concorrente no modo Ponto.
                numThreads = 1,
                debug = false,
                provider = "cpu",
            ),
            maxNumSentences = 1,
        )
        return OfflineTts(config = config)
    }

    private fun synthesize(tts: OfflineTts, text: String): CachedNeuralAudio {
        val generated = tts.generateWithConfig(
            text = text,
            config = GenerationConfig(
                sid = 0,
                speed = VOICE_SPEED,
                silenceScale = SILENCE_SCALE,
            ),
        )
        check(generated.samples.isNotEmpty() && generated.sampleRate > 0) {
            "VOICE_EMPTY_AUDIO"
        }
        val pcm = ShortArray(generated.samples.size) { index ->
            val sample = generated.samples[index].coerceIn(-1f, 1f)
            (sample * Short.MAX_VALUE).toInt().toShort()
        }
        return CachedNeuralAudio(samples = pcm, sampleRate = generated.sampleRate)
    }

    private fun play(
        audio: CachedNeuralAudio,
        lifecycle: Long,
        utterance: Long,
        onStarted: () -> Unit,
    ): Boolean {
        val minBuffer = AudioTrack.getMinBufferSize(
            audio.sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        check(minBuffer > 0) { "VOICE_AUDIO_BUFFER_INVALID" }

        val bufferBytes = max(minBuffer, WRITE_CHUNK_SAMPLES * 2)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(audio.sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(bufferBytes)
            .build()

        check(track.state == AudioTrack.STATE_INITIALIZED) { "VOICE_AUDIO_TRACK_INIT_FAILED" }

        synchronized(lock) {
            if (!isCurrentLocked(lifecycle, utterance)) {
                track.release()
                return false
            }
            currentTrack = track
        }

        try {
            track.play()
            check(track.playState == AudioTrack.PLAYSTATE_PLAYING) { "VOICE_AUDIO_TRACK_NOT_PLAYING" }
            onStarted()

            var offset = 0
            while (offset < audio.samples.size && isCurrent(lifecycle, utterance)) {
                val count = min(WRITE_CHUNK_SAMPLES, audio.samples.size - offset)
                val written = track.write(
                    audio.samples,
                    offset,
                    count,
                    AudioTrack.WRITE_BLOCKING,
                )
                check(written > 0) { "VOICE_AUDIO_WRITE_FAILED:$written" }
                offset += written
            }

            if (!isCurrent(lifecycle, utterance) || offset < audio.samples.size) return false

            while (
                isCurrent(lifecycle, utterance) &&
                track.playState == AudioTrack.PLAYSTATE_PLAYING &&
                track.playbackHeadPosition.toLong() < audio.samples.size.toLong()
            ) {
                Thread.sleep(20L)
            }

            return isCurrent(lifecycle, utterance) &&
                track.playbackHeadPosition.toLong() >= audio.samples.size.toLong()
        } finally {
            runCatching { track.stop() }
            runCatching { track.flush() }
            runCatching { track.release() }
            synchronized(lock) {
                if (currentTrack === track) currentTrack = null
            }
        }
    }

    private fun markEngineFailure(failedEngine: OfflineTts, error: Throwable) {
        val code = diagnosticCode(error, "VOICE_ENGINE_FAILED")
        synchronized(lock) {
            if (engine !== failedEngine) return
            engine = null
            state = NeuralVoiceState.FAILED
            lastFailureAtMillis = System.currentTimeMillis()
            lastFailureReason = classifyVoiceFailure(error)
            lastFailureCode = code
            cache.clear()
        }
        Log.e(TAG, "$code VOICE_ENGINE_FAILED", error)
        runCatching { failedEngine.release() }
    }

    private fun modelDirectory(context: Context): File =
        File(File(context.filesDir, "pontocafe-voice"), MODEL_DIR)

    private fun modelReadyOnDisk(modelDir: File): Boolean {
        val marker = File(modelDir, ".ready-$MODEL_SHA256")
        val model = File(modelDir, MODEL_FILE)
        val tokens = File(modelDir, "tokens.txt")
        val dataDir = File(modelDir, "espeak-ng-data")
        return marker.isFile && model.isFile && model.length() == MODEL_SIZE_BYTES &&
            tokens.isFile && dataDir.isDirectory
    }

    private fun ensureModelInstalled(context: Context): File {
        val parent = File(context.filesDir, "pontocafe-voice").apply { mkdirs() }
        val finalDir = File(parent, MODEL_DIR)
        val marker = File(finalDir, ".ready-$MODEL_SHA256")
        val existingModel = File(finalDir, MODEL_FILE)
        val existingTokens = File(finalDir, "tokens.txt")
        val existingDataDir = File(finalDir, "espeak-ng-data")

        if (
            existingModel.isFile && existingModel.length() == MODEL_SIZE_BYTES &&
            existingTokens.isFile && existingDataDir.isDirectory
        ) {
            // The hash is always recomputed on reuse, even when a marker file
            // is present: a marker byte can survive disk corruption that the
            // model weights themselves do not, and trusting it blindly would
            // let a broken install look "installed" forever across restarts.
            if (sha256(existingModel).equals(MODEL_SHA256, ignoreCase = true)) {
                if (!marker.isFile) marker.writeText("$MODEL_SHA256\n")
                Log.i(TAG, "VOICE_MODEL_REUSED")
                return finalDir
            }
        }

        // Uma instalação incompleta nunca é reaproveitada. A cópia vem do APK
        // assinado (assets/voice/), não de rede — uma única tentativa limpa é
        // suficiente; uma falha aqui é um problema real de armazenamento do
        // aparelho, não flakiness de rede que valeria a pena repetir.
        finalDir.deleteRecursively()
        try {
            copyModelFromAssets(context, finalDir)

            val model = File(finalDir, MODEL_FILE)
            val tokens = File(finalDir, "tokens.txt")
            val dataDir = File(finalDir, "espeak-ng-data")

            check(model.isFile && model.length() == MODEL_SIZE_BYTES) {
                "VOICE_MODEL_SIZE_INVALID:${model.length()}"
            }
            check(tokens.isFile) { "VOICE_TOKENS_MISSING_AFTER_INSTALL" }
            check(dataDir.isDirectory) { "VOICE_ESPEAK_DATA_MISSING_AFTER_INSTALL" }
            check(sha256(model).equals(MODEL_SHA256, ignoreCase = true)) {
                "VOICE_MODEL_HASH_INVALID"
            }

            marker.writeText("$MODEL_SHA256\n")
            Log.i(TAG, "VOICE_MODEL_INSTALLED")
            return finalDir
        } catch (error: Throwable) {
            val code = diagnosticCode(error, "VOICE_MODEL_INSTALL_FAILED")
            Log.e(TAG, "$code VOICE_MODEL_INSTALL_FAILED", error)
            finalDir.deleteRecursively()
            throw error
        }
    }

    /**
     * Copies the model tree bundled at build time (app/build.gradle.kts's
     * prepareVoiceModel task, assets/voice/) into app-private storage. A real
     * filesystem path is required here — sherpa-onnx's native layer opens the
     * model/tokens/espeak-ng-data files directly by absolute path, which an
     * asset entry inside the APK zip cannot provide.
     */
    private fun copyModelFromAssets(context: Context, destinationDir: File) {
        val assets = context.applicationContext.assets
        check(!assets.list(MODEL_ASSET_DIR).isNullOrEmpty()) { "VOICE_MODEL_ASSET_MISSING" }
        copyAssetTree(assets, MODEL_ASSET_DIR, destinationDir)
    }

    private fun copyAssetTree(assets: AssetManager, sourcePath: String, destination: File) {
        val children = assets.list(sourcePath)
        if (children.isNullOrEmpty()) {
            destination.parentFile?.let { parent ->
                check(parent.mkdirs() || parent.isDirectory) { "VOICE_MODEL_INSTALL_MKDIR_FAILED" }
            }
            assets.open(sourcePath).use { input ->
                BufferedOutputStream(FileOutputStream(destination)).use { output ->
                    input.copyTo(output)
                }
            }
            return
        }
        check(destination.mkdirs() || destination.isDirectory) { "VOICE_MODEL_INSTALL_MKDIR_FAILED" }
        for (child in children) {
            copyAssetTree(assets, "$sourcePath/$child", File(destination, child))
        }
    }

    private fun isVoiceDiagnostic(error: Throwable): Boolean =
        error.message.orEmpty().trim().uppercase().startsWith("VOICE_")

    private fun emitEvent(
        listener: ((PontoNeuralSpeechEvent) -> Unit)?,
        event: PontoNeuralSpeechEvent,
    ) {
        runCatching { listener?.invoke(event) }
    }

    private fun diagnosticCode(error: Throwable, fallback: String): String {
        val raw = error.message?.trim().orEmpty()
        return raw
            .takeIf { it.matches(Regex("[A-Z0-9_:-]{3,160}")) }
            ?: fallback
    }

    private fun classifyVoiceFailure(error: Throwable): String {
        val code = error.message.orEmpty().uppercase()
        return when {
            "ASSET_MISSING" in code ->
                "Os arquivos da voz neural não foram encontrados neste aplicativo instalado."
            "HASH" in code || "SIZE" in code ->
                "Os arquivos da voz neural embutidos no aplicativo estão incompletos ou incompatíveis."
            "TOKENS" in code || "ESPEAK" in code || "MODEL" in code ->
                "Os arquivos necessários da voz neural estão incompletos."
            "AUDIO" in code ->
                "O áudio neural não pôde ser preparado neste aparelho."
            else ->
                "O motor neural da voz PontoCafe não pôde ser inicializado."
        }
    }

    private fun NeuralVoiceState.toAvailability(): PontoNeuralVoiceAvailability = when (this) {
        NeuralVoiceState.IDLE -> PontoNeuralVoiceAvailability.IDLE
        NeuralVoiceState.PREPARING -> PontoNeuralVoiceAvailability.PREPARING
        NeuralVoiceState.READY -> PontoNeuralVoiceAvailability.READY
        NeuralVoiceState.FAILED -> PontoNeuralVoiceAvailability.FAILED
    }

    private fun isCurrent(lifecycle: Long, utterance: Long): Boolean = synchronized(lock) {
        isCurrentLocked(lifecycle, utterance)
    }

    private fun isCurrentLocked(lifecycle: Long, utterance: Long): Boolean =
        lifecycle == lifecycleVersion && utterance == utteranceVersion

    internal fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count <= 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }
}
