package com.pontocafe.app.voice

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.pontocafe.app.ComprovantePonto
import com.pontocafe.app.PontoCafeViewModel
import com.pontocafe.app.PontoRecognitionStage
import com.pontocafe.app.TipoComprovantePonto
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

data class PontoVoicePrompt(
    val key: String,
    val text: String,
    val interrupt: Boolean = true,
)

enum class PontoVoiceKioskCue {
    CAMERA_PERMISSION_REQUIRED,
    CAMERA_UNAVAILABLE,
    MODEL_UNAVAILABLE,
    MULTIPLE_FACES,
    NO_FACE,
    LOOK_AT_CAMERA,
    BLINK,
    OPEN_EYES,
    TURN_LEFT,
    TURN_RIGHT,
    CENTER_FACE,
    FACE_NOT_RECOGNIZED,
}

/**
 * Pure phrase policy. Keeping copy selection outside TextToSpeech makes voice
 * behavior deterministic and testable without an Android speech engine.
 */
object PontoVoicePromptPolicy {
    fun kiosk(cue: PontoVoiceKioskCue): PontoVoicePrompt = when (cue) {
        PontoVoiceKioskCue.CAMERA_PERMISSION_REQUIRED -> prompt(
            "camera-permission",
            "Ative a câmera para bater o ponto.",
        )
        PontoVoiceKioskCue.CAMERA_UNAVAILABLE -> prompt(
            "camera-unavailable",
            "Câmera indisponível. Procure um responsável.",
        )
        PontoVoiceKioskCue.MODEL_UNAVAILABLE -> prompt(
            "model-unavailable",
            "Reconhecimento facial indisponível neste aparelho.",
        )
        PontoVoiceKioskCue.MULTIPLE_FACES -> prompt(
            "multiple-faces",
            "Apenas uma pessoa por vez. Deixe somente um rosto diante da câmera.",
        )
        PontoVoiceKioskCue.NO_FACE -> prompt(
            "no-face",
            "Aproxime-se e olhe para a câmera.",
        )
        PontoVoiceKioskCue.LOOK_AT_CAMERA -> prompt(
            "look-at-camera",
            "Olhe para a câmera e mantenha o rosto centralizado.",
        )
        PontoVoiceKioskCue.BLINK -> prompt(
            "blink",
            "Pisque uma vez.",
        )
        PontoVoiceKioskCue.OPEN_EYES -> prompt(
            "open-eyes",
            "Agora abra os olhos.",
        )
        PontoVoiceKioskCue.TURN_LEFT -> prompt(
            "turn-left",
            "Vire levemente para a esquerda.",
        )
        PontoVoiceKioskCue.TURN_RIGHT -> prompt(
            "turn-right",
            "Vire levemente para a direita.",
        )
        PontoVoiceKioskCue.CENTER_FACE -> prompt(
            "center-face",
            "Volte ao centro e olhe para a câmera.",
        )
        PontoVoiceKioskCue.FACE_NOT_RECOGNIZED -> prompt(
            "face-not-recognized",
            "Rosto não reconhecido. Olhe de frente, centralize o rosto e tente novamente.",
        )
    }

    fun receipt(comprovante: ComprovantePonto): PontoVoicePrompt {
        val offlineSuffix = if (comprovante.pendenteSincronizacao) {
            " O registro ficou salvo neste aparelho e será sincronizado quando a conexão voltar."
        } else {
            ""
        }

        return when (comprovante.tipo) {
            TipoComprovantePonto.INICIO -> {
                val deadline = comprovante.retornoAte?.takeIf(String::isNotBlank)
                val text = if (deadline != null) {
                    "Pausa iniciada. Retorne até $deadline.$offlineSuffix"
                } else {
                    "Pausa iniciada.$offlineSuffix"
                }
                prompt("receipt-start:${comprovante.horarioRegistrado}", text)
            }
            TipoComprovantePonto.RETORNO -> {
                val text = when {
                    comprovante.excedeuLimite ->
                        "Retorno registrado. Atenção: o limite da pausa foi excedido.$offlineSuffix"
                    else -> "Retorno registrado com sucesso.$offlineSuffix"
                }
                prompt("receipt-return:${comprovante.horarioRegistrado}", text)
            }
        }
    }

    fun blocked(motivo: String?): PontoVoicePrompt = when (motivo) {
        "PAUSAS_DO_DIA_JA_UTILIZADAS" -> prompt(
            "blocked-daily",
            "Pausas de hoje já utilizadas. Não há mais pausa disponível para hoje.",
        )
        "PAUSA_PERIODO_JA_UTILIZADA" -> prompt(
            "blocked-period",
            "Esta pausa já foi utilizada hoje. Nenhum novo registro foi criado.",
        )
        "FORA_HORARIO",
        "FORA_HORARIO_NAO_LIBERADO" -> prompt(
            "blocked-outside-window",
            "Fora do horário permitido. Solicite uma liberação ao supervisor.",
        )
        else -> prompt(
            "blocked-generic",
            "Registro não realizado. Verifique a mensagem na tela.",
        )
    }

