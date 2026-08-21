package com.pontocafe.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pontocafe.app.camera.FaceEmbeddingEngine
import com.pontocafe.app.camera.FaceFrame
import com.pontocafe.app.data.AppStatusResponse
import com.pontocafe.app.data.CachedFaceCatalog
import com.pontocafe.app.data.FinalizarPausaResponse
import com.pontocafe.app.data.IdentificarBiometriaResponse
import com.pontocafe.app.data.IniciarPausaResponse
import com.pontocafe.app.data.LocalFaceMatch
import com.pontocafe.app.data.LocalFaceMatcher
import com.pontocafe.app.data.LocalFaceResolvedMatch
import com.pontocafe.app.data.PausaAbertaResumo
import com.pontocafe.app.data.PontoCafeRepository
import com.pontocafe.app.data.SecureDeviceTokenStore
import com.pontocafe.app.data.SecureFaceCatalogStore
import com.pontocafe.app.data.SecurePontoOfflineStore
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext


enum class TipoComprovantePonto { INICIO, RETORNO }

enum class PontoRecognitionStage {
    IDENTIFICANDO,
    CONFIRMANDO_IDENTIDADE,
    REGISTRANDO_PONTO,
}

data class ComprovantePonto(
    val tipo: TipoComprovantePonto,
    val nome: String,
    val horarioRegistrado: String,
    val retornoAte: String? = null,
    val duracaoSegundos: Int? = null,
    val limiteSegundos: Int,
    val excedeuLimite: Boolean = false,
    val foraHorario: Boolean = false,
    val pendenteSincronizacao: Boolean = false,
)

data class PontoCafeUiState(
    val deviceConfigured: Boolean = false,
    val carregando: Boolean = false,
    val scanning: Boolean = false,
    val recognitionStage: PontoRecognitionStage? = null,
    val scanCycle: Int = 0,
    val identificacao: IdentificarBiometriaResponse? = null,
    val needsAuthorization: Boolean = false,
    val comprovante: ComprovantePonto? = null,
    val sincronizandoBiometrias: Boolean = false,
    val catalogoBiometricoCarregado: Boolean = false,
    val catalogoBiometricoPronto: Boolean = false,
    val totalBiometrias: Int = 0,
    val erroSincronizacaoBiometrica: String? = null,
    val modoOffline: Boolean = false,
    val sincronizandoPendencias: Boolean = false,
    val eventosPendentes: Int = 0,
    val ultimaConexaoEmMillis: Long? = null,
    val versaoMaisRecente: String? = null,
    val atualizacaoDisponivel: Boolean = false,
    val atualizacaoObrigatoria: Boolean = false,
    val mensagem: String? = null,
    val erro: String? = null,
)

