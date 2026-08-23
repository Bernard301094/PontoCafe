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
import com.pontocafe.app.TipoComprovantePonto
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.delay

private const val DEVICE_AUTH_RECHECK_MILLIS = 10_000L
private const val NEURAL_RESULT_WAIT_MILLIS = 2_500L
private const val NEURAL_RESULT_WAIT_STEP_MILLIS = 100L
private const val DEVICE_REVOKED_ERROR_FRAGMENT = "não está mais autorizado"

enum class PontoVoicePriority {
    LOW,
    INSTRUCTION,
    RESULT,
    CRITICAL,
}

data class PontoVoicePrompt(
    val key: String,
    val text: String,
    val priority: PontoVoicePriority = PontoVoicePriority.INSTRUCTION,
    val cooldownMillis: Long = 12_000L,
    val stabilityDelayMillis: Long = 0L,
    val interrupt: Boolean = true,
    val countsTowardInstructionBudget: Boolean = false,
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
 * Pure phrase policy. Keeping copy and cadence outside the speech engines makes
 * voice behavior deterministic and testable.
 */
object PontoVoicePromptPolicy {
    fun kiosk(cue: PontoVoiceKioskCue): PontoVoicePrompt = when (cue) {
        PontoVoiceKioskCue.CAMERA_PERMISSION_REQUIRED -> prompt(
            key = "camera-permission",
            text = "Ative a câmera para bater o ponto.",
            priority = PontoVoicePriority.CRITICAL,
            cooldownMillis = 60_000L,
        )
        PontoVoiceKioskCue.CAMERA_UNAVAILABLE -> prompt(
            key = "camera-unavailable",
            text = "Câmera indisponível. Procure um responsável.",
            priority = PontoVoicePriority.CRITICAL,
            cooldownMillis = 60_000L,
        )
        PontoVoiceKioskCue.MODEL_UNAVAILABLE -> prompt(
            key = "model-unavailable",
            text = "Reconhecimento facial indisponível neste aparelho.",
            priority = PontoVoicePriority.CRITICAL,
            cooldownMillis = 60_000L,
        )
        PontoVoiceKioskCue.MULTIPLE_FACES -> prompt(
            key = "multiple-faces",
            text = "Apenas uma pessoa por vez. Deixe somente um rosto diante da câmera.",
            priority = PontoVoicePriority.INSTRUCTION,
            cooldownMillis = 15_000L,
            stabilityDelayMillis = 1_200L,
        )
        PontoVoiceKioskCue.NO_FACE -> prompt(
            key = "no-face",
            text = "Aproxime-se e olhe para a câmera.",
            priority = PontoVoicePriority.LOW,
            cooldownMillis = 30_000L,
            stabilityDelayMillis = 5_000L,
            interrupt = false,
        )
        PontoVoiceKioskCue.LOOK_AT_CAMERA -> prompt(
            key = "look-at-camera",
            text = "Olhe para a câmera e mantenha o rosto centralizado.",
            priority = PontoVoicePriority.LOW,
            cooldownMillis = 15_000L,
            stabilityDelayMillis = 1_800L,
            interrupt = false,
        )
        PontoVoiceKioskCue.BLINK -> livenessPrompt(
            key = "blink",
            text = "Pisque uma vez.",
        )
        PontoVoiceKioskCue.OPEN_EYES -> livenessPrompt(
            key = "open-eyes",
            text = "Agora abra os olhos.",
            stabilityDelayMillis = 250L,
        )
        PontoVoiceKioskCue.TURN_LEFT -> livenessPrompt(
            key = "turn-left",
            text = "Vire levemente para a esquerda.",
        )
        PontoVoiceKioskCue.TURN_RIGHT -> livenessPrompt(
            key = "turn-right",
            text = "Vire levemente para a direita.",
        )
        PontoVoiceKioskCue.CENTER_FACE -> livenessPrompt(
            key = "center-face",
            text = "Volte ao centro e olhe para a câmera.",
            stabilityDelayMillis = 350L,
        )
        PontoVoiceKioskCue.FACE_NOT_RECOGNIZED -> prompt(
            key = "face-not-recognized",
            text = "Rosto não reconhecido. Olhe de frente, centralize o rosto e tente novamente.",
            priority = PontoVoicePriority.RESULT,
            cooldownMillis = 12_000L,
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
                prompt(
                    key = "receipt-start:${comprovante.horarioRegistrado}",
                    text = text,
                    priority = PontoVoicePriority.RESULT,
                    cooldownMillis = 60_000L,
                )
            }
            TipoComprovantePonto.RETORNO -> {
                val text = when {
                    comprovante.excedeuLimite ->
                        "Retorno registrado. Atenção: o limite da pausa foi excedido.$offlineSuffix"
                    else -> "Retorno registrado com sucesso.$offlineSuffix"
                }
                prompt(
                    key = "receipt-return:${comprovante.horarioRegistrado}",
                    text = text,
                    priority = PontoVoicePriority.RESULT,
                    cooldownMillis = 60_000L,
                )
            }
        }
    }

    fun blocked(motivo: String?): PontoVoicePrompt = when (motivo) {
        "PAUSAS_DO_DIA_JA_UTILIZADAS" -> criticalPrompt(
            "blocked-daily",
            "Pausas de hoje já utilizadas. Não há mais pausa disponível para hoje.",
        )
        "PAUSA_PERIODO_JA_UTILIZADA" -> criticalPrompt(
            "blocked-period",
            "Esta pausa já foi utilizada hoje. Nenhum novo registro foi criado.",
        )
        "FORA_HORARIO",
        "FORA_HORARIO_NAO_LIBERADO" -> criticalPrompt(
            "blocked-outside-window",
            "Fora do horário permitido. Solicite uma liberação ao supervisor.",
        )
        else -> criticalPrompt(
            "blocked-generic",
            "Registro não realizado. Verifique a mensagem na tela.",
        )
    }

    fun genericRegistrationError(): PontoVoicePrompt = criticalPrompt(
        "registration-error",
        "Não foi possível registrar o ponto. Verifique a mensagem na tela.",
        cooldownMillis = 15_000L,
    )

    private fun livenessPrompt(
        key: String,
        text: String,
        stabilityDelayMillis: Long = 450L,
    ) = prompt(
        key = key,
        text = text,
        priority = PontoVoicePriority.INSTRUCTION,
        cooldownMillis = 12_000L,
        stabilityDelayMillis = stabilityDelayMillis,
        countsTowardInstructionBudget = true,
    )

    private fun criticalPrompt(
        key: String,
        text: String,
        cooldownMillis: Long = 30_000L,
    ) = prompt(
        key = key,
        text = text,
        priority = PontoVoicePriority.CRITICAL,
        cooldownMillis = cooldownMillis,
    )

    private fun prompt(
        key: String,
        text: String,
        priority: PontoVoicePriority,
        cooldownMillis: Long,
        stabilityDelayMillis: Long = 0L,
        interrupt: Boolean = true,
        countsTowardInstructionBudget: Boolean = false,
    ) = PontoVoicePrompt(
        key = key,
        text = text,
        priority = priority,
        cooldownMillis = cooldownMillis,
        stabilityDelayMillis = stabilityDelayMillis,
        interrupt = interrupt,
        countsTowardInstructionBudget = countsTowardInstructionBudget,
    )
}

