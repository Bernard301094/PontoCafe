package com.pontocafe.app.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
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

private data class PendingArchiveLink(
    val destination: File,
    val linkName: String,
    val symbolic: Boolean,
)

/**
 * Voz própria do Ponto, executada localmente com sherpa-onnx + Piper/VITS.
 *
 * O modelo pt-BR não é empacotado no APK: ele é baixado uma única vez para o
 * armazenamento privado da aplicação, validado por SHA-256 e reutilizado sem
 * internet. Quando o modelo já está instalado, uma fala solicitada enquanto o
 * engine ainda está em PREPARING fica enfileirada atrás da inicialização neural
 * em vez de cair imediatamente no Android TTS. Enquanto o primeiro download
 * ainda não terminou, o chamador continua livre para usar o fallback Android.
 */
internal object PontoNeuralVoiceRuntime {
    private const val TAG = "PontoCafeVoice"
    private const val MODEL_DIR = "vits-piper-pt_BR-faber-medium"
    private const val MODEL_FILE = "pt_BR-faber-medium.onnx"
    private const val MODEL_URL =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-pt_BR-faber-medium.tar.bz2"
    private const val MODEL_SHA256 = "39fb6b580d6d40a3230b7a9d0851d282074537b9694892b5b3cd90ff87c6cbb3"
    private const val MODEL_SIZE_BYTES = 63_201_428L
    private const val MAX_ARCHIVE_BYTES = 120L * 1024L * 1024L
    private const val MAX_EXTRACTED_BYTES = 160L * 1024L * 1024L
    private const val MODEL_INSTALL_ATTEMPTS = 3
    private const val MODEL_INSTALL_RETRY_BASE_MILLIS = 750L
    private const val DOWNLOAD_CONNECT_TIMEOUT_MILLIS = 20_000
    private const val DOWNLOAD_READ_TIMEOUT_MILLIS = 120_000
    private const val RETRY_AFTER_MILLIS = 30_000L
    private const val CACHE_ENTRIES = 24
    private const val VOICE_SPEED = 1.02f
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
            if (marker.isFile || sha256(existingModel).equals(MODEL_SHA256, ignoreCase = true)) {
                if (!marker.isFile) marker.writeText("$MODEL_SHA256\n")
                Log.i(TAG, "VOICE_MODEL_REUSED")
                return finalDir
            }
        }

        // Uma instalação incompleta nunca é reaproveitada. Cada tentativa usa
        // cache próprio e só promove o diretório depois de tamanho + SHA-256.
        finalDir.deleteRecursively()
        var lastError: Throwable? = null

        for (attempt in 1..MODEL_INSTALL_ATTEMPTS) {
            val workRoot = File(context.cacheDir, "pontocafe-voice-install-$attempt").apply {
                deleteRecursively()
                mkdirs()
            }
            val archive = File(workRoot, "$MODEL_DIR.tar.bz2")
            val extracted = File(workRoot, "extracted").apply { mkdirs() }

            try {
                Log.i(TAG, "VOICE_DOWNLOAD_START attempt=$attempt/$MODEL_INSTALL_ATTEMPTS")
                downloadModel(archive)
                Log.i(TAG, "VOICE_DOWNLOAD_DONE attempt=$attempt bytes=${archive.length()}")
                extractArchive(archive, extracted)

                val extractedModel = extracted.walkTopDown()
                    .firstOrNull { it.isFile && it.name == MODEL_FILE }
                    ?: error("VOICE_MODEL_NOT_FOUND_AFTER_EXTRACT")
                val extractedModelDir = extractedModel.parentFile
                    ?: error("VOICE_MODEL_PARENT_MISSING")
                val model = File(extractedModelDir, MODEL_FILE)
                val tokens = File(extractedModelDir, "tokens.txt")
                val dataDir = File(extractedModelDir, "espeak-ng-data")

                check(model.isFile && model.length() == MODEL_SIZE_BYTES) {
                    "VOICE_MODEL_SIZE_INVALID:${model.length()}"
                }
                check(tokens.isFile) { "VOICE_TOKENS_MISSING_AFTER_EXTRACT" }
                check(dataDir.isDirectory) { "VOICE_ESPEAK_DATA_MISSING_AFTER_EXTRACT" }
                check(sha256(model).equals(MODEL_SHA256, ignoreCase = true)) {
                    "VOICE_MODEL_HASH_INVALID"
                }

                parent.mkdirs()
                finalDir.deleteRecursively()
                if (!extractedModelDir.renameTo(finalDir)) {
                    check(extractedModelDir.copyRecursively(finalDir, overwrite = true)) {
                        "VOICE_MODEL_INSTALL_COPY_FAILED"
                    }
                }
                File(finalDir, ".ready-$MODEL_SHA256").writeText("$MODEL_SHA256\n")
                Log.i(TAG, "VOICE_MODEL_INSTALLED attempt=$attempt")
                return finalDir
            } catch (error: Throwable) {
                lastError = error
                val code = diagnosticCode(error, "VOICE_MODEL_INSTALL_FAILED")
                Log.w(TAG, "$code attempt=$attempt/$MODEL_INSTALL_ATTEMPTS", error)
                finalDir.deleteRecursively()

                if (attempt >= MODEL_INSTALL_ATTEMPTS || !isRetriableModelInstallFailure(error)) {
                    throw error
                }
                Thread.sleep(MODEL_INSTALL_RETRY_BASE_MILLIS * attempt)
            } finally {
                workRoot.deleteRecursively()
            }
        }

        throw lastError ?: IllegalStateException("VOICE_MODEL_INSTALL_FAILED")
    }

    private fun downloadModel(destination: File) {
        destination.parentFile?.mkdirs()
        val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = DOWNLOAD_CONNECT_TIMEOUT_MILLIS
            readTimeout = DOWNLOAD_READ_TIMEOUT_MILLIS
            instanceFollowRedirects = true
            useCaches = false
            requestMethod = "GET"
            setRequestProperty("User-Agent", "PontoCafe-Android/1.0")
            setRequestProperty("Accept", "application/octet-stream,*/*")
            // Evita que proxies/camadas HTTP transformem o corpo binário e
            // tornem Content-Length incompatível com os bytes persistidos.
            setRequestProperty("Accept-Encoding", "identity")
        }

        try {
            try {
                connection.connect()
                check(connection.responseCode in 200..299) {
                    "VOICE_DOWNLOAD_HTTP_${connection.responseCode}"
                }

                val contentType = connection.contentType.orEmpty().lowercase()
                check(!contentType.contains("text/html")) {
                    "VOICE_DOWNLOAD_UNEXPECTED_CONTENT_TYPE"
                }

                val advertisedSize = connection.contentLengthLong
                check(advertisedSize <= 0L || advertisedSize <= MAX_ARCHIVE_BYTES) {
                    "VOICE_ARCHIVE_TOO_LARGE:$advertisedSize"
                }

                var total = 0L
                BufferedInputStream(connection.inputStream).use { input ->
                    BufferedOutputStream(FileOutputStream(destination)).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count <= 0) break
                            total += count
                            check(total <= MAX_ARCHIVE_BYTES) {
                                "VOICE_ARCHIVE_LIMIT_EXCEEDED"
                            }
                            output.write(buffer, 0, count)
                        }
                        output.flush()
                    }
                }

                check(total > 0L) { "VOICE_DOWNLOAD_EMPTY" }
                check(advertisedSize <= 0L || total == advertisedSize) {
                    "VOICE_DOWNLOAD_LENGTH_MISMATCH:$total:$advertisedSize"
                }
            } catch (error: Throwable) {
                if (isVoiceDiagnostic(error)) throw error
                throw IllegalStateException("VOICE_DOWNLOAD_IO_FAILED", error)
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun extractArchive(archive: File, destinationRoot: File) {
        try {
            val safeRoot = destinationRoot.canonicalFile
            var extractedBytes = 0L
            val pendingLinks = mutableListOf<PendingArchiveLink>()

            BZip2CompressorInputStream(BufferedInputStream(FileInputStream(archive))).use { bzip ->
                TarArchiveInputStream(bzip).use { tar ->
                    while (true) {
                        val entry = tar.nextEntry ?: break
                        val destination = safeArchiveDestination(safeRoot, entry.name)

                        if (entry.isSymbolicLink || entry.isLink) {
                            // Os pacotes oficiais podem preservar links internos.
                            // Em vez de rejeitar o arquivo inteiro, validamos o
                            // alvo e materializamos o conteúdo dentro do sandbox.
                            pendingLinks += PendingArchiveLink(
                                destination = destination,
                                linkName = entry.linkName.orEmpty(),
                                symbolic = entry.isSymbolicLink,
                            )
                            continue
                        }

                        if (entry.isDirectory) {
                            check(destination.mkdirs() || destination.isDirectory) {
                                "VOICE_ARCHIVE_DIRECTORY_CREATE_FAILED"
                            }
                            continue
                        }

                        // Cabeçalhos/metadados TAR que não representam arquivo
                        // comum não são necessários para o modelo.
                        if (!entry.isFile) continue

                        destination.parentFile?.let { parent ->
                            check(parent.mkdirs() || parent.isDirectory) {
                                "VOICE_ARCHIVE_PARENT_CREATE_FAILED"
                            }
                        }
                        BufferedOutputStream(FileOutputStream(destination)).use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val count = tar.read(buffer)
                                if (count <= 0) break
                                extractedBytes += count
                                check(extractedBytes <= MAX_EXTRACTED_BYTES) {
                                    "VOICE_EXTRACTED_LIMIT_EXCEEDED"
                                }
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                }
            }

            extractedBytes += materializeArchiveLinks(
                safeRoot = safeRoot,
                pendingLinks = pendingLinks,
                remainingBudget = MAX_EXTRACTED_BYTES - extractedBytes,
            )
            check(extractedBytes <= MAX_EXTRACTED_BYTES) {
                "VOICE_EXTRACTED_LIMIT_EXCEEDED"
            }
        } catch (error: Throwable) {
            if (isVoiceDiagnostic(error)) throw error
            throw IllegalStateException("VOICE_ARCHIVE_READ_FAILED", error)
        }
    }

    private fun materializeArchiveLinks(
        safeRoot: File,
        pendingLinks: List<PendingArchiveLink>,
        remainingBudget: Long,
    ): Long {
        if (pendingLinks.isEmpty()) return 0L
        check(remainingBudget >= 0L) { "VOICE_EXTRACTED_LIMIT_EXCEEDED" }

        val unresolved = pendingLinks.toMutableList()
        var copiedBytes = 0L

        while (unresolved.isNotEmpty()) {
            var progressed = false
            val iterator = unresolved.iterator()

            while (iterator.hasNext()) {
                val link = iterator.next()
                val target = archiveLinkTarget(safeRoot, link)
                if (!target.exists()) continue

                val remaining = remainingBudget - copiedBytes
                check(remaining >= 0L) { "VOICE_EXTRACTED_LIMIT_EXCEEDED" }
                copiedBytes += materializeArchiveTarget(
                    source = target,
                    destination = link.destination,
                    byteBudget = remaining,
                )
                check(copiedBytes <= remainingBudget) {
                    "VOICE_EXTRACTED_LIMIT_EXCEEDED"
                }
                iterator.remove()
                progressed = true
            }

            check(progressed) {
                val first = unresolved.firstOrNull()?.linkName.orEmpty().take(80)
                "VOICE_ARCHIVE_LINK_TARGET_MISSING:$first"
            }
        }

        Log.i(TAG, "VOICE_ARCHIVE_LINKS_MATERIALIZED count=${pendingLinks.size} bytes=$copiedBytes")
        return copiedBytes
    }

    private fun archiveLinkTarget(safeRoot: File, link: PendingArchiveLink): File {
        val name = link.linkName.trim()
        check(name.isNotEmpty()) { "VOICE_ARCHIVE_LINK_NAME_EMPTY" }
        check(!File(name).isAbsolute) { "VOICE_ARCHIVE_LINK_OUTSIDE_ROOT" }

        val base = if (link.symbolic) {
            link.destination.parentFile ?: safeRoot
        } else {
            safeRoot
        }
        val target = File(base, name).canonicalFile
        check(isInsideRoot(safeRoot, target)) { "VOICE_ARCHIVE_LINK_OUTSIDE_ROOT" }
        check(target.path != link.destination.path) { "VOICE_ARCHIVE_LINK_CYCLE" }
        return target
    }

    private fun materializeArchiveTarget(
        source: File,
        destination: File,
        byteBudget: Long,
    ): Long {
        check(byteBudget >= 0L) { "VOICE_EXTRACTED_LIMIT_EXCEEDED" }
        destination.parentFile?.let { parent ->
            check(parent.mkdirs() || parent.isDirectory) {
                "VOICE_ARCHIVE_PARENT_CREATE_FAILED"
            }
        }
        destination.deleteRecursively()

        if (source.isFile) {
            return copyFileWithBudget(source, destination, byteBudget)
        }

        check(source.isDirectory) { "VOICE_ARCHIVE_LINK_TARGET_INVALID" }
        val sourcePath = source.canonicalPath
        val destinationPath = destination.canonicalPath
        check(
            !destinationPath.startsWith(sourcePath + File.separator) &&
                !sourcePath.startsWith(destinationPath + File.separator),
        ) { "VOICE_ARCHIVE_LINK_CYCLE" }

        // Snapshot antes de criar o destino evita que uma cópia de diretório
        // passe a enxergar os próprios arquivos recém-criados.
        val snapshot = source.walkTopDown().toList()
        var copied = 0L
        for (item in snapshot) {
            val relative = item.relativeTo(source).path
            val target = if (relative.isBlank()) destination else File(destination, relative)
            if (item.isDirectory) {
                check(target.mkdirs() || target.isDirectory) {
                    "VOICE_ARCHIVE_DIRECTORY_CREATE_FAILED"
                }
            } else if (item.isFile) {
                val remaining = byteBudget - copied
                check(remaining >= 0L) { "VOICE_EXTRACTED_LIMIT_EXCEEDED" }
                target.parentFile?.let { parent ->
                    check(parent.mkdirs() || parent.isDirectory) {
                        "VOICE_ARCHIVE_PARENT_CREATE_FAILED"
                    }
                }
                copied += copyFileWithBudget(item, target, remaining)
            }
        }
        return copied
    }

    private fun copyFileWithBudget(source: File, destination: File, byteBudget: Long): Long {
        var copied = 0L
        BufferedInputStream(FileInputStream(source)).use { input ->
            BufferedOutputStream(FileOutputStream(destination)).use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count <= 0) break
                    copied += count
                    check(copied <= byteBudget) { "VOICE_EXTRACTED_LIMIT_EXCEEDED" }
                    output.write(buffer, 0, count)
                }
            }
        }
        return copied
    }

    private fun safeArchiveDestination(safeRoot: File, entryName: String): File {
        val destination = File(safeRoot, entryName).canonicalFile
        check(isInsideRoot(safeRoot, destination)) { "VOICE_ARCHIVE_PATH_REJECTED" }
        return destination
    }

    private fun isInsideRoot(root: File, candidate: File): Boolean =
        candidate.path == root.path || candidate.path.startsWith(root.path + File.separator)

    private fun isRetriableModelInstallFailure(error: Throwable): Boolean {
        val code = error.message.orEmpty().uppercase()
        if (
            "PATH_REJECTED" in code ||
            "LINK_OUTSIDE_ROOT" in code ||
            "LINK_CYCLE" in code ||
            "LINK_TARGET_MISSING" in code
        ) {
            return false
        }
        return error is java.io.IOException ||
            "DOWNLOAD" in code ||
            "HTTP_" in code ||
            "ARCHIVE" in code ||
            "HASH" in code ||
            "SIZE" in code
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
            "DOWNLOAD" in code || "HTTP_" in code ->
                "Não foi possível baixar completamente o modelo neural de voz."
            "HASH" in code || "SIZE" in code || "ARCHIVE" in code ->
                "A instalação recebeu arquivos incompletos ou incompatíveis e foi interrompida com segurança."
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

    private fun sha256(file: File): String {
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