class PontoCafeViewModel(
    private val repository: PontoCafeRepository,
    private val tokenStore: SecureDeviceTokenStore,
    private val faceCatalogStore: SecureFaceCatalogStore,
    private val offlineStore: SecurePontoOfflineStore,
    private val embeddingEngine: FaceEmbeddingEngine,
) : ViewModel() {

    private val catalogRefreshMillis = 15 * 60 * 1000L
    private val catalogMissRefreshCooldownMillis = 30 * 1000L
    private val offlineGraceMillis = 12 * 60 * 60 * 1000L
    private val timezone = ZoneId.of("America/Fortaleza")
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private var pendingOfflineEmbedding: FloatArray? = null
    private var lastCatalogMissRefreshMillis: Long = 0L
    private var lastRegisteredCollaboratorId: String? = null
    private var lastRegisteredAtMillis: Long = 0L
    private val recognitionInFlight = AtomicBoolean(false)
    private val catalogSyncMutex = Mutex()

    var state by mutableStateOf(
        PontoCafeUiState(
            deviceConfigured = tokenStore.hasToken(),
            scanning = tokenStore.hasToken(),
        ),
    )
        private set

    val faceModelReady: Boolean get() = embeddingEngine.isReady
    val faceModelName: String get() = embeddingEngine.modelName

    init {
        if (tokenStore.hasToken() && embeddingEngine.isReady) {
            viewModelScope.launch {
                runCatching { embeddingEngine.warmUp() }
            }
            sincronizarBiometrias(force = false)
            atualizarConectividadeESincronizar()
        }
    }

    fun configurarDispositivo(token: String) {
        val normalizedToken = token.trim()
        if (!Regex("^[A-Za-z0-9]{10}$").matches(normalizedToken)) {
            state = state.copy(
                erro = "Token inválido. Informe exatamente 10 letras ou números, respeitando maiúsculas e minúsculas.",
            )
            return
        }
        if (state.carregando) return

        viewModelScope.launch {
            state = state.copy(carregando = true, mensagem = null, erro = null)
            runCatching { repository.activateDevice(normalizedToken) }
                .onSuccess { deviceToken ->
                    tokenStore.save(deviceToken)
                    faceCatalogStore.clear()
                    offlineStore.clear()
                    pendingOfflineEmbedding = null
                    lastRegisteredCollaboratorId = null
                    lastRegisteredAtMillis = 0L
                    state = PontoCafeUiState(
                        deviceConfigured = true,
                        scanning = true,
                        scanCycle = state.scanCycle + 1,
                        mensagem = "Dispositivo configurado com sucesso.",
                    )
                    viewModelScope.launch { runCatching { embeddingEngine.warmUp() } }
                    sincronizarBiometrias(force = true)
                    atualizarConectividadeESincronizar()
                }
                .onFailure { error ->
                    state = state.copy(
                        carregando = false,
                        erro = PontoCafeRepository.mensagemErro(error),
                    )
                }
        }
    }

    fun removerConfiguracao() {
        tokenStore.clear()
        faceCatalogStore.clear()
        offlineStore.clear()
        pendingOfflineEmbedding = null
        lastRegisteredCollaboratorId = null
        lastRegisteredAtMillis = 0L
        state = PontoCafeUiState(deviceConfigured = false)
    }

    fun atualizarConectividadeESincronizar() {
        if (!state.deviceConfigured) return
        viewModelScope.launch {
            val offlineSnapshot = withContext(Dispatchers.IO) {
                offlineStore.pendingCount() to offlineStore.lastServerOkMillis().takeIf { it > 0L }
            }
            state = state.copy(
                eventosPendentes = offlineSnapshot.first,
                ultimaConexaoEmMillis = offlineSnapshot.second,
            )
            runCatching {
                withContext(Dispatchers.IO) {
                    val horario = repository.consultarHorario()
                    offlineStore.saveRules(horario.regras)
                    runCatching { repository.appStatus() }.getOrNull()
                }
            }.onSuccess { appStatus ->
                marcarServidorOnline(appStatus)
                sincronizarPendenciasOfflineInterno()
            }.onFailure { error ->
                if (PontoCafeRepository.isAuthFailure(error)) {
                    state = state.copy(
                        modoOffline = false,
                        erro = "Este dispositivo não está mais autorizado. Solicite uma nova ativação ao Administrador.",
                    )
                } else {
                    val offlineStatus = withContext(Dispatchers.IO) {
                        offlineStore.canOperateOffline(offlineGraceMillis) to offlineStore.pendingCount()
                    }
                    state = state.copy(
                        modoOffline = offlineStatus.first,
                        eventosPendentes = offlineStatus.second,
                        erro = if (offlineStatus.first) null else state.erro,
                    )
                }
            }
        }
    }

    fun sincronizarBiometrias(force: Boolean = false) {
        if (!state.deviceConfigured || !embeddingEngine.isReady) return
        viewModelScope.launch {
            // Chamadas concorrentes ficam serializadas em vez de descartar um
            // refresh forçado solicitado ao voltar do cadastro biométrico.
            catalogSyncMutex.withLock {
                if (!state.deviceConfigured || !embeddingEngine.isReady) return@withLock
                sincronizarBiometriasInterno(force)
            }
        }
    }

    private suspend fun sincronizarBiometriasInterno(force: Boolean) {
        val cached = withContext(Dispatchers.IO) { faceCatalogStore.read() }
        publishCatalogState(
            catalog = cached,
            syncing = true,
            syncError = null,
        )

        try {
            val catalog = obterCatalogoAtual(
                force = force,
                allowStaleOnFailure = false,
            )
            publishCatalogState(
                catalog = catalog,
                syncing = false,
                syncError = null,
            )
        } catch (error: CancellationException) {
            state = state.copy(sincronizandoBiometrias = false)
            throw error
        } catch (_: Throwable) {
            // Uma falha de rede/parsing não invalida um catálogo local íntegro e
            // compatível. Catálogo ausente, corrompido ou de outro modelo continua
            // indisponível e exige uma sincronização válida.
            val fallback = withContext(Dispatchers.IO) { faceCatalogStore.read() }
            publishCatalogState(
                catalog = fallback,
                syncing = false,
                syncError = CATALOG_SYNC_ERROR,
            )
        }
    }

    private fun publishCatalogState(
        catalog: CachedFaceCatalog?,
        syncing: Boolean,
        syncError: String?,
    ) {
        val compatible = catalog?.takeIf(::catalogCompatible)
        state = state.copy(
            sincronizandoBiometrias = syncing,
            catalogoBiometricoCarregado = compatible != null,
            catalogoBiometricoPronto = compatible?.templates?.isNotEmpty() == true,
            totalBiometrias = compatible?.totalColaboradores ?: 0,
            erroSincronizacaoBiometrica = syncError,
        )
    }

    fun ativarCamera() {
        pendingOfflineEmbedding = null
        state = state.copy(
            scanning = true,
            recognitionStage = null,
            scanCycle = state.scanCycle + 1,
            identificacao = null,
            needsAuthorization = false,
            comprovante = null,
            mensagem = null,
            erro = null,
        )
        sincronizarBiometrias(force = false)
        atualizarConectividadeESincronizar()
    }

    /**
     * Rearme rápido usado entre pessoas de uma fila. Não refaz consultas de
     * conectividade nem sincronização de catálogo: a câmera já está montada e o
     * catálogo possui política própria de validade. Pendências continuam sendo
     * sincronizadas em background depois dos registros.
     */
    fun concluirComprovante() {
        pendingOfflineEmbedding = null
        state = state.copy(
            scanning = true,
            recognitionStage = null,
            scanCycle = state.scanCycle + 1,
            identificacao = null,
            needsAuthorization = false,
            comprovante = null,
            mensagem = null,
            erro = null,
        )
        sincronizarPendenciasOffline()
    }

    fun processarFrame(frame: FaceFrame) {
        if (!state.scanning || state.carregando) {
            if (!frame.bitmap.isRecycled) frame.bitmap.recycle()
            return
        }
        if (!embeddingEngine.isReady) {
            if (!frame.bitmap.isRecycled) frame.bitmap.recycle()
            state = state.copy(
                scanning = false,
                recognitionStage = null,
                erro = "O módulo de reconhecimento facial ainda não está instalado neste dispositivo.",
            )
            return
        }
        if (!recognitionInFlight.compareAndSet(false, true)) {
            if (!frame.bitmap.isRecycled) frame.bitmap.recycle()
            return
        }

        viewModelScope.launch {
            pendingOfflineEmbedding = null
            state = state.copy(
                carregando = true,
                scanning = false,
                recognitionStage = PontoRecognitionStage.IDENTIFICANDO,
                erro = null,
                mensagem = null,
            )
            try {
                // Começamos pelo catálogo local já descriptografado/cacheado. Isso
                // permite decidir imediatamente depois do primeiro embedding, sem
                // esperar uma sincronização de rede e sem calcular fallbacks à toa.
                var catalogo = withContext(Dispatchers.IO) { faceCatalogStore.read() }
                var resolvedDuringInference: LocalFaceResolvedMatch? = null

                val embeddings = embeddingEngine.embedForIdentification(frame) { candidate, candidateIndex ->
                    val currentCatalog = catalogo
                    if (currentCatalog == null || currentCatalog.templates.isEmpty()) {
                        true
                    } else {
                        val candidateMatch = LocalFaceMatcher.match(candidate, currentCatalog)
                        if (candidateMatch != null) {
                            resolvedDuringInference = LocalFaceResolvedMatch(
                                match = candidateMatch,
                                embedding = candidate,
                                candidateIndex = candidateIndex,
                            )
                            false
                        } else {
                            true
                        }
                    }
                }
                require(embeddings.isNotEmpty()) { "Nenhum embedding facial foi gerado." }

                var resolvedMatch = resolvedDuringInference

                // Só consultamos a rede depois de um miss local. Um catálogo já
                // vencido é revalidado aqui; se estava fresco, o refresh por miss
                // abaixo respeita cooldown. Nunca fazemos as duas chamadas no
                // mesmo reconhecimento.
                var catalogRefreshAttempted = false
                if (resolvedMatch == null && catalogNeedsRefresh(catalogo)) {
                    catalogRefreshAttempted = true
                    val previousCatalog = catalogo
                    val refreshed = obterCatalogoAtual(force = false)
                    catalogo = refreshed
                    if (refreshed != null && matchingCatalogChanged(previousCatalog, refreshed)) {
                        resolvedMatch = withContext(Dispatchers.Default) {
                            LocalFaceMatcher.matchBest(embeddings, refreshed)
                        }
                    }
                }

                if (resolvedMatch == null && !catalogRefreshAttempted) {
                    val now = System.currentTimeMillis()
                    val canRefreshAfterMiss =
                        now - lastCatalogMissRefreshMillis >= catalogMissRefreshCooldownMillis
                    if (canRefreshAfterMiss) {
                        lastCatalogMissRefreshMillis = now
                        val previousCatalog = catalogo
                        val refreshed = obterCatalogoAtual(force = true, fullRefresh = false)
                        catalogo = refreshed
                        if (refreshed != null && matchingCatalogChanged(previousCatalog, refreshed)) {
                            withContext(Dispatchers.Default) {
                                resolvedMatch = LocalFaceMatcher.matchBest(embeddings, refreshed)
                            }
                        }
                    }
                }

                val resolved = resolvedMatch
                if (resolved == null) {
                    state = state.copy(
                        carregando = false,
                        scanning = true,
                        recognitionStage = null,
                        scanCycle = state.scanCycle + 1,
                        identificacao = null,
                        erro = if (catalogo?.templates?.isEmpty() != false) {
                            "Nenhum rosto está cadastrado neste dispositivo. Peça ao administrador para cadastrar e sincronizar os colaboradores."
                        } else {
                            "ROSTO NÃO RECONHECIDO. Não foi possível identificar você com segurança. Posicione o rosto dentro do contorno e tente novamente."
                        },
                    )
                    return@launch
                }

                val match = resolved.match
                val embedding = resolved.embedding

                if (samePersonCooldownActive(match.colaborador.id)) {
                    state = state.copy(
                        carregando = false,
                        scanning = true,
                        recognitionStage = null,
                        scanCycle = state.scanCycle + 1,
                        identificacao = null,
                        mensagem = "Registro já concluído. Afaste-se da câmera para liberar a próxima pessoa.",
                        erro = null,
                    )
                    return@launch
                }

                val fastEligible = catalogo?.let { fastPathEligible(match, it) } == true
                var fastRouteUnavailable = false

                if (fastEligible) {
                    state = state.copy(recognitionStage = PontoRecognitionStage.REGISTRANDO_PONTO)
                    val fastResult = withContext(Dispatchers.IO) {
                        repository.registrarRapido(
                            colaboradorId = match.colaborador.id,
                            embedding = embedding,
                            modelo = embeddingEngine.modelName,
                            versaoModelo = embeddingEngine.modelVersion,
                        )
                    }

                    if (fastResult == null) {
                        fastRouteUnavailable = true
                    } else {
                        when (fastResult.status) {
                            "INICIO" -> {
                                val pausa = fastResult.inicio
                                if (pausa != null) {
                                    val colaborador = fastResult.colaborador ?: match.colaborador
                                    aplicarInicioOnline(colaborador.id, colaborador.nome, pausa)
                                    return@launch
                                }
                            }
                            "RETORNO" -> {
                                val pausa = fastResult.retorno
                                if (pausa != null) {
                                    val colaborador = fastResult.colaborador ?: match.colaborador
                                    aplicarRetornoOnline(colaborador.id, colaborador.nome, pausa)
                                    return@launch
                                }
                            }
                            // O servidor decidiu que este caso exige o fluxo completo.
                            // Não auto-confirmamos depois disso: a decisão reforçada do
                            // servidor sempre prevalece.
                            "INTERACAO_NECESSARIA" -> fastRouteUnavailable = false
                        }
                    }
                }

                // Sincronização de eventos antigos nunca bloqueia a pessoa atual.
                if (state.eventosPendentes > 0) sincronizarPendenciasOffline()

                state = state.copy(recognitionStage = PontoRecognitionStage.CONFIRMANDO_IDENTIDADE)
                val identificacao = try {
                    val confirmed = withContext(Dispatchers.IO) {
                        repository.confirmarIdentidadeLocal(
                            colaboradorId = match.colaborador.id,
                            embedding = embedding,
                            modelo = embeddingEngine.modelName,
                            versaoModelo = embeddingEngine.modelVersion,
                        )
                    }
                    pendingOfflineEmbedding = null
                    val lastServerOk = withContext(Dispatchers.IO) {
                        offlineStore.markServerOk()
                        offlineStore.lastServerOkMillis()
                    }
                    state = state.copy(
                        modoOffline = false,
                        ultimaConexaoEmMillis = lastServerOk,
                    )
                    confirmed
                } catch (error: Throwable) {
                    val offlineAllowed = withContext(Dispatchers.IO) {
                        offlineStore.canOperateOffline(offlineGraceMillis)
                    }
                    if (!PontoCafeRepository.isTemporaryFailure(error) || !offlineAllowed) {
                        throw error
                    }
                    pendingOfflineEmbedding = embedding.copyOf()
                    withContext(Dispatchers.IO) {
                        identificacaoOffline(match.colaborador, match.score, embedding)
                    }
                }

                if (!identificacao.reconhecido || identificacao.colaborador == null || identificacao.verificacaoToken.isNullOrBlank()) {
                    pendingOfflineEmbedding = null
                    state = state.copy(
                        carregando = false,
                        scanning = true,
                        recognitionStage = null,
                        scanCycle = state.scanCycle + 1,
                        identificacao = null,
                        erro = identificacao.mensagem
                            ?: "ROSTO NÃO RECONHECIDO. Não foi possível confirmar sua identidade. Tente novamente.",
                    )
                    return@launch
                }

                // Compatibilidade com Worker antigo: quando a rota rápida ainda não
                // existe, um match que já passou pelos critérios reforçados pode
                // eliminar apenas o toque manual, mantendo as duas validações legadas.
                if (
                    fastEligible &&
                    fastRouteUnavailable &&
                    identificacao.verificacaoToken != OFFLINE_VERIFICATION_TOKEN &&
                    identificacao.acaoSugerida != "BLOQUEADO" &&
                    (identificacao.acaoSugerida == "FINALIZAR" || identificacao.dentroHorario == true)
                ) {
                    if (registrarIdentificacaoLegadoRapido(identificacao)) return@launch
                }

                val pendingEvents = withContext(Dispatchers.IO) { offlineStore.pendingCount() }
                state = state.copy(
                    carregando = false,
                    identificacao = identificacao,
                    scanning = false,
                    recognitionStage = null,
                    modoOffline = identificacao.verificacaoToken == OFFLINE_VERIFICATION_TOKEN,
                    eventosPendentes = pendingEvents,
                    erro = null,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                pendingOfflineEmbedding = null
                state = state.copy(
                    carregando = false,
                    scanning = true,
                    recognitionStage = null,
                    scanCycle = state.scanCycle + 1,
                    identificacao = null,
                    erro = PontoCafeRepository.mensagemErro(error),
                )
            }
        }.invokeOnCompletion {
            if (!frame.bitmap.isRecycled) frame.bitmap.recycle()
            recognitionInFlight.set(false)
        }
    }

    private fun fastPathEligible(match: LocalFaceMatch, catalog: CachedFaceCatalog): Boolean {
        val reinforcedThreshold = (catalog.limiar + FAST_SCORE_BONUS).coerceAtMost(0.98)
        val requiredGap = maxOf(
            catalog.margem + FAST_MARGIN_BONUS,
            catalog.margem * FAST_MARGIN_MULTIPLIER,
        )
        val actualGap = match.segundoScore?.let { match.score - it } ?: 1.0
        return match.score >= reinforcedThreshold && actualGap >= requiredGap
    }

    private fun samePersonCooldownActive(collaboratorId: String): Boolean {
        if (lastRegisteredCollaboratorId != collaboratorId) return false
        return System.currentTimeMillis() - lastRegisteredAtMillis < SAME_PERSON_COOLDOWN_MILLIS
    }

    private fun markRegistered(collaboratorId: String) {
        lastRegisteredCollaboratorId = collaboratorId
        lastRegisteredAtMillis = System.currentTimeMillis()
    }

    private suspend fun registrarIdentificacaoLegadoRapido(
        identificacao: IdentificarBiometriaResponse,
    ): Boolean {
        val colaborador = identificacao.colaborador ?: return false
        val token = identificacao.verificacaoToken ?: return false
        state = state.copy(recognitionStage = PontoRecognitionStage.REGISTRANDO_PONTO)
        return try {
            if (identificacao.acaoSugerida == "FINALIZAR") {
                val pausa = withContext(Dispatchers.IO) {
                    repository.finalizar(colaborador.id, token)
                }
                aplicarRetornoOnline(colaborador.id, colaborador.nome, pausa)
            } else {
                val pausa = withContext(Dispatchers.IO) {
                    repository.iniciar(
                        colaboradorId = colaborador.id,
                        verificacaoToken = token,
                    )
                }
                aplicarInicioOnline(colaborador.id, colaborador.nome, pausa)
            }
            true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            state = state.copy(
                carregando = false,
                scanning = false,
                recognitionStage = null,
                identificacao = identificacao,
                erro = PontoCafeRepository.mensagemErro(error),
            )
            false
        }
    }

    private fun identificacaoOffline(
        colaborador: com.pontocafe.app.data.Colaborador,
        score: Double,
        embedding: FloatArray,
    ): IdentificarBiometriaResponse {
        val open = offlineStore.localOpenPause(colaborador.id)

        // A disponibilidade diária é autoritativa e NÃO depende da hora atual.
        // Se manhã e tarde já foram consumidas, nenhuma janela/horário pode
        // mascarar esse estado como simples FORA_HORARIO.
        if (open == null) {
            val completedMorning = offlineStore.completedPauseToday(colaborador.id, "MANHA")
            val completedAfternoon = offlineStore.completedPauseToday(colaborador.id, "TARDE")
            if (completedMorning != null && completedAfternoon != null) {
                // Preserva a auditoria offline quando houver regra em cache. A
                // tentativa é associada à tarde apenas para reutilizar o mesmo
                // mecanismo de fila; o bloqueio abaixo independe dessa gravação.
                val auditRule = offlineStore.snapshot().regras.firstOrNull { it.periodo == "TARDE" }
                    ?: offlineStore.snapshot().regras.firstOrNull { it.periodo == "MANHA" }
                if (auditRule != null) {
                    runCatching {
                        offlineStore.queueOfflineStart(
                            colaborador = colaborador,
                            score = score,
                            embedding = embedding,
                            model = embeddingEngine.modelName,
                            modelVersion = embeddingEngine.modelVersion,
                            rule = auditRule,
                        )
                    }
                }

                return IdentificarBiometriaResponse(
                    reconhecido = true,
                    motivo = "PAUSAS_DO_DIA_JA_UTILIZADAS",
                    mensagem = "Pausas de hoje já utilizadas (2/2). Manhã: ${completedMorning.inicioLocal}–${completedMorning.fimLocal} · Tarde: ${completedAfternoon.inicioLocal}–${completedAfternoon.fimLocal}. Não há mais pausa disponível para hoje.",
                    score = score,
                    verificacaoToken = OFFLINE_VERIFICATION_TOKEN,
                    colaborador = colaborador,
                    acaoSugerida = "BLOQUEADO",
                    pausaAberta = null,
                    dentroHorario = false,
                    periodoAtual = null,
                    limiteSegundos = null,
                )
            }
        }

        val rule = offlineStore.currentRule()
        val completed = if (open == null && rule != null) {
            offlineStore.completedPauseToday(colaborador.id, rule.periodo)
        } else {
            null
        }
        val elapsed = open?.let {
            ((System.currentTimeMillis() - it.inicioEmMillis) / 1000L)
                .coerceAtLeast(0L)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
        }

        if (completed != null && rule != null) {
            runCatching {
                offlineStore.queueOfflineStart(
                    colaborador = colaborador,
                    score = score,
                    embedding = embedding,
                    model = embeddingEngine.modelName,
                    modelVersion = embeddingEngine.modelVersion,
                    rule = rule,
                )
            }
            val minutos = completed.duracaoSegundos / 60
            val segundos = completed.duracaoSegundos % 60
            val duracao = if (segundos > 0) "${minutos} min ${segundos} s" else "${minutos} min"
            val periodoLabel = if (completed.periodo == "MANHA") "manhã" else "tarde"
            return IdentificarBiometriaResponse(
                reconhecido = true,
                motivo = "PAUSA_PERIODO_JA_UTILIZADA",
                mensagem = "Pausa da $periodoLabel já utilizada hoje. Saída: ${completed.inicioLocal} · Retorno: ${completed.fimLocal} · Duração: $duracao. Esta nova tentativa foi registrada e será sincronizada quando a conexão voltar.",
                score = score,
                verificacaoToken = OFFLINE_VERIFICATION_TOKEN,
                colaborador = colaborador,
                acaoSugerida = "BLOQUEADO",
                pausaAberta = null,
                dentroHorario = false,
                periodoAtual = completed.periodo,
                limiteSegundos = completed.limiteSegundos,
            )
        }

        return IdentificarBiometriaResponse(
            reconhecido = true,
            motivo = "MODO_OFFLINE",
            mensagem = "Identidade confirmada localmente. O registro será revalidado biometricamente pelo servidor quando a conexão voltar.",
            score = score,
            verificacaoToken = OFFLINE_VERIFICATION_TOKEN,
            colaborador = colaborador,
            acaoSugerida = if (open != null) "FINALIZAR" else "INICIAR",
            pausaAberta = open?.let {
                PausaAbertaResumo(
                    id = "offline-${colaborador.id}",
                    periodo = it.periodo,
                    inicioEm = Instant.ofEpochMilli(it.inicioEmMillis).toString(),
                    inicioLocal = it.inicioLocal,
                    limiteSegundos = it.limiteSegundos,
                    tempoDecorridoSegundos = elapsed ?: 0,
                )
            },
            dentroHorario = rule != null,
            periodoAtual = rule?.periodo,
            limiteSegundos = rule?.limiteSegundos,
        )
    }

    private fun catalogCompatible(catalog: CachedFaceCatalog): Boolean =
        catalog.modelo == embeddingEngine.modelName &&
            catalog.versaoModelo == embeddingEngine.modelVersion

    private fun catalogNeedsRefresh(
        catalog: CachedFaceCatalog?,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        if (catalog == null || !catalogCompatible(catalog)) return true
        val ageMillis = nowMillis - catalog.sincronizadoEmMillis
        return catalog.sincronizadoEmMillis <= 0L || ageMillis < 0L || ageMillis >= catalogRefreshMillis
    }

    private fun matchingCatalogChanged(
        previous: CachedFaceCatalog?,
        current: CachedFaceCatalog,
    ): Boolean = previous == null ||
        previous.versao != current.versao ||
        previous.modelo != current.modelo ||
        previous.versaoModelo != current.versaoModelo ||
        previous.limiar != current.limiar ||
        previous.margem != current.margem ||
        previous.templates !== current.templates

    private suspend fun obterCatalogoAtual(
        force: Boolean,
        fullRefresh: Boolean = force,
        allowStaleOnFailure: Boolean = true,
    ): CachedFaceCatalog? {
        // SecureFaceCatalogStore mantém a leitura descriptografada em RAM depois
        // da primeira carga, portanto esta chamada é um fast-path em memória.
        val cache = withContext(Dispatchers.IO) { faceCatalogStore.read() }
        val compatibleCache = cache?.takeIf(::catalogCompatible)
        val agora = System.currentTimeMillis()
        val stale = catalogNeedsRefresh(cache, agora)
        if (!force && !stale) return cache

        val response = try {
            // Um cache vazio não serve como prova de que o dispositivo possui o
            // catálogo completo. Na próxima janela de refresh pedimos o payload
            // integral para recuperar caches antigos que tenham sido gravados sem
            // templates por uma falha transitória de leitura/descriptografia.
            val conditionalVersion = compatibleCache
                ?.takeIf { it.templates.isNotEmpty() }
                ?.versao
            withContext(Dispatchers.IO) {
                repository.sincronizarCatalogo(
                    modelo = embeddingEngine.modelName,
                    versaoModelo = embeddingEngine.modelVersion,
                    versaoAtual = if (fullRefresh) null else conditionalVersion,
                )
            }
        } catch (error: Throwable) {
            if (
                allowStaleOnFailure && compatibleCache != null &&
                PontoCafeRepository.isTemporaryFailure(error)
            ) {
                return compatibleCache
            }
            throw error
        }
        withContext(Dispatchers.IO) { offlineStore.markServerOk() }

        require(response.modelo == embeddingEngine.modelName && response.versaoModelo == embeddingEngine.modelVersion) {
            "O servidor retornou um catálogo facial incompatível com este dispositivo."
        }

        if (!response.atualizado) {
            val current = requireNotNull(compatibleCache) {
                "O servidor informou catálogo inalterado sem existir cache local compatível."
            }
            val refreshed = current.copy(
                limiar = response.limiar,
                margem = response.margem,
                sincronizadoEmMillis = agora,
            )
            withContext(Dispatchers.IO) { faceCatalogStore.save(refreshed) }
            return refreshed
        }

        val novo = CachedFaceCatalog(
            versao = response.versao,
            modelo = response.modelo,
            versaoModelo = response.versaoModelo,
            limiar = response.limiar,
            margem = response.margem,
            templates = response.templates,
            sincronizadoEmMillis = agora,
        )
        withContext(Dispatchers.IO) { faceCatalogStore.save(novo) }
        return novo
    }

    fun confirmarIdentidade() {
        val identificacao = state.identificacao ?: return
        val colaborador = identificacao.colaborador ?: return
        val token = identificacao.verificacaoToken ?: return

        if (identificacao.acaoSugerida == "BLOQUEADO") {
            rejeitarIdentidade()
            return
        }

        if (state.modoOffline || token == OFFLINE_VERIFICATION_TOKEN) {
            val embedding = pendingOfflineEmbedding
            if (embedding == null) {
                state = state.copy(erro = "A amostra biométrica offline expirou. Faça o reconhecimento novamente.")
                return
            }
            if (identificacao.acaoSugerida == "FINALIZAR") {
                finalizarPausaOffline(colaborador, embedding)
            } else {
                iniciarPausaOffline(colaborador, identificacao.score ?: 0.0, embedding)
            }
            return
        }

        pendingOfflineEmbedding = null
        if (identificacao.acaoSugerida == "FINALIZAR") {
            finalizarPausa(colaborador.id, colaborador.nome, token)
            return
        }

        if (identificacao.dentroHorario == true) {
            iniciarPausa(
                colaboradorId = colaborador.id,
                nome = colaborador.nome,
                verificacaoToken = token,
            )
        } else {
            state = state.copy(
                needsAuthorization = true,
                mensagem = "Fora do horário permitido. Informe o código temporário gerado pelo administrador.",
                erro = null,
            )
        }
    }

    fun rejeitarIdentidade() {
        pendingOfflineEmbedding = null
        state = state.copy(
            identificacao = null,
            scanning = true,
            recognitionStage = null,
            scanCycle = state.scanCycle + 1,
            needsAuthorization = false,
            mensagem = "Tudo bem. Vamos tentar identificar novamente.",
            erro = null,
        )
    }

    fun confirmarAutorizacao(periodo: String, codigo: String) {
        if (state.modoOffline) {
            pendingOfflineEmbedding = null
            state = state.copy(
                erro = "A autorização fora do horário exige conexão com o servidor.",
                needsAuthorization = false,
            )
            return
        }
        val identificacao = state.identificacao ?: return
        val colaborador = identificacao.colaborador ?: return
        val token = identificacao.verificacaoToken ?: return

        if (codigo.length != 6 || codigo.any { !it.isDigit() }) {
            state = state.copy(erro = "Informe o código de 6 dígitos.")
            return
        }
        if (periodo != "MANHA" && periodo != "TARDE") {
            state = state.copy(erro = "Selecione o período autorizado.")
            return
        }

        iniciarPausa(
            colaboradorId = colaborador.id,
            nome = colaborador.nome,
            verificacaoToken = token,
            periodo = periodo,
            codigoAutorizacao = codigo,
        )
    }

    fun cancelarAutorizacao() {
        rejeitarIdentidade()
    }

    fun limparMensagem() {
        state = state.copy(mensagem = null, erro = null)
    }

    private fun iniciarPausa(
        colaboradorId: String,
        nome: String,
        verificacaoToken: String,
        periodo: String? = null,
        codigoAutorizacao: String? = null,
    ) {
        viewModelScope.launch {
            state = state.copy(
                carregando = true,
                recognitionStage = PontoRecognitionStage.REGISTRANDO_PONTO,
                erro = null,
            )
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.iniciar(
                        colaboradorId = colaboradorId,
                        verificacaoToken = verificacaoToken,
                        periodo = periodo,
                        codigoAutorizacao = codigoAutorizacao,
                    )
                }
            }.onSuccess { pausa ->
                aplicarInicioOnline(colaboradorId, nome, pausa)
            }.onFailure { error ->
                if (error is CancellationException) throw error
                state = state.copy(
                    carregando = false,
                    recognitionStage = null,
                    erro = PontoCafeRepository.mensagemErro(error),
                )
            }
        }
    }

    private fun finalizarPausa(colaboradorId: String, nome: String, verificacaoToken: String) {
        viewModelScope.launch {
            state = state.copy(
                carregando = true,
                recognitionStage = PontoRecognitionStage.REGISTRANDO_PONTO,
                erro = null,
            )
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.finalizar(colaboradorId, verificacaoToken)
                }
            }
                .onSuccess { pausa ->
                    aplicarRetornoOnline(colaboradorId, nome, pausa)
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    state = state.copy(
                        carregando = false,
                        recognitionStage = null,
                        erro = PontoCafeRepository.mensagemErro(error),
                    )
                }
        }
    }

    private suspend fun aplicarInicioOnline(
        colaboradorId: String,
        nome: String,
        pausa: IniciarPausaResponse,
    ) {
        pendingOfflineEmbedding = null
        val offlineStatus = withContext(Dispatchers.IO) {
            offlineStore.recordOnlineStart(colaboradorId, nome, pausa)
            offlineStore.pendingCount() to offlineStore.lastServerOkMillis()
        }
        markRegistered(colaboradorId)
        state = state.copy(
            carregando = false,
            scanning = false,
            recognitionStage = null,
            needsAuthorization = false,
            identificacao = null,
            comprovante = comprovanteInicio(nome, pausa),
            modoOffline = false,
            eventosPendentes = offlineStatus.first,
            ultimaConexaoEmMillis = offlineStatus.second,
            mensagem = null,
            erro = null,
        )
        sincronizarPendenciasOffline()
    }

    private suspend fun aplicarRetornoOnline(
        colaboradorId: String,
        nome: String,
        pausa: FinalizarPausaResponse,
    ) {
        pendingOfflineEmbedding = null
        val offlineStatus = withContext(Dispatchers.IO) {
            offlineStore.recordOnlineFinish(colaboradorId)
            offlineStore.pendingCount() to offlineStore.lastServerOkMillis()
        }
        markRegistered(colaboradorId)
        state = state.copy(
            carregando = false,
            scanning = false,
            recognitionStage = null,
            identificacao = null,
            comprovante = comprovanteRetorno(nome, pausa),
            modoOffline = false,
            eventosPendentes = offlineStatus.first,
            ultimaConexaoEmMillis = offlineStatus.second,
            mensagem = null,
            erro = null,
        )
        sincronizarPendenciasOffline()
    }

    private fun iniciarPausaOffline(
        colaborador: com.pontocafe.app.data.Colaborador,
        score: Double,
        embedding: FloatArray,
    ) {
        state = state.copy(
            carregando = true,
            recognitionStage = PontoRecognitionStage.REGISTRANDO_PONTO,
            erro = null,
        )
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val rule = offlineStore.currentRule() ?: return@withContext null
                    val local = offlineStore.queueOfflineStart(
                        colaborador = colaborador,
                        score = score,
                        embedding = embedding,
                        model = embeddingEngine.modelName,
                        modelVersion = embeddingEngine.modelVersion,
                        rule = rule,
                    )
                    local to offlineStore.pendingCount()
                }
            }.onSuccess { persisted ->
                if (persisted == null) {
                    state = state.copy(
                        carregando = false,
                        recognitionStage = null,
                        needsAuthorization = false,
                        erro = "Sem conexão, uma pausa fora do horário não pode ser autorizada. Aguarde a conexão ou procure o Supervisor.",
                    )
                } else {
                    val (local, pendingCount) = persisted
                    pendingOfflineEmbedding = null
                    markRegistered(colaborador.id)
                    state = state.copy(
                        carregando = false,
                        scanning = false,
                        recognitionStage = null,
                        needsAuthorization = false,
                        identificacao = null,
                        modoOffline = true,
                        eventosPendentes = pendingCount,
                        comprovante = ComprovantePonto(
                            tipo = TipoComprovantePonto.INICIO,
                            nome = colaborador.nome,
                            horarioRegistrado = local.inicioLocal,
                            retornoAte = local.retornoAteLocal,
                            limiteSegundos = local.limiteSegundos,
                            pendenteSincronizacao = true,
                        ),
                        mensagem = null,
                        erro = null,
                    )
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                state = state.copy(
                    carregando = false,
                    recognitionStage = null,
                    erro = error.message ?: "Não foi possível salvar o registro offline.",
                )
            }
        }
    }

    private fun finalizarPausaOffline(
        colaborador: com.pontocafe.app.data.Colaborador,
        embedding: FloatArray,
    ) {
        val score = state.identificacao?.score ?: 0.0
        state = state.copy(
            carregando = true,
            recognitionStage = PontoRecognitionStage.REGISTRANDO_PONTO,
            erro = null,
        )
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val (open, duration) = offlineStore.queueOfflineFinish(
                        colaborador = colaborador,
                        score = score,
                        embedding = embedding,
                        model = embeddingEngine.modelName,
                        modelVersion = embeddingEngine.modelVersion,
                    )
                    Triple(open, duration, offlineStore.pendingCount())
                }
            }.onSuccess { (open, duration, pendingCount) ->
                pendingOfflineEmbedding = null
                markRegistered(colaborador.id)
                state = state.copy(
                    carregando = false,
                    scanning = false,
                    recognitionStage = null,
                    identificacao = null,
                    modoOffline = true,
                    eventosPendentes = pendingCount,
                    comprovante = ComprovantePonto(
                        tipo = TipoComprovantePonto.RETORNO,
                        nome = colaborador.nome,
                        horarioRegistrado = ZonedDateTime.now(timezone).format(timeFormatter),
                        duracaoSegundos = duration,
                        limiteSegundos = open.limiteSegundos,
                        excedeuLimite = duration > open.limiteSegundos,
                        pendenteSincronizacao = true,
                    ),
                    mensagem = null,
                    erro = null,
                )
            }.onFailure { error ->
                if (error is CancellationException) throw error
                state = state.copy(
                    carregando = false,
                    recognitionStage = null,
                    erro = error.message ?: "Não foi possível salvar o retorno offline.",
                )
            }
        }
    }

    fun sincronizarPendenciasOffline() {
        if (!state.deviceConfigured || state.sincronizandoPendencias) return
        viewModelScope.launch { sincronizarPendenciasOfflineInterno() }
    }

    private suspend fun sincronizarPendenciasOfflineInterno() {
        val initialPendingCount = withContext(Dispatchers.IO) { offlineStore.pendingCount() }
        if (state.sincronizandoPendencias || initialPendingCount == 0) return
        state = state.copy(sincronizandoPendencias = true, eventosPendentes = initialPendingCount)
        try {
            var batches = 0
            while (batches < 5) {
                val batch = withContext(Dispatchers.IO) { offlineStore.pendingEvents().take(100) }
                if (batch.isEmpty()) break
                val response = withContext(Dispatchers.IO) { repository.sincronizarOffline(batch) }
                withContext(Dispatchers.IO) {
                    offlineStore.removeProcessed(response.processados)
                    offlineStore.markServerOk()
                }
                batches += 1
                if (response.processados.isEmpty()) break
            }
            val offlineStatus = withContext(Dispatchers.IO) {
                offlineStore.pendingCount() to offlineStore.lastServerOkMillis()
            }
            state = state.copy(
                sincronizandoPendencias = false,
                eventosPendentes = offlineStatus.first,
                modoOffline = false,
                ultimaConexaoEmMillis = offlineStatus.second,
                mensagem = if (offlineStatus.first == 0) null else "Alguns registros ainda aguardam sincronização.",
            )
        } catch (error: CancellationException) {
            state = state.copy(sincronizandoPendencias = false)
            throw error
        } catch (_: Throwable) {
            val offlineStatus = withContext(Dispatchers.IO) {
                offlineStore.pendingCount() to offlineStore.canOperateOffline(offlineGraceMillis)
            }
            state = state.copy(
                sincronizandoPendencias = false,
                eventosPendentes = offlineStatus.first,
                modoOffline = offlineStatus.second,
            )
        }
    }

    private suspend fun marcarServidorOnline(appStatus: AppStatusResponse?) {
        val offlineStatus = withContext(Dispatchers.IO) {
            offlineStore.markServerOk()
            offlineStore.lastServerOkMillis() to offlineStore.pendingCount()
        }
        val latest = appStatus?.latestAndroidVersion
        val minimum = appStatus?.minimumAndroidVersion
        state = state.copy(
            modoOffline = false,
            ultimaConexaoEmMillis = offlineStatus.first,
            versaoMaisRecente = latest,
            atualizacaoDisponivel = latest?.let { compareVersions(BuildConfig.VERSION_NAME, it) < 0 } == true,
            atualizacaoObrigatoria = minimum?.let { compareVersions(BuildConfig.VERSION_NAME, it) < 0 } == true,
            eventosPendentes = offlineStatus.second,
        )
    }

    private fun comprovanteInicio(nome: String, pausa: IniciarPausaResponse) = ComprovantePonto(
        tipo = TipoComprovantePonto.INICIO,
        nome = nome,
        horarioRegistrado = pausa.inicioLocal,
        retornoAte = pausa.retornoAteLocal,
        limiteSegundos = pausa.limiteSegundos,
        foraHorario = pausa.foraHorario,
    )

    private fun comprovanteRetorno(nome: String, pausa: FinalizarPausaResponse) = ComprovantePonto(
        tipo = TipoComprovantePonto.RETORNO,
        nome = nome,
        horarioRegistrado = pausa.fimLocal,
        duracaoSegundos = pausa.duracaoSegundos,
        limiteSegundos = pausa.limiteSegundos,
        excedeuLimite = pausa.excedeuLimite,
    )

    fun formatarTempo(segundos: Int): String = "%02d:%02d".format(segundos / 60, segundos % 60)

    private fun compareVersions(current: String, target: String): Int {
        val left = current.split('.').map { it.toIntOrNull() ?: 0 }
        val right = target.split('.').map { it.toIntOrNull() ?: 0 }
        val size = maxOf(left.size, right.size)
        for (index in 0 until size) {
            val a = left.getOrElse(index) { 0 }
            val b = right.getOrElse(index) { 0 }
            if (a != b) return a.compareTo(b)
        }
        return 0
    }

    companion object {
        private const val OFFLINE_VERIFICATION_TOKEN = "OFFLINE_LOCAL"
        private const val CATALOG_SYNC_ERROR =
            "Não foi possível atualizar os rostos agora. Verifique a conexão e tente novamente."
        private const val FAST_SCORE_BONUS = 0.03
        private const val FAST_MARGIN_BONUS = 0.02
        private const val FAST_MARGIN_MULTIPLIER = 1.5
        private const val SAME_PERSON_COOLDOWN_MILLIS = 8_000L
    }
}
