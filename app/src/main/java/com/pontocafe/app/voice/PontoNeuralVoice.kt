package com.pontocafe.app.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
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

internal enum class PontoNeuralSpeechDecision {
    ACCEPTED,
    SUPPRESSED,
    UNAVAILABLE,
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
 * O modelo pt-BR não é empacotado no APK: ele é baixado uma única vez para o
 * armazenamento privado da aplicação, validado por SHA-256 e reutilizado sem
 * internet. Até ficar pronto (ou se qualquer etapa falhar), o chamador mantém
 * o Android TextToSpeech como fallback. Nenhuma falha de voz interfere no
 * reconhecimento facial ou no registro do ponto.
 */
internal object PontoNeuralVoiceRuntime {
    private const val MODEL_DIR = "vits-piper-pt_BR-faber-medium"
    private const val MODEL_FILE = "pt_BR-faber-medium.onnx"
    private const val MODEL_URL =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-pt_BR-faber-medium.tar.bz2"
    private const val MODEL_SHA256 = "39fb6b580d6d40a3230b7a9d0851d282074537b9694892b5b3cd90ff87c6cbb3"
    private const val MODEL_SIZE_BYTES = 63_201_428L
    private const val MAX_ARCHIVE_BYTES = 120L * 1024L * 1024L
    private const val MAX_EXTRACTED_BYTES = 160L * 1024L * 1024L
    private const val RETRY_AFTER_MILLIS = 15L * 60L * 1_000L
    private const val CACHE_ENTRIES = 24
    private const val VOICE_SPEED = 1.02f
    private const val SILENCE_SCALE = 0.18f

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
    private var lifecycleVersion = 0L

    fun prewarm(context: Context) {
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
        synchronized(lock) {
            if (!gate.canSpeak(prompt, now, sessionKey)) {
                return PontoNeuralSpeechDecision.SUPPRESSED
            }

            val playing = currentTrack?.playState == AudioTrack.PLAYSTATE_PLAYING
            if (playing && !prompt.interrupt) {
                return PontoNeuralSpeechDecision.SUPPRESSED
            }

            if (playing && prompt.interrupt) {
                runCatching { currentTrack?.stop() }
            }
            gate.markSpoken(prompt, now, sessionKey)
        }

        val version = synchronized(lock) { lifecycleVersion }
        worker.execute {
            try {
                if (version != synchronized(lock) { lifecycleVersion }) return@execute
                val audio = synchronized(lock) { cache[normalizedText] }
                    ?: synthesize(currentEngine, normalizedText).also { generated ->
                        synchronized(lock) { cache[normalizedText] = generated }
                    }
                play(audio, version)
            } catch (_: Throwable) {
                markRuntimeFailure(currentEngine)
                runCatching { onFailure?.invoke() }
            }
        }
        return PontoNeuralSpeechDecision.ACCEPTED
    }

