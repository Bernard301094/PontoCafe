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
import com.pontocafe.app.data.LocalFaceMatcher
import com.pontocafe.app.data.PausaAbertaResumo
import com.pontocafe.app.data.PontoCafeRepository
import com.pontocafe.app.data.RegraCafe
import com.pontocafe.app.data.SecureDeviceTokenStore
import com.pontocafe.app.data.SecureFaceCatalogStore
import com.pontocafe.app.data.SecurePontoOfflineStore
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch


enum class TipoComprovantePonto { INICIO, RETORNO }

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
    val scanCycle: Int = 0,
    val identificacao: IdentificarBiometriaResponse? = null,
    val needsAuthorization: Boolean = false,
    val comprovante: ComprovantePonto? = null,
    val sincronizandoBiometrias: Boolean = false,
    val catalogoBiometricoPronto: Boolean = false,
    val totalBiometrias: Int = 0,
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
    private val offlineGraceMillis = 12 * 60 * 60 * 1000L
    private val timezone = ZoneId.of("America/Fortaleza")
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    var state by mutableStateOf(
        PontoCafeUiState(
            deviceConfigured = tokenStore.hasToken(),
            scanning = tokenStore.hasToken(),
            catalogoBiometricoPronto = faceCatalogStore.read()?.templates?.isNotEmpty() == true,
            totalBiometrias = faceCatalogStore.read()?.templates?.size ?: 0,
            eventosPendentes = offlineStore.pendingCount(),
            ultimaConexaoEmMillis = offlineStore.lastServerOkMillis().takeIf { it > 0L },
        ),
    )
        private set

    val faceModelReady: Boolean get() = embeddingEngine.isReady
    val faceModelName: String get() = embeddingEngine.modelName

    init {
        if (tokenStore.hasToken() && embeddingEngine.isReady) {
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
                    state = PontoCafeUiState(
                        deviceConfigured = true,
                        scanning = true,
                        scanCycle = state.scanCycle + 1,
                        mensagem = "Dispositivo configurado com sucesso.",
                    )
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
        state = PontoCafeUiState(deviceConfigured = false)
    }

    fun atualizarConectividadeESincronizar() {
        if (!state.deviceConfigured) return
        viewModelScope.launch {
            state = state.copy(eventosPendentes = offlineStore.pendingCount())
            runCatching {
                val horario = repository.consultarHorario()
                offlineStore.saveRules(horario.regras)
                val appStatus = runCatching { repository.appStatus() }.getOrNull()
                appStatus
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
                    state = state.copy(
                        modoOffline = offlineStore.canOperateOffline(offlineGraceMillis),
                        eventosPendentes = offlineStore.pendingCount(),
                        erro = if (offlineStore.canOperateOffline(offlineGraceMillis)) null else state.erro,
                    )
                }
            }
        }
    }

    fun sincronizarBiometrias(force: Boolean = false) {
        if (!state.deviceConfigured || !embeddingEngine.isReady || state.sincronizandoBiometrias) return
        viewModelScope.launch {
            state = state.copy(sincronizandoBiometrias = true)
            runCatching { obterCatalogoAtual(force) }
                .onSuccess { catalogo ->
                    state = state.copy(
                        sincronizandoBiometrias = false,
                        catalogoBiometricoPronto = catalogo?.templates?.isNotEmpty() == true,
                        totalBiometrias = catalogo?.templates?.size ?: 0,
                    )
                }
                .onFailure {
                    state = state.copy(
                        sincronizandoBiometrias = false,
                        erro = "Não foi possível sincronizar os rostos cadastrados. Verifique a conexão.",
                    )
                }
        }
    }

    fun ativarCamera() {
        state = state.copy(
            scanning = true,
            scanCycle = state.scanCycle + 1,
            identificacao = null,
            needsAuthorization = false,
            comprovante = null,
            mensagem = null,
            erro = null,
            eventosPendentes = offlineStore.pendingCount(),
        )
        sincronizarBiometrias(force = false)
        atualizarConectividadeESincronizar()
    }

    fun processarFrame(frame: FaceFrame) {
        if (!state.scanning || state.carregando) return
        if (!embeddingEngine.isReady) {
            state = state.copy(
                scanning = false,
                erro = "O módulo de reconhecimento facial ainda não está instalado neste dispositivo.",
            )
            return
        }

        viewModelScope.launch {
            state = state.copy(carregando = true, scanning = false, erro = null, mensagem = null)
            try {
                val embedding = embeddingEngine.embed(frame)
                var catalogo = obterCatalogoAtual(force = false)
                var match = catalogo?.let { LocalFaceMatcher.match(embedding, it) }

                if (match == null) {
                    catalogo = obterCatalogoAtual(force = true)
                    match = catalogo?.let { LocalFaceMatcher.match(embedding, it) }
                }

                if (match == null) {
                    state = state.copy(
                        carregando = false,
                        scanning = true,
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

                if (offlineStore.pendingCount() > 0) {
                    runCatching { sincronizarPendenciasOfflineInterno() }
                }

                val identificacao = try {
                    repository.confirmarIdentidadeLocal(
                        colaboradorId = match.colaborador.id,
                        embedding = embedding,
                        modelo = embeddingEngine.modelName,
                        versaoModelo = embeddingEngine.modelVersion,
                    ).also {
                        offlineStore.markServerOk()
                        state = state.copy(
                            modoOffline = false,
                            ultimaConexaoEmMillis = offlineStore.lastServerOkMillis(),
                        )
                    }
                } catch (error: Throwable) {
                    if (!PontoCafeRepository.isTemporaryFailure(error) || !offlineStore.canOperateOffline(offlineGraceMillis)) {
                        throw error
                    }
                    identificacaoOffline(match.colaborador, match.score)
                }

                if (!identificacao.reconhecido || identificacao.colaborador == null || identificacao.verificacaoToken.isNullOrBlank()) {
                    state = state.copy(
                        carregando = false,
                        scanning = true,
                        scanCycle = state.scanCycle + 1,
                        identificacao = null,
                        erro = identificacao.mensagem
                            ?: "ROSTO NÃO RECONHECIDO. Não foi possível confirmar sua identidade. Tente novamente.",
                    )
                    return@launch
                }

                state = state.copy(
                    carregando = false,
                    identificacao = identificacao,
                    scanning = false,
                    modoOffline = identificacao.verificacaoToken == OFFLINE_VERIFICATION_TOKEN,
                    eventosPendentes = offlineStore.pendingCount(),
                    erro = null,
                )
            } catch (error: Throwable) {
                state = state.copy(
                    carregando = false,
                    scanning = true,
                    scanCycle = state.scanCycle + 1,
                    identificacao = null,
                    erro = PontoCafeRepository.mensagemErro(error),
                )
            }
        }
    }

    private fun identificacaoOffline(
        colaborador: com.pontocafe.app.data.Colaborador,
        score: Double,
    ): IdentificarBiometriaResponse {
        val open = offlineStore.localOpenPause(colaborador.id)
        val rule = offlineStore.currentRule()
        val elapsed = open?.let {
            ((System.currentTimeMillis() - it.inicioEmMillis) / 1000L)
                .coerceAtLeast(0L)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
        }
        return IdentificarBiometriaResponse(
            reconhecido = true,
            motivo = "MODO_OFFLINE",
            mensagem = "Identidade confirmada localmente. O registro será sincronizado quando a conexão voltar.",
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

    private suspend fun obterCatalogoAtual(force: Boolean): CachedFaceCatalog? {
        val cache = faceCatalogStore.read()
        val agora = System.currentTimeMillis()
        val stale = cache == null || agora - cache.sincronizadoEmMillis >= catalogRefreshMillis
        if (!force && !stale) return cache

        val response = try {
            repository.sincronizarCatalogo(
                modelo = embeddingEngine.modelName,
                versaoModelo = embeddingEngine.modelVersion,
                versaoAtual = if (force) null else cache?.versao,
            )
        } catch (error: Throwable) {
            if (cache != null && PontoCafeRepository.isTemporaryFailure(error)) return cache
            throw error
        }
        offlineStore.markServerOk()

        if (!response.atualizado && cache != null) {
            val refreshed = cache.copy(sincronizadoEmMillis = agora)
            faceCatalogStore.save(refreshed)
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
        faceCatalogStore.save(novo)
        return novo
    }

    fun confirmarIdentidade() {
        val identificacao = state.identificacao ?: return
        val colaborador = identificacao.colaborador ?: return
        val token = identificacao.verificacaoToken ?: return

        if (state.modoOffline || token == OFFLINE_VERIFICATION_TOKEN) {
            if (identificacao.acaoSugerida == "FINALIZAR") {
                finalizarPausaOffline(colaborador)
            } else {
                val rule = offlineStore.currentRule()
                if (rule == null) {
                    state = state.copy(
                        erro = "Sem conexão, uma pausa fora do horário não pode ser autorizada. Aguarde a conexão ou procure o Supervisor.",
                        needsAuthorization = false,
                    )
                } else {
                    iniciarPausaOffline(colaborador, identificacao.score ?: 0.0, rule)
                }
            }
            return
        }

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
        state = state.copy(
            identificacao = null,
            scanning = true,
            scanCycle = state.scanCycle + 1,
            needsAuthorization = false,
            mensagem = "Tudo bem. Vamos tentar identificar novamente.",
            erro = null,
        )
    }

    fun confirmarAutorizacao(periodo: String, codigo: String) {
        if (state.modoOffline) {
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

    fun concluirComprovante() {
        ativarCamera()
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
            state = state.copy(carregando = true, erro = null)
            runCatching {
                repository.iniciar(
                    colaboradorId = colaboradorId,
                    verificacaoToken = verificacaoToken,
                    periodo = periodo,
                    codigoAutorizacao = codigoAutorizacao,
                )
            }.onSuccess { pausa ->
                offlineStore.recordOnlineStart(colaboradorId, nome, pausa)
                state = state.copy(
                    carregando = false,
                    needsAuthorization = false,
                    identificacao = null,
                    comprovante = comprovanteInicio(nome, pausa),
                    modoOffline = false,
                    eventosPendentes = offlineStore.pendingCount(),
                    ultimaConexaoEmMillis = offlineStore.lastServerOkMillis(),
                    mensagem = null,
                    erro = null,
                )
                sincronizarPendenciasOffline()
            }.onFailure { error ->
                state = state.copy(
                    carregando = false,
                    erro = PontoCafeRepository.mensagemErro(error),
                )
            }
        }
    }

    private fun finalizarPausa(colaboradorId: String, nome: String, verificacaoToken: String) {
        viewModelScope.launch {
            state = state.copy(carregando = true, erro = null)
            runCatching { repository.finalizar(colaboradorId, verificacaoToken) }
                .onSuccess { pausa ->
                    offlineStore.recordOnlineFinish(colaboradorId)
                    state = state.copy(
                        carregando = false,
                        identificacao = null,
                        comprovante = comprovanteRetorno(nome, pausa),
                        modoOffline = false,
                        eventosPendentes = offlineStore.pendingCount(),
                        ultimaConexaoEmMillis = offlineStore.lastServerOkMillis(),
                        mensagem = null,
                        erro = null,
                    )
                    sincronizarPendenciasOffline()
                }
                .onFailure { error ->
                    state = state.copy(
                        carregando = false,
                        erro = PontoCafeRepository.mensagemErro(error),
                    )
                }
        }
    }

    private fun iniciarPausaOffline(
        colaborador: com.pontocafe.app.data.Colaborador,
        score: Double,
        rule: RegraCafe,
    ) {
        runCatching {
            offlineStore.queueOfflineStart(
                colaborador = colaborador,
                score = score,
                model = embeddingEngine.modelName,
                modelVersion = embeddingEngine.modelVersion,
                rule = rule,
            )
        }.onSuccess { local ->
            state = state.copy(
                carregando = false,
                needsAuthorization = false,
                identificacao = null,
                modoOffline = true,
                eventosPendentes = offlineStore.pendingCount(),
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
        }.onFailure { error ->
            state = state.copy(erro = error.message ?: "Não foi possível salvar o registro offline.")
        }
    }

    private fun finalizarPausaOffline(colaborador: com.pontocafe.app.data.Colaborador) {
        val score = state.identificacao?.score ?: 0.0
        runCatching {
            offlineStore.queueOfflineFinish(
                colaborador = colaborador,
                score = score,
                model = embeddingEngine.modelName,
                modelVersion = embeddingEngine.modelVersion,
            )
        }.onSuccess { (open, duration) ->
            state = state.copy(
                carregando = false,
                identificacao = null,
                modoOffline = true,
                eventosPendentes = offlineStore.pendingCount(),
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
            state = state.copy(erro = error.message ?: "Não foi possível salvar o retorno offline.")
        }
    }

    fun sincronizarPendenciasOffline() {
        if (!state.deviceConfigured || state.sincronizandoPendencias || offlineStore.pendingCount() == 0) return
        viewModelScope.launch { sincronizarPendenciasOfflineInterno() }
    }

    private suspend fun sincronizarPendenciasOfflineInterno() {
        if (state.sincronizandoPendencias || offlineStore.pendingCount() == 0) return
        state = state.copy(sincronizandoPendencias = true, eventosPendentes = offlineStore.pendingCount())
        try {
            var batches = 0
            while (offlineStore.pendingCount() > 0 && batches < 5) {
                val batch = offlineStore.pendingEvents().take(100)
                if (batch.isEmpty()) break
                val response = repository.sincronizarOffline(batch)
                offlineStore.removeProcessed(response.processados)
                offlineStore.markServerOk()
                batches += 1
                if (response.processados.isEmpty()) break
            }
            state = state.copy(
                sincronizandoPendencias = false,
                eventosPendentes = offlineStore.pendingCount(),
                modoOffline = false,
                ultimaConexaoEmMillis = offlineStore.lastServerOkMillis(),
                mensagem = if (offlineStore.pendingCount() == 0) null else "Alguns registros ainda aguardam sincronização.",
            )
        } catch (_: Throwable) {
            state = state.copy(
                sincronizandoPendencias = false,
                eventosPendentes = offlineStore.pendingCount(),
                modoOffline = offlineStore.canOperateOffline(offlineGraceMillis),
            )
        }
    }

    private fun marcarServidorOnline(appStatus: AppStatusResponse?) {
        offlineStore.markServerOk()
        val latest = appStatus?.latestAndroidVersion
        val minimum = appStatus?.minimumAndroidVersion
        state = state.copy(
            modoOffline = false,
            ultimaConexaoEmMillis = offlineStore.lastServerOkMillis(),
            versaoMaisRecente = latest,
            atualizacaoDisponivel = latest?.let { compareVersions(BuildConfig.VERSION_NAME, it) < 0 } == true,
            atualizacaoObrigatoria = minimum?.let { compareVersions(BuildConfig.VERSION_NAME, it) < 0 } == true,
            eventosPendentes = offlineStore.pendingCount(),
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
    }
}
