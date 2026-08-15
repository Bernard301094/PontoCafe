package com.pontocafe.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pontocafe.app.camera.FaceEmbeddingEngine
import com.pontocafe.app.camera.FaceFrame
import com.pontocafe.app.data.CachedFaceCatalog
import com.pontocafe.app.data.FinalizarPausaResponse
import com.pontocafe.app.data.IdentificarBiometriaResponse
import com.pontocafe.app.data.IniciarPausaResponse
import com.pontocafe.app.data.LocalFaceMatcher
import com.pontocafe.app.data.PontoCafeRepository
import com.pontocafe.app.data.SecureDeviceTokenStore
import com.pontocafe.app.data.SecureFaceCatalogStore
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
    val mensagem: String? = null,
    val erro: String? = null,
)

class PontoCafeViewModel(
    private val repository: PontoCafeRepository,
    private val tokenStore: SecureDeviceTokenStore,
    private val faceCatalogStore: SecureFaceCatalogStore,
    private val embeddingEngine: FaceEmbeddingEngine,
) : ViewModel() {

    private val catalogRefreshMillis = 15 * 60 * 1000L

    var state by mutableStateOf(
        PontoCafeUiState(
            deviceConfigured = tokenStore.hasToken(),
            scanning = tokenStore.hasToken(),
            catalogoBiometricoPronto = faceCatalogStore.read()?.templates?.isNotEmpty() == true,
            totalBiometrias = faceCatalogStore.read()?.templates?.size ?: 0,
        ),
    )
        private set

    val faceModelReady: Boolean get() = embeddingEngine.isReady
    val faceModelName: String get() = embeddingEngine.modelName

    init {
        if (tokenStore.hasToken() && embeddingEngine.isReady) {
            sincronizarBiometrias(force = false)
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
        tokenStore.save(normalizedToken)
        faceCatalogStore.clear()
        state = PontoCafeUiState(
            deviceConfigured = true,
            scanning = true,
            scanCycle = state.scanCycle + 1,
            mensagem = "Dispositivo configurado com sucesso.",
        )
        sincronizarBiometrias(force = true)
    }

    fun removerConfiguracao() {
        tokenStore.clear()
        faceCatalogStore.clear()
        state = PontoCafeUiState(deviceConfigured = false)
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
        )
        sincronizarBiometrias(force = false)
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

                val identificacao = repository.confirmarIdentidadeLocal(
                    colaboradorId = match.colaborador.id,
                    embedding = embedding,
                    modelo = embeddingEngine.modelName,
                    versaoModelo = embeddingEngine.modelVersion,
                )

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

    private suspend fun obterCatalogoAtual(force: Boolean): CachedFaceCatalog? {
        val cache = faceCatalogStore.read()
        val agora = System.currentTimeMillis()
        val stale = cache == null || agora - cache.sincronizadoEmMillis >= catalogRefreshMillis
        if (!force && !stale) return cache

        val response = repository.sincronizarCatalogo(
            modelo = embeddingEngine.modelName,
            versaoModelo = embeddingEngine.modelVersion,
            versaoAtual = if (force) null else cache?.versao,
        )

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
                state = state.copy(
                    carregando = false,
                    needsAuthorization = false,
                    identificacao = null,
                    comprovante = comprovanteInicio(nome, pausa),
                    mensagem = null,
                    erro = null,
                )
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
                    state = state.copy(
                        carregando = false,
                        identificacao = null,
                        comprovante = comprovanteRetorno(nome, pausa),
                        mensagem = null,
                        erro = null,
                    )
                }
                .onFailure { error ->
                    state = state.copy(
                        carregando = false,
                        erro = PontoCafeRepository.mensagemErro(error),
                    )
                }
        }
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
}