/**
 * Small pure state gate that prevents a kiosk from becoming a continuous
 * narrator. Repeated prompts are cooled down per scan session and liveness is
 * limited to a small instruction budget. Point results and critical warnings do
 * not consume that budget.
 */
internal class PontoVoiceGate {
    private var activeSessionKey: String? = null
    private var sessionInstructionCount = 0
    private val sessionLastSpokenAt = mutableMapOf<String, Long>()
    private val globalLastSpokenAt = mutableMapOf<String, Long>()

    fun canSpeak(
        prompt: PontoVoicePrompt,
        nowMillis: Long,
        sessionKey: String?,
    ): Boolean {
        prepareSession(sessionKey)
        val timestamps = if (sessionKey != null) sessionLastSpokenAt else globalLastSpokenAt
        val previous = timestamps[prompt.key]
        if (previous != null && nowMillis - previous < prompt.cooldownMillis) return false
        if (
            sessionKey != null && prompt.countsTowardInstructionBudget &&
            sessionInstructionCount >= MAX_INSTRUCTIONS_PER_SESSION
        ) {
            return false
        }
        return true
    }

    fun markSpoken(
        prompt: PontoVoicePrompt,
        nowMillis: Long,
        sessionKey: String?,
    ) {
        prepareSession(sessionKey)
        val timestamps = if (sessionKey != null) sessionLastSpokenAt else globalLastSpokenAt
        timestamps[prompt.key] = nowMillis
        if (sessionKey != null && prompt.countsTowardInstructionBudget) {
            sessionInstructionCount += 1
        }
    }