    fun registrationInProgress(): PontoVoicePrompt = prompt(
        "registration-in-progress",
        "Identidade confirmada. Aguarde o registro do ponto.",
    )

    fun genericRegistrationError(): PontoVoicePrompt = prompt(
        "registration-error",
        "Não foi possível registrar o ponto. Verifique a mensagem na tela.",
    )

    private fun prompt(key: String, text: String) = PontoVoicePrompt(key = key, text = text)
}

/**
 * Process-scoped TTS runtime using only the Android speech engine. It is
 * deliberately fail-open: initialization, locale or playback failures never
 * alter the biometric/point flow.
 */
object PontoVoiceRuntime {
    private val lock = Any()

    @Volatile
    private var speaker: PontoTextToSpeech? = null

    fun speak(context: Context, prompt: PontoVoicePrompt) {
        if (screenReaderOwnsSpeech(context)) return
        val current = speaker ?: synchronized(lock) {
            speaker ?: PontoTextToSpeech(context.applicationContext).also { speaker = it }
        }
        runCatching { current.speak(prompt) }
    }

    fun shutdown() {
        synchronized(lock) {
            runCatching { speaker?.shutdown() }
            speaker = null
        }
    }

    private fun screenReaderOwnsSpeech(context: Context): Boolean {
        val accessibility = context.getSystemService(AccessibilityManager::class.java) ?: return false
        return accessibility.isEnabled && accessibility.isTouchExplorationEnabled
    }
}

private class PontoTextToSpeech(context: Context) {
    private val utteranceSequence = AtomicLong(0L)
    private var engine: TextToSpeech? = null
    private var ready = false
    private var pending: PontoVoicePrompt? = null
    private var lastText: String? = null
    private var lastSpokenAtMillis: Long = 0L

    init {
        engine = TextToSpeech(context.applicationContext) { status -> onInitialized(status) }
    }

    private fun onInitialized(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            synchronized(this) {
                ready = false
                pending = null
            }
            return
        }

        val current = engine ?: return
        val localeResult = runCatching {
            current.setLanguage(Locale.forLanguageTag("pt-BR"))
        }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
        if (localeResult == TextToSpeech.LANG_MISSING_DATA || localeResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            val fallback = runCatching { current.setLanguage(Locale.getDefault()) }
                .getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
            if (fallback == TextToSpeech.LANG_MISSING_DATA || fallback == TextToSpeech.LANG_NOT_SUPPORTED) {
                synchronized(this) {
                    ready = false
                    pending = null
                }
                return
            }
        }

        runCatching {
            current.setSpeechRate(0.96f)
            current.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
        }

        val queued = synchronized(this) {
            ready = true
            pending.also { pending = null }
        }
        queued?.let(::speak)
    }

    @Synchronized
    fun speak(prompt: PontoVoicePrompt) {
        val text = prompt.text.trim()
        if (text.isEmpty()) return

        if (!ready) {
            pending = prompt.copy(text = text)
            return
        }

        val now = System.currentTimeMillis()
        if (text == lastText && now - lastSpokenAtMillis < REPEAT_SUPPRESSION_MILLIS) return

        val current = engine ?: return
        val queueMode = if (prompt.interrupt) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        val utteranceId = "pontocafe:${prompt.key}:${utteranceSequence.incrementAndGet()}"
        val result = runCatching {
            current.speak(text, queueMode, null, utteranceId)
        }.getOrDefault(TextToSpeech.ERROR)
        if (result == TextToSpeech.SUCCESS) {
            lastText = text
            lastSpokenAtMillis = now
        }
    }

    @Synchronized
    fun shutdown() {
        pending = null
        ready = false
        runCatching { engine?.stop() }
        runCatching { engine?.shutdown() }
        engine = null
    }

    companion object {
        private const val REPEAT_SUPPRESSION_MILLIS = 2_500L
    }
}

/**
 * Announces only point outcomes and authoritative blocks. Camera/liveness cues
 * are emitted directly by FaceKioskScreen where the current challenge exists.
 */
@Composable
fun PontoVoiceGuidanceEffect(viewModel: PontoCafeViewModel) {
    val context = LocalContext.current
    val state = viewModel.state
    val identificacao = state.identificacao
    val comprovante = state.comprovante

    val prompt = remember(
        comprovante,
        identificacao?.acaoSugerida,
        identificacao?.motivo,
        state.recognitionStage,
        state.erro,
    ) {
        when {
            comprovante != null -> PontoVoicePromptPolicy.receipt(comprovante)
            identificacao?.acaoSugerida == "BLOQUEADO" ->
                PontoVoicePromptPolicy.blocked(identificacao.motivo)
            state.recognitionStage == PontoRecognitionStage.REGISTRANDO_PONTO ->
                PontoVoicePromptPolicy.registrationInProgress()
            identificacao != null && !state.erro.isNullOrBlank() ->
                PontoVoicePromptPolicy.genericRegistrationError()
            else -> null
        }
    }

    LaunchedEffect(prompt?.key, prompt?.text) {
        prompt?.let { PontoVoiceRuntime.speak(context, it) }
    }
}
