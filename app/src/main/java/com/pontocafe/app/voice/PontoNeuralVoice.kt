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

internal data class PontoNeuralVoiceDiagnostics(
    val availability: PontoNeuralVoiceAvailability,
    val modelInstalled: Boolean,
    val usingAndroidFallback: Boolean,
    val lastFailureAtMillis: Long?,
    val lastFailureReason: String?,
    val retryAvailableInMillis: Long,
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

/**
 * Voz própria do Ponto, executada localmente com sherpa-onnx + Piper/VITS.
 *
 * O modelo pt-BR não é empacotado no APK: ele é baixado uma única vez para o
 * armazenamento privado da aplicação, validado por SHA-256 e reutilizado sem
 * internet. Até ficar pronto (ou se qualquer etapa falhar), o chamador mantém
 * o Android TextToSpeech como fallback. Nenhuma falha de voz interfere no
 * reconhecimento facial ou no registro do ponto.
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
    private var lifecycleVersion = 0L
    private var utteranceVersion = 0L

    fun prewarm(context: Context) {
        ensurePreparing(context.applicationContext)
    }

    fun diagnostics(context: Context): PontoNeuralVoiceDiagnostics {
        val appContext = context.applicationContext
        val modelDir = File(File(appContext.filesDir, "pontocafe-voice"), MODEL_DIR)
        val now = System.currentTimeMillis()
        synchronized(lock) {
            val retryIn = if (state == NeuralVoiceState.FAILED && lastFailureAtMillis > 0L) {
                (RETRY_AFTER_MILLIS - (now - lastFailureAtMillis)).coerceAtLeast(0L)
            } else {
                0L
            }
            return PontoNeuralVoiceDiagnostics(
                availability = state.toAvailability(),
                modelInstalled = modelReadyOnDisk(modelDir),
                usingAndroidFallback = state != NeuralVoiceState.READY || engine == null,
                lastFailureAtMillis = lastFailureAtMillis.takeIf { it > 0L },
                lastFailureReason = lastFailureReason,
                retryAvailableInMillis = retryIn,
            )
        }
    }

    fun retryNow(context: Context) {
        synchronized(lock) {
            if (state == NeuralVoiceState.FAILED) {
                state = NeuralVoiceState.IDLE
                lastFailureAtMillis = 0L
            }
        }
        ensurePreparing(context.applicationContext)
    }

    fun speak(
        context: Context,
        prompt: PontoVoicePrompt,
        sessionKey: String?,
        onFailure: (() -> Unit)? = null,
    ): PontoNeuralSpeechDecision {
        val currentEngine = engine
        if (state != NeuralVoiceState.READY || currentEngine == null) {
            ensurePreparing(context.applicationContext)
            return PontoNeuralSpeechDecision.UNAVAILABLE
        }

        val normalizedText = PontoVoiceTextNormalizer.normalize(prompt.text)
        if (normalizedText.isBlank()) return PontoNeuralSpeechDecision.SUPPRESSED

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

        worker.execute {
            if (!isCurrent(lifecycle, utterance)) return@execute

            val audio = try {
                synchronized(lock) { cache[normalizedText] }
                    ?: synthesize(currentEngine, normalizedText).also { generated ->
                        synchronized(lock) { cache[normalizedText] = generated }
                    }
            } catch (error: Throwable) {
                Log.e(TAG, "VOICE_SYNTHESIS_FAILED", error)
                markEngineFailure(currentEngine, error)
                runCatching { onFailure?.invoke() }
                return@execute
            }

            if (!isCurrent(lifecycle, utterance)) return@execute

            try {
                play(audio, lifecycle, utterance)
            } catch (error: Throwable) {
                // Falha de AudioTrack não invalida o modelo/engine. A versão
                // anterior marcava todo o runtime como FAILED e podia manter a
                // voz Android por 15 minutos após um erro transitório de áudio.
                Log.e(TAG, "VOICE_PLAYBACK_FAILED", error)
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
                }
                Log.i(TAG, "VOICE_ENGINE_READY")
            } catch (error: Throwable) {
                Log.e(TAG, "VOICE_PREPARE_FAILED", error)
                synchronized(lock) {
                    if (version == lifecycleVersion) {
                        state = NeuralVoiceState.FAILED
                        lastFailureAtMillis = System.currentTimeMillis()
                        lastFailureReason = classifyVoiceFailure(error)
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

    private fun play(audio: CachedNeuralAudio, lifecycle: Long, utterance: Long) {
        val minBuffer = AudioTrack.getMinBufferSize(
            audio.sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        check(minBuffer > 0) { "VOICE_AUDIO_BUFFER_INVALID" }

        // MODE_STREAM deve começar a reproduzir antes do WRITE_BLOCKING. Isso
        // evita que uma fala longa tente preencher um buffer inteiro antes de o
        // AudioTrack poder drená-lo, situação que fazia o fallback Android ser
        // acionado em alguns aparelhos.
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
                return
            }
            currentTrack = track
        }

        try {
            track.play()
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

            if (!isCurrent(lifecycle, utterance)) return

            while (
                isCurrent(lifecycle, utterance) &&
                track.playState == AudioTrack.PLAYSTATE_PLAYING &&
                track.playbackHeadPosition.toLong() < audio.samples.size.toLong()
            ) {
                Thread.sleep(20L)
            }
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
        synchronized(lock) {
            if (engine !== failedEngine) return
            engine = null
            state = NeuralVoiceState.FAILED
            lastFailureAtMillis = System.currentTimeMillis()
            lastFailureReason = classifyVoiceFailure(error)
            cache.clear()
        }
        Log.e(TAG, "VOICE_ENGINE_FAILED", error)
        runCatching { failedEngine.release() }
    }

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

        finalDir.deleteRecursively()
        val workRoot = File(context.cacheDir, "pontocafe-voice-install").apply {
            deleteRecursively()
            mkdirs()
        }
        val archive = File(workRoot, "$MODEL_DIR.tar.bz2")
        val extracted = File(workRoot, "extracted").apply { mkdirs() }

        try {
            Log.i(TAG, "VOICE_DOWNLOAD_START")
            downloadModel(archive)
            Log.i(TAG, "VOICE_DOWNLOAD_DONE bytes=${archive.length()}")
            extractArchive(archive, extracted)

            // O pacote oficial usa MODEL_DIR como raiz. A busca recursiva deixa
            // o instalador tolerante a um eventual prefixo ./ ou mudança de
            // empacotamento sem aceitar um modelo diferente.
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
            if (!extractedModelDir.renameTo(finalDir)) {
                check(extractedModelDir.copyRecursively(finalDir, overwrite = true)) {
                    "VOICE_MODEL_INSTALL_COPY_FAILED"
                }
            }
            File(finalDir, ".ready-$MODEL_SHA256").writeText("$MODEL_SHA256\n")
            Log.i(TAG, "VOICE_MODEL_INSTALLED")
            return finalDir
        } finally {
            workRoot.deleteRecursively()
        }
    }

    private fun downloadModel(destination: File) {
        destination.parentFile?.mkdirs()
        val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", "PontoCafe-Android/1.0")
            setRequestProperty("Accept", "application/octet-stream,*/*")
        }
        try {
            connection.connect()
            check(connection.responseCode in 200..299) {
                "VOICE_DOWNLOAD_HTTP_${connection.responseCode}"
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
                        check(total <= MAX_ARCHIVE_BYTES) { "VOICE_ARCHIVE_LIMIT_EXCEEDED" }
                        output.write(buffer, 0, count)
                    }
                }
            }
            check(total > 0L) { "VOICE_DOWNLOAD_EMPTY" }
        } finally {
            connection.disconnect()
        }
    }

    private fun extractArchive(archive: File, destinationRoot: File) {
        val safeRoot = destinationRoot.canonicalFile
        var extractedBytes = 0L
        BZip2CompressorInputStream(BufferedInputStream(FileInputStream(archive))).use { bzip ->
            TarArchiveInputStream(bzip).use { tar ->
                while (true) {
                    val entry = tar.nextEntry ?: break
                    check(!entry.isSymbolicLink && !entry.isLink) { "VOICE_ARCHIVE_LINK_REJECTED" }
                    val destination = File(safeRoot, entry.name).canonicalFile
                    check(
                        destination.path == safeRoot.path ||
                            destination.path.startsWith(safeRoot.path + File.separator),
                    ) { "VOICE_ARCHIVE_PATH_REJECTED" }
                    if (entry.isDirectory) {
                        check(destination.mkdirs() || destination.isDirectory)
                        continue
                    }
                    destination.parentFile?.let { parent -> check(parent.mkdirs() || parent.isDirectory) }
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
    }

    private fun classifyVoiceFailure(error: Throwable): String {
        val code = error.message.orEmpty().uppercase()
        return when {
            "DOWNLOAD" in code || "HTTP_" in code ->
                "Não foi possível baixar o modelo neural de voz."
            "HASH" in code || "SIZE" in code || "ARCHIVE" in code ->
                "O modelo de voz não passou na validação de integridade."
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