    fun reset() {
        activeSessionKey = null
        sessionInstructionCount = 0
        sessionLastSpokenAt.clear()
        globalLastSpokenAt.clear()
    }

    private fun prepareSession(sessionKey: String?) {
        if (sessionKey == null || sessionKey == activeSessionKey) return
        activeSessionKey = sessionKey
        sessionInstructionCount = 0
        sessionLastSpokenAt.clear()
    }

    companion object {
        const val MAX_INSTRUCTIONS_PER_SESSION = 3
    }
}

/**
 * Runtime process-scoped neural-first. A voz Piper/VITS local é preferida
 * quando pronta; Android TextToSpeech pt-BR continua como fallback imediato.
 * Inicialização, download, síntese e reprodução são fail-open e nunca alteram
 * o fluxo biométrico/de ponto.
 */
object PontoVoiceRuntime {
    private val lock = Any()

    @Volatile
    private var speaker: PontoTextToSpeech? = null

    fun prewarm(context: Context) {
        if (screenReaderOwnsSpeech(context)) return
        val appContext = context.applicationContext
        PontoNeuralVoiceRuntime.prewarm(appContext)
        fallbackSpeaker(appContext)
    }

    fun speak(
        context: Context,
        prompt: PontoVoicePrompt,
        sessionKey: String? = null,
    ) {
        if (screenReaderOwnsSpeech(context)) return
        val appContext = context.applicationContext
        val normalizedPrompt = prompt.copy(text = PontoVoiceTextNormalizer.normalize(prompt.text))
        val decision = runCatching {
            PontoNeuralVoiceRuntime.speak(
                context = appContext,
                prompt = normalizedPrompt,
                sessionKey = sessionKey,
                onFailure = { speakFallback(appContext, normalizedPrompt, sessionKey) },
            )
        }.getOrDefault(PontoNeuralSpeechDecision.UNAVAILABLE)

        if (decision == PontoNeuralSpeechDecision.UNAVAILABLE) {
            speakFallback(appContext, normalizedPrompt, sessionKey)
        }
    }

    fun shutdown() {
        PontoNeuralVoiceRuntime.shutdown()
        synchronized(lock) {
            runCatching { speaker?.shutdown() }
            speaker = null
        }
    }

    private fun speakFallback(context: Context, prompt: PontoVoicePrompt, sessionKey: String?) {
        runCatching { fallbackSpeaker(context).speak(prompt, sessionKey) }
    }

    private fun fallbackSpeaker(context: Context): PontoTextToSpeech = speaker ?: synchronized(lock) {
        speaker ?: PontoTextToSpeech(context.applicationContext).also { speaker = it }
    }

    private fun screenReaderOwnsSpeech(context: Context): Boolean {
        val accessibility = context.getSystemService(AccessibilityManager::class.java) ?: return false
        return accessibility.isEnabled && accessibility.isTouchExplorationEnabled
    }
}

private data class PendingSpeech(
    val prompt: PontoVoicePrompt,
    val sessionKey: String?,
)

private class PontoTextToSpeech(context: Context) {
    private val utteranceSequence = AtomicLong(0L)
    private val gate = PontoVoiceGate()
    private var engine: TextToSpeech? = null
    private var ready = false
    private var pending: PendingSpeech? = null

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
        queued?.let { speak(it.prompt, it.sessionKey) }
    }

    @Synchronized
    fun speak(prompt: PontoVoicePrompt, sessionKey: String?) {
        val text = prompt.text.trim()
        if (text.isEmpty()) return

        if (!ready) {
            pending = PendingSpeech(prompt.copy(text = text), sessionKey)
            return
        }

        val now = System.currentTimeMillis()
        if (!gate.canSpeak(prompt, now, sessionKey)) return

        val current = engine ?: return
        val queueMode = if (prompt.interrupt) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        val utteranceId = "pontocafe:${prompt.key}:${utteranceSequence.incrementAndGet()}"
        val result = runCatching {
            current.speak(text, queueMode, null, utteranceId)
        }.getOrDefault(TextToSpeech.ERROR)
        if (result == TextToSpeech.SUCCESS) {
            gate.markSpoken(prompt, now, sessionKey)
        }
    }

    @Synchronized
    fun shutdown() {
        pending = null
        ready = false
        gate.reset()
        runCatching { engine?.stop() }
        runCatching { engine?.shutdown() }
        engine = null
    }
}

