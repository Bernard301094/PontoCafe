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
import com.pontocafe.app.PontoStep
import com.pontocafe.app.TipoComprovantePonto
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

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

/**
 * Os momentos do quiosque em que falar acrescenta alguma coisa.
 *
 * A lista encolheu de propósito quando o rosto saiu de cena: não há mais o que
 * narrar sobre posicionamento, piscar ou luz. Sobrou o que a pessoa precisa
 * saber quando está de pé em frente ao aparelho sem óculos.
 */
enum class PontoVoiceKioskCue {
    ESCOLHER_PESSOA,
    DIGITAR_CODIGO_SAIDA,
    DIGITAR_CODIGO_RETORNO,
}

/**
 * Fallback usado só enquanto /app-status ainda não respondeu nesta sessão.
 * O valor real vem do servidor (`config.accessCodeTtlSeconds`).
 */
private const val DEFAULT_CODE_VALIDITY_SECONDS = 120

/**
 * Pure phrase policy. Keeping copy and cadence outside the speech engines makes
 * voice behavior deterministic and testable.
 */
object PontoVoicePromptPolicy {
    /**
     * [validadeSegundos] é a janela real que o servidor devolve em /app-status,
     * nunca um número escrito à mão aqui. Se a operação decidir alargar ou
     * encurtar o prazo, a fala acompanha sozinha — dizer "dois minutos" quando o
     * servidor concede cinco seria pior do que não dizer nada.
     */
    fun kiosk(
        cue: PontoVoiceKioskCue,
        validadeSegundos: Int = DEFAULT_CODE_VALIDITY_SECONDS,
    ): PontoVoicePrompt = when (cue) {
        PontoVoiceKioskCue.ESCOLHER_PESSOA -> prompt(
            key = "pick-person",
            text = "Toque no seu nome na lista.",
            priority = PontoVoicePriority.LOW,
            cooldownMillis = 30_000L,
            stabilityDelayMillis = 3_000L,
            interrupt = false,
        )
        // A janela é curta, então dizê-la é a informação mais útil do passo: quem
        // souber que tem pouco tempo digita agora em vez de guardar o papel.
        PontoVoiceKioskCue.DIGITAR_CODIGO_SAIDA -> prompt(
            key = "type-code-out",
            text = "Digite o código de seis caracteres que o supervisor entregou. " +
                "Ele vale ${spokenDuration(validadeSegundos)} a partir do momento em que foi gerado.",
            priority = PontoVoicePriority.INSTRUCTION,
            cooldownMillis = 20_000L,
            stabilityDelayMillis = 900L,
            interrupt = false,
        )
        // No retorno não há prazo nenhum, e dizer isso evita a pressa de quem
        // acha que também vai perder o código enquanto volta.
        PontoVoiceKioskCue.DIGITAR_CODIGO_RETORNO -> prompt(
            key = "type-code-back",
            text = "Digite o mesmo código que você usou para sair. Ele não expira para o retorno.",
            priority = PontoVoicePriority.INSTRUCTION,
            cooldownMillis = 20_000L,
            stabilityDelayMillis = 900L,
            interrupt = false,
        )
    }