    fun shutdown() {
        val engineToRelease: OfflineTts?
        synchronized(lock) {
            lifecycleVersion += 1L
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

        worker.execute {
            var prepared: OfflineTts? = null
            try {
                val modelDir = ensureModelInstalled(context)
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
                }
            } catch (_: Throwable) {
                synchronized(lock) {
                    if (version == lifecycleVersion) {
                        state = NeuralVoiceState.FAILED
                        lastFailureAtMillis = System.currentTimeMillis()
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
        check(model.isFile && tokens.isFile && dataDir.isDirectory)

        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = model.absolutePath,
                    tokens = tokens.absolutePath,
                    dataDir = dataDir.absolutePath,
                ),
                numThreads = 2,
                debug = false,
            ),
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
        check(generated.samples.isNotEmpty() && generated.sampleRate > 0)
        val pcm = ShortArray(generated.samples.size) { index ->
            val sample = generated.samples[index].coerceIn(-1f, 1f)
            (sample * Short.MAX_VALUE).toInt().toShort()
        }
        return CachedNeuralAudio(samples = pcm, sampleRate = generated.sampleRate)
    }

    private fun play(audio: CachedNeuralAudio, version: Long) {
        val minBuffer = AudioTrack.getMinBufferSize(
            audio.sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        check(minBuffer > 0)
        val bufferBytes = max(minBuffer, audio.samples.size * 2)
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

        synchronized(lock) {
            if (version != lifecycleVersion) {
                track.release()
                return
            }
            currentTrack = track
        }

        try {
            val written = track.write(audio.samples, 0, audio.samples.size, AudioTrack.WRITE_BLOCKING)
            check(written == audio.samples.size)
            track.play()
            while (
                version == synchronized(lock) { lifecycleVersion } &&
                track.playState == AudioTrack.PLAYSTATE_PLAYING &&
                track.playbackHeadPosition.toLong() < audio.samples.size.toLong()
            ) {
                Thread.sleep(20L)
            }
        } finally {
            runCatching { track.stop() }
            runCatching { track.release() }
            synchronized(lock) {
                if (currentTrack === track) currentTrack = null
            }
        }
    }

    private fun markRuntimeFailure(failedEngine: OfflineTts) {
        synchronized(lock) {
            if (engine !== failedEngine) return
            engine = null
            state = NeuralVoiceState.FAILED
            lastFailureAtMillis = System.currentTimeMillis()
            cache.clear()
        }
        runCatching { failedEngine.release() }
    }

    private fun ensureModelInstalled(context: Context): File {
        val parent = File(context.filesDir, "pontocafe-voice").apply { mkdirs() }
        val finalDir = File(parent, MODEL_DIR)
        val marker = File(finalDir, ".ready-$MODEL_SHA256")
        val existingModel = File(finalDir, MODEL_FILE)
        if (
            marker.isFile && existingModel.isFile && existingModel.length() == MODEL_SIZE_BYTES &&
            File(finalDir, "tokens.txt").isFile && File(finalDir, "espeak-ng-data").isDirectory
        ) {
            return finalDir
        }

        finalDir.deleteRecursively()
        val workRoot = File(context.cacheDir, "pontocafe-voice-install").apply {
            deleteRecursively()
            mkdirs()
        }
        val archive = File(workRoot, "$MODEL_DIR.tar.bz2")
        val extracted = File(workRoot, "extracted").apply { mkdirs() }

        try {
            downloadModel(archive)
            extractArchive(archive, extracted)
            val extractedModelDir = File(extracted, MODEL_DIR)
            val model = File(extractedModelDir, MODEL_FILE)
            val tokens = File(extractedModelDir, "tokens.txt")
            val dataDir = File(extractedModelDir, "espeak-ng-data")
            check(model.isFile && model.length() == MODEL_SIZE_BYTES)
            check(tokens.isFile && dataDir.isDirectory)
            check(sha256(model).equals(MODEL_SHA256, ignoreCase = true))

            parent.mkdirs()
            if (!extractedModelDir.renameTo(finalDir)) {
                check(extractedModelDir.copyRecursively(finalDir, overwrite = true))
            }
            File(finalDir, ".ready-$MODEL_SHA256").writeText("$MODEL_SHA256\n")
            return finalDir
        } finally {
            workRoot.deleteRecursively()
        }
    }

    private fun downloadModel(destination: File) {
        destination.parentFile?.mkdirs()
        val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 45_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", "PontoCafe-Android/1.0")
        }
        try {
            connection.connect()
            check(connection.responseCode in 200..299)
            val advertisedSize = connection.contentLengthLong
            check(advertisedSize <= 0L || advertisedSize <= MAX_ARCHIVE_BYTES)

            var total = 0L
            BufferedInputStream(connection.inputStream).use { input ->
                BufferedOutputStream(FileOutputStream(destination)).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count <= 0) break
                        total += count
                        check(total <= MAX_ARCHIVE_BYTES)
                        output.write(buffer, 0, count)
                    }
                }
            }
            check(total > 0L)
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
                    check(!entry.isSymbolicLink && !entry.isLink)
                    val destination = File(safeRoot, entry.name).canonicalFile
                    check(
                        destination.path == safeRoot.path ||
                            destination.path.startsWith(safeRoot.path + File.separator),
                    )
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
                            check(extractedBytes <= MAX_EXTRACTED_BYTES)
                            output.write(buffer, 0, count)
                        }
                    }
                }
            }
        }
    }

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