/**
 * Announces only authoritative point outcomes and blocks. Normal recognition,
 * processing and registration stages intentionally stay silent.
 *
 * Também vigia a credencial do dispositivo enquanto a câmera está ativa. O
 * backend já responde 401 para um token de dispositivo removido/inativo; a
 * checagem periódica reaproveita o fluxo existente de conectividade e, ao
 * receber esse sinal autoritativo, desmonta o modo Ponto e volta ao cadastro de
 * token. Falhas temporárias de rede não revogam o aparelho e preservam o modo
 * offline existente.
 */
@Composable
fun PontoVoiceGuidanceEffect(viewModel: PontoCafeViewModel) {
    val context = LocalContext.current
    val state = viewModel.state
    val identificacao = state.identificacao
    val comprovante = state.comprovante

    LaunchedEffect(Unit) {
        PontoVoiceRuntime.prewarm(context)
    }

    LaunchedEffect(state.deviceConfigured) {
        if (!state.deviceConfigured) return@LaunchedEffect
        while (true) {
            viewModel.atualizarConectividadeESincronizar()
            delay(DEVICE_AUTH_RECHECK_MILLIS)
        }
    }

    LaunchedEffect(state.deviceConfigured, state.erro) {
        val revoked = state.deviceConfigured &&
            state.erro?.contains(DEVICE_REVOKED_ERROR_FRAGMENT, ignoreCase = true) == true
        if (revoked) {
            viewModel.removerConfiguracao()
        }
    }

    val prompt = remember(
        comprovante,
        identificacao?.acaoSugerida,
        identificacao?.motivo,
        state.erro,
    ) {
        when {
            comprovante != null -> PontoVoicePromptPolicy.receipt(comprovante)
            identificacao?.acaoSugerida == "BLOQUEADO" ->
                PontoVoicePromptPolicy.blocked(identificacao.motivo)
            identificacao != null && !state.erro.isNullOrBlank() ->
                PontoVoicePromptPolicy.genericRegistrationError()
            else -> null
        }
    }

    LaunchedEffect(prompt?.key, prompt?.text) {
        val currentPrompt = prompt ?: return@LaunchedEffect

        // Resultado/bloqueio é curto e importante. Quando o modelo neural já
        // está instalado, damos ao engine alguns instantes para sair de
        // PREPARING (ou refazemos uma inicialização que falhou) antes de cair no
        // TTS do Android. Isso evita ouvir sistematicamente a voz do aparelho só
        // porque o engine estava a centenas de milissegundos de ficar READY.
        if (
            currentPrompt.priority == PontoVoicePriority.RESULT ||
            currentPrompt.priority == PontoVoicePriority.CRITICAL
        ) {
            var diagnostics = PontoNeuralVoiceRuntime.diagnostics(context)
            if (diagnostics.modelInstalled && diagnostics.availability == PontoNeuralVoiceAvailability.FAILED) {
                PontoNeuralVoiceRuntime.retryNow(context)
                diagnostics = PontoNeuralVoiceRuntime.diagnostics(context)
            }

            var waited = 0L
            while (
                diagnostics.modelInstalled &&
                diagnostics.availability != PontoNeuralVoiceAvailability.READY &&
                waited < NEURAL_RESULT_WAIT_MILLIS
            ) {
                delay(NEURAL_RESULT_WAIT_STEP_MILLIS)
                waited += NEURAL_RESULT_WAIT_STEP_MILLIS
                diagnostics = PontoNeuralVoiceRuntime.diagnostics(context)
            }
        }

        PontoVoiceRuntime.speak(context, currentPrompt)
    }
}