    /**
     * Duração falada em pt-BR. O motor lê "2" como "dois", então o número vai em
     * dígito; o que se resolve aqui é o singular/plural e o caso de janelas
     * menores que um minuto, onde "0 minutos" seria absurdo.
     */
    internal fun spokenDuration(totalSeconds: Int): String {
        val safe = totalSeconds.coerceAtLeast(0)
        if (safe < 60) return if (safe == 1) "1 segundo" else "$safe segundos"
        val minutes = safe / 60
        val seconds = safe % 60
        val minutesText = if (minutes == 1) "1 minuto" else "$minutes minutos"
        if (seconds == 0) return minutesText
        val secondsText = if (seconds == 1) "1 segundo" else "$seconds segundos"
        return "$minutesText e $secondsText"
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

    /** Recebe o `codigo` de erro devolvido pelo Worker, não uma frase livre. */
    fun blocked(
        motivo: String?,
        validadeSegundos: Int = DEFAULT_CODE_VALIDITY_SECONDS,
    ): PontoVoicePrompt = when (motivo) {
        "PAUSA_PERIODO_JA_UTILIZADA" -> criticalPrompt(
            "blocked-period",
            "Esta pausa já foi utilizada hoje. Nenhum novo registro foi criado.",
        )
        "CODIGO_INVALIDO" -> criticalPrompt(
            "blocked-invalid-code",
            "Código não aceito. Confira os seis caracteres com o supervisor.",
        )
        // Dizer só "expirou" faz a pessoa tentar de novo com o mesmo código.
        // A fala precisa fechar a porta e apontar a saída na mesma frase.
        "CODIGO_EXPIRADO" -> criticalPrompt(
            "blocked-expired-code",
            "Este código expirou. Ele vale apenas ${spokenDuration(validadeSegundos)} depois de gerado. " +
                "Peça um código novo ao supervisor.",
        )
        "CODIGO_BLOQUEADO_TEMPORARIAMENTE" -> criticalPrompt(
            "blocked-too-many-attempts",
            "Muitas tentativas com código errado. Aguarde alguns minutos ou chame o supervisor.",
        )
        "PAUSA_JA_ABERTA" -> criticalPrompt(
            "blocked-open-pause",
            "Você já tem uma pausa aberta. Registre o retorno antes de sair de novo.",
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

        // O cooldown é medido SEMPRE contra o relógio global, nunca contra o da
        // sessão. Antes ele vivia em sessionLastSpokenAt, que prepareSession limpa
        // a cada troca de sessionKey — e o quiosque passa "scan:${scanCycle}", com
        // scanCycle subindo a cada nova tentativa de leitura. Na prática o cooldown
        // era apagado imediatamente antes de ser consultado: quem não era detectado
        // ouvia "Aproxime-se e olhe para a câmera" a cada ciclo, apesar dos 30 s
        // declarados na política.
        //
        // Uma frase é a mesma frase independentemente de quantos ciclos passaram.
        // Já o ORÇAMENTO de instruções continua por sessão, porque ali o reset é
        // proposital: quem chega depois não deve herdar o silêncio de quem estava
        // antes na frente da câmera.
        val previous = globalLastSpokenAt[prompt.key]
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
        // Registrado nos dois: o global é o que sustenta o cooldown entre ciclos, e
        // o da sessão continua servindo para inspeção e para o reset por pessoa.
        globalLastSpokenAt[prompt.key] = nowMillis
        if (sessionKey != null) sessionLastSpokenAt[prompt.key] = nowMillis
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
 * Runtime process-scoped neural-first. When the neural model is already stored,
 * PontoNeuralVoiceRuntime queues prompts behind engine preparation so the
 * Android voice no longer wins merely because initialization was still in
 * progress. Android TextToSpeech remains the fail-open fallback for first model
 * installation and genuine synthesis/playback failures.
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
            // Fallback do TTS do Android. Mantido em paridade com VOICE_SPEED da voz
            // neural para que trocar de motor nao mude o ritmo percebido no quiosque.
            current.setSpeechRate(0.88f)
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
            PontoSpeechBackendTracker.mark(PontoSpeechBackend.ANDROID_TTS_FALLBACK)
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
 * Announces only authoritative point outcomes and blocks. Device authorization
 * is deliberately not owned here; this composable is voice-only.
 */
@Composable
fun PontoVoiceGuidanceEffect(viewModel: PontoCafeViewModel) {
    val context = LocalContext.current
    val state = viewModel.state
    val comprovante = state.comprovante

    LaunchedEffect(Unit) {
        PontoVoiceRuntime.prewarm(context)
    }

    // O quiosque deixou de ter um estágio de "identificação": a pessoa escolhe
    // o próprio nome e digita o código. A voz acompanha esses três passos e
    // fala a recusa com o motivo exato que o servidor devolveu — "código não
    // aceito" e "código expirado" pedem ações diferentes de quem está ali.
    val prompt = remember(
        comprovante,
        state.passo,
        state.acaoEsperada,
        state.erro,
        state.erroCodigo,
        state.selecionado?.id,
        state.validadeCodigoSegundos,
    ) {
        when {
            comprovante != null -> PontoVoicePromptPolicy.receipt(comprovante)
            !state.erro.isNullOrBlank() && state.selecionado != null ->
                PontoVoicePromptPolicy.blocked(state.erroCodigo, state.validadeCodigoSegundos)
            !state.erro.isNullOrBlank() -> PontoVoicePromptPolicy.genericRegistrationError()
            state.passo == PontoStep.DIGITAR_CODIGO ->
                PontoVoicePromptPolicy.kiosk(
                    if (state.acaoEsperada == "RETORNO") {
                        PontoVoiceKioskCue.DIGITAR_CODIGO_RETORNO
                    } else {
                        PontoVoiceKioskCue.DIGITAR_CODIGO_SAIDA
                    },
                    state.validadeCodigoSegundos,
                )
            state.passo == PontoStep.ESCOLHER_PESSOA ->
                PontoVoicePromptPolicy.kiosk(PontoVoiceKioskCue.ESCOLHER_PESSOA)
            else -> null
        }
    }

    LaunchedEffect(prompt?.key, prompt?.text) {
        val currentPrompt = prompt ?: return@LaunchedEffect
        PontoVoiceRuntime.speak(context, currentPrompt)
    }
}
