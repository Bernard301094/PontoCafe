package com.pontocafe.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pontocafe.app.data.AppStatusResponse
import com.pontocafe.app.data.Colaborador
import com.pontocafe.app.data.DeviceAuthInvalidationBus
import com.pontocafe.app.data.InicioPausaResponse
import com.pontocafe.app.data.LocalOpenPause
import com.pontocafe.app.data.PausaAbertaResumo
import com.pontocafe.app.data.PontoCafeRepository
import com.pontocafe.app.data.RetornoPausaResponse
import com.pontocafe.app.data.SecureDeviceTokenStore
import com.pontocafe.app.data.SecurePontoOfflineStore
import com.pontocafe.app.domain.AccessCode
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


enum class TipoComprovantePonto { INICIO, RETORNO }

enum class DeviceAuthorizationState {
    NO_TOKEN,
    CHECKING,
    AUTHORIZED_ONLINE,
    AUTHORIZED_OFFLINE,
    TEMPORARY_FAILURE,
    REVOKED,
}

/**
 * Os três passos do quiosque: escolher o próprio nome, digitar o código que o
 * Supervisor entregou, e ler o comprovante. Nada mais.
 */
enum class PontoStep { ESCOLHER_PESSOA, DIGITAR_CODIGO, COMPROVANTE }

data class ComprovantePonto(
    val tipo: TipoComprovantePonto,
    val nome: String,
    val horarioRegistrado: String,
    val retornoAte: String? = null,
    /**
     * Hora em que a carência acaba e o limite de fato começa a correr.
     *
     * É o que distingue este comprovante do anterior ao código de acesso: quem
     * sai às 09:00 com 1 minuto de carência e 15 de limite tem o relógio a
     * começar às 09:01 e o prazo a terminar às 09:16.
     */
    val contagemComecaAs: String? = null,
    val duracaoSegundos: Int? = null,
    val tempoContadoSegundos: Int? = null,
    val limiteSegundos: Int,
    val carenciaSegundos: Int = 0,
    val excedeuLimite: Boolean = false,
    val foraHorario: Boolean = false,
    val pendenteSincronizacao: Boolean = false,
)

data class PontoCafeUiState(
    val deviceConfigured: Boolean = false,
    val deviceAuthorizationState: DeviceAuthorizationState = DeviceAuthorizationState.NO_TOKEN,
    val carregando: Boolean = false,
    val passo: PontoStep = PontoStep.ESCOLHER_PESSOA,
    val busca: String = "",
    val colaboradores: List<Colaborador> = emptyList(),
    val carregandoColaboradores: Boolean = false,
    val selecionado: Colaborador? = null,
    /** "SAIDA" ou "RETORNO": muda apenas o texto do ecrã, nunca a decisão. */
    val acaoEsperada: String? = null,
    val pausaAberta: PausaAbertaResumo? = null,
    val codigo: String = "",
    val registrando: Boolean = false,
    val comprovante: ComprovantePonto? = null,
    val modoOffline: Boolean = false,
    val sincronizandoPendencias: Boolean = false,
    val eventosPendentes: Int = 0,
    val ultimaConexaoEmMillis: Long? = null,
    val versaoMaisRecente: String? = null,
    val atualizacaoDisponivel: Boolean = false,
    val atualizacaoObrigatoria: Boolean = false,
    val mensagem: String? = null,
    val erro: String? = null,
    /** `codigo` de erro do Worker: é o que a voz usa para dizer o motivo certo. */
    val erroCodigo: String? = null,
    /**
     * Janela para apresentar o código na SAÍDA, como o servidor a define.
     *
     * A tela e a voz leem daqui em vez de trazer o número escrito à mão: se a
     * operação mudar o prazo, os dois acompanham sem novo APK.
     */
    val validadeCodigoSegundos: Int = 120,
) {
    val codigoCompleto: Boolean get() = AccessCode.isComplete(codigo)
}

class PontoCafeViewModel(
    private val repository: PontoCafeRepository,
    private val tokenStore: SecureDeviceTokenStore,
    private val offlineStore: SecurePontoOfflineStore,
) : ViewModel() {

    private val offlineGraceMillis = 12 * 60 * 60 * 1000L
    private val collaboratorRefreshMillis = 5 * 60 * 1000L
    private val timezone = ZoneId.of("America/Fortaleza")
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val hasStoredCredentialAtLaunch = tokenStore.hasToken()
    private var lastCollaboratorLoadMillis: Long = 0L
    private val authorizationValidationInFlight = AtomicBoolean(false)
    private val registrationInFlight = AtomicBoolean(false)
    private var carenciaSegundosServidor: Int = 60

    suspend fun validarPinSaida(pin: String, area: String) = repository.validarPinSaida(pin, area)

    var state by mutableStateOf(
        PontoCafeUiState(
            deviceConfigured = false,
            deviceAuthorizationState = if (hasStoredCredentialAtLaunch) {
                DeviceAuthorizationState.CHECKING
            } else {
                DeviceAuthorizationState.NO_TOKEN
            },
            carregando = hasStoredCredentialAtLaunch,
        ),
    )
        private set

    init {
        viewModelScope.launch {
            DeviceAuthInvalidationBus.events.collect {
                handleRemoteRevocation()
            }
        }

        if (hasStoredCredentialAtLaunch) {
            validarAutorizacaoDoDispositivo(bloquearDuranteValidacao = true)
        }
    }

    // region Dispositivo

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
            try {
                val deviceToken = withContext(Dispatchers.IO) {
                    repository.activateDevice(normalizedToken)
                }
                val credentialPersisted = withContext(Dispatchers.IO) {
                    tokenStore.save(deviceToken) && tokenStore.read() == deviceToken
                }
                if (!credentialPersisted) {
                    withContext(Dispatchers.IO) { tokenStore.clear() }
                    state = state.copy(
                        carregando = false,
                        deviceConfigured = false,
                        deviceAuthorizationState = DeviceAuthorizationState.NO_TOKEN,
                        erro = "A ativação foi aceita, mas a credencial segura não pôde ser salva neste aparelho. Gere um novo token no Administrador e tente novamente.",
                    )
                    return@launch
                }

                val offlineStatus = withContext(Dispatchers.IO) {
                    offlineStore.pendingCount() to offlineStore.hasQuarantinedPendingEvents()
                }
                state = PontoCafeUiState(
                    deviceConfigured = true,
                    deviceAuthorizationState = DeviceAuthorizationState.AUTHORIZED_ONLINE,
                    eventosPendentes = offlineStatus.first,
                    mensagem = if (offlineStatus.second) {
                        "Dispositivo configurado. Há registros offline preservados de uma credencial anterior; eles não serão reenviados automaticamente."
                    } else {
                        "Dispositivo configurado com sucesso."
                    },
                )
                carregarColaboradores(force = true)
                atualizarConectividadeESincronizar()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                state = state.copy(
                    carregando = false,
                    erro = PontoCafeRepository.mensagemErro(error),
                )
            }
        }
    }

    /** Explicit local reset requested by the user/admin. This remains destructive. */
    fun removerConfiguracao() {
        tokenStore.clear()
        offlineStore.clear()
        lastCollaboratorLoadMillis = 0L
        state = PontoCafeUiState(
            deviceConfigured = false,
            deviceAuthorizationState = DeviceAuthorizationState.NO_TOKEN,
        )
    }

    /**
     * Authoritative credential validation. At process start / foreground resume
     * the kiosk is withheld until the server accepts the token (or the existing
     * offline grace policy explicitly allows operation).
     */
    fun validarAutorizacaoDoDispositivo(bloquearDuranteValidacao: Boolean) {
        if (!tokenStore.hasToken()) {
            state = PontoCafeUiState(
                deviceConfigured = false,
                deviceAuthorizationState = DeviceAuthorizationState.NO_TOKEN,
            )
            return
        }
        if (!authorizationValidationInFlight.compareAndSet(false, true)) return

        val wasOperational = state.deviceConfigured
        if (bloquearDuranteValidacao) {
            state = state.copy(
                deviceConfigured = false,
                deviceAuthorizationState = DeviceAuthorizationState.CHECKING,
                carregando = true,
                erro = null,
            )
        }

        viewModelScope.launch {
            try {
                val appStatus = withContext(Dispatchers.IO) {
                    val horario = repository.consultarHorario()
                    offlineStore.saveRules(horario.regras)
                    carenciaSegundosServidor = horario.carenciaSegundos
                    runCatching { repository.appStatus() }.getOrNull()
                }
                val pending = withContext(Dispatchers.IO) { offlineStore.pendingCount() }
                state = state.copy(
                    deviceConfigured = true,
                    deviceAuthorizationState = DeviceAuthorizationState.AUTHORIZED_ONLINE,
                    carregando = false,
                    modoOffline = false,
                    eventosPendentes = pending,
                    erro = null,
                )
                marcarServidorOnline(appStatus)
                if (!wasOperational) carregarColaboradores(force = true)
                sincronizarPendenciasOfflineInterno()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (PontoCafeRepository.isAuthFailure(error)) {
                    handleRemoteRevocation()
                } else {
                    val offlineStatus = withContext(Dispatchers.IO) {
                        offlineStore.canOperateOffline(offlineGraceMillis) to offlineStore.pendingCount()
                    }
                    when {
                        offlineStatus.first -> {
                            state = state.copy(
                                deviceConfigured = true,
                                deviceAuthorizationState = DeviceAuthorizationState.AUTHORIZED_OFFLINE,
                                carregando = false,
                                modoOffline = true,
                                eventosPendentes = offlineStatus.second,
                                erro = null,
                            )
                        }
                        !bloquearDuranteValidacao && wasOperational -> {
                            // A transient watchdog failure must not revoke a session
                            // that was already authoritatively accepted.
                            state = state.copy(
                                carregando = false,
                                eventosPendentes = offlineStatus.second,
                            )
                        }
                        else -> {
                            state = state.copy(
                                deviceConfigured = false,
                                deviceAuthorizationState = DeviceAuthorizationState.TEMPORARY_FAILURE,
                                carregando = false,
                                modoOffline = false,
                                eventosPendentes = offlineStatus.second,
                                erro = "Não foi possível validar este dispositivo agora. Verifique a conexão e tente novamente.",
                            )
                        }
                    }
                }
            } finally {
                authorizationValidationInFlight.set(false)
            }
        }
    }

    private suspend fun handleRemoteRevocation() {
        if (state.deviceAuthorizationState == DeviceAuthorizationState.REVOKED && !tokenStore.hasToken()) return
        registrationInFlight.set(false)

        val pending = withContext(Dispatchers.IO) {
            runCatching { offlineStore.quarantinePendingEvents("DEVICE_AUTH_INVALID") }
            runCatching { tokenStore.clear() }
            offlineStore.pendingCount()
        }

        state = PontoCafeUiState(
            deviceConfigured = false,
            deviceAuthorizationState = DeviceAuthorizationState.REVOKED,
            eventosPendentes = pending,
            mensagem = if (pending > 0) {
                "$pending registro(s) offline foram preservados neste aparelho e colocados em quarentena para evitar atribuição a outra credencial."
            } else {
                null
            },
            erro = "Este dispositivo não está mais autorizado. Solicite uma nova ativação ao Administrador.",
        )
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
                    carenciaSegundosServidor = horario.carenciaSegundos
                    runCatching { repository.appStatus() }.getOrNull()
                }
            }.onSuccess { appStatus ->
                state = state.copy(deviceAuthorizationState = DeviceAuthorizationState.AUTHORIZED_ONLINE)
                marcarServidorOnline(appStatus)
                sincronizarPendenciasOfflineInterno()
            }.onFailure { error ->
                if (PontoCafeRepository.isAuthFailure(error)) {
                    handleRemoteRevocation()
                } else {
                    val offlineStatus = withContext(Dispatchers.IO) {
                        offlineStore.canOperateOffline(offlineGraceMillis) to offlineStore.pendingCount()
                    }
                    state = state.copy(
                        deviceAuthorizationState = if (offlineStatus.first) {
                            DeviceAuthorizationState.AUTHORIZED_OFFLINE
                        } else {
                            state.deviceAuthorizationState
                        },
                        modoOffline = offlineStatus.first,
                        eventosPendentes = offlineStatus.second,
                        erro = if (offlineStatus.first) null else state.erro,
                    )
                }
            }
        }
    }

    // endregion

    // region Lista de pessoas

    fun carregarColaboradores(force: Boolean = false) {
        if (!state.deviceConfigured) return
        val now = System.currentTimeMillis()
        if (!force && lastCollaboratorLoadMillis > 0L && now - lastCollaboratorLoadMillis < collaboratorRefreshMillis) {
            return
        }
        if (state.carregandoColaboradores) return

        viewModelScope.launch {
            state = state.copy(carregandoColaboradores = true)
            runCatching {
                withContext(Dispatchers.IO) { repository.listarColaboradores() }
            }.onSuccess { pessoas ->
                lastCollaboratorLoadMillis = System.currentTimeMillis()
                state = state.copy(
                    colaboradores = pessoas,
                    carregandoColaboradores = false,
                    deviceAuthorizationState = DeviceAuthorizationState.AUTHORIZED_ONLINE,
                    modoOffline = false,
                )
            }.onFailure { error ->
                if (PontoCafeRepository.isAuthFailure(error)) {
                    handleRemoteRevocation()
                    return@onFailure
                }
                // A lista anterior continua utilizável: quem já apareceu no ecrã
                // não desaparece só porque a rede caiu no meio de um refresh.
                state = state.copy(
                    carregandoColaboradores = false,
                    modoOffline = true,
                    erro = if (state.colaboradores.isEmpty()) {
                        "Não foi possível carregar a lista de nomes. Verifique a conexão."
                    } else {
                        state.erro
                    },
                )
            }
        }
    }

    fun atualizarBusca(texto: String) {
        state = state.copy(busca = texto)
    }

    /**
     * A pessoa escolheu o próprio nome. Consultamos o estado da pausa só para
     * escrever a frase certa no teclado; se a consulta falhar, o quiosque segue
     * com o que sabe localmente e o servidor decide no momento do registro.
     */
    fun selecionarColaborador(colaborador: Colaborador) {
        state = state.copy(
            passo = PontoStep.DIGITAR_CODIGO,
            selecionado = colaborador,
            codigo = "",
            acaoEsperada = null,
            pausaAberta = null,
            mensagem = null,
            erro = null,
        )

        viewModelScope.launch {
            val local = withContext(Dispatchers.IO) { offlineStore.localOpenPause(colaborador.id) }
            if (local != null) aplicarPausaLocalNoEstado(colaborador, local)

            runCatching {
                withContext(Dispatchers.IO) { repository.estadoDaPausa(colaborador.id) }
            }.onSuccess { estado ->
                if (state.selecionado?.id != colaborador.id) return@onSuccess
                state = state.copy(
                    acaoEsperada = estado.acaoEsperada,
                    pausaAberta = estado.pausaAberta,
                )
            }.onFailure { error ->
                if (PontoCafeRepository.isAuthFailure(error)) handleRemoteRevocation()
                // Sem rede o passo continua utilizável: o texto cai para o que a
                // pausa local diz, ou para o pedido genérico de código.
            }
        }
    }

    private fun aplicarPausaLocalNoEstado(colaborador: Colaborador, local: LocalOpenPause) {
        if (state.selecionado?.id != colaborador.id) return
        val decorrido = ((System.currentTimeMillis() - local.inicioEmMillis) / 1000L)
            .coerceAtLeast(0L)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
        state = state.copy(
            acaoEsperada = "RETORNO",
            pausaAberta = PausaAbertaResumo(
                id = "local:${colaborador.id}",
                periodo = local.periodo,
                inicioEm = "",
                inicioLocal = local.inicioLocal,
                limiteSegundos = local.limiteSegundos,
                carenciaSegundos = local.carenciaSegundos,
                tempoDecorridoSegundos = decorrido,
                retornoAteLocal = local.retornoAteLocal,
            ),
        )
    }

    fun voltarParaLista() {
        state = state.copy(
            passo = PontoStep.ESCOLHER_PESSOA,
            selecionado = null,
            codigo = "",
            acaoEsperada = null,
            pausaAberta = null,
            erro = null,
            mensagem = null,
        )
    }

    // endregion

    // region Código e registro

    fun digitarCodigo(texto: String) {
        state = state.copy(codigo = AccessCode.sanitizeInput(texto), erro = null, erroCodigo = null)
    }

    fun apagarUltimoDigito() {
        state = state.copy(codigo = state.codigo.dropLast(1), erro = null, erroCodigo = null)
    }

    fun acrescentarDigito(char: Char) {
        val canonical = AccessCode.canonicalChar(char) ?: return
        if (state.codigo.length >= AccessCode.LENGTH) return
        state = state.copy(codigo = state.codigo + canonical, erro = null, erroCodigo = null)
    }

    /**
     * Envia (pessoa, código) e mostra o que o servidor decidiu.
     *
     * Não há caminho separado para saída e retorno no aparelho: o mesmo botão faz
     * as duas coisas porque só o servidor sabe se aquele código já foi usado para
     * sair. Sem rede, o registro entra na fila e é validado na sincronização.
     */
    fun registrar() {
        val colaborador = state.selecionado ?: return
        val codigo = state.codigo
        if (!AccessCode.isComplete(codigo)) {
            state = state.copy(erro = "Digite os ${AccessCode.LENGTH} caracteres do código.")
            return
        }
        if (!registrationInFlight.compareAndSet(false, true)) return

        viewModelScope.launch {
            state = state.copy(registrando = true, erro = null, erroCodigo = null, mensagem = null)
            try {
                val resultado = withContext(Dispatchers.IO) { repository.registrar(colaborador.id, codigo) }
                aplicarResultadoOnline(colaborador, resultado.status, resultado.inicio, resultado.retorno)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                when {
                    PontoCafeRepository.isAuthFailure(error) -> handleRemoteRevocation()
                    PontoCafeRepository.isTemporaryFailure(error) -> registrarOffline(colaborador, codigo)
                    else -> state = state.copy(
                        registrando = false,
                        // O código continua no campo: quem errou um caractere
                        // corrige um caractere, não redigita os seis.
                        erro = PontoCafeRepository.mensagemErro(error),
                        erroCodigo = PontoCafeRepository.codigoErro(error),
                    )
                }
            } finally {
                registrationInFlight.set(false)
            }
        }
    }

    private suspend fun aplicarResultadoOnline(
        colaborador: Colaborador,
        status: String,
        inicio: InicioPausaResponse?,
        retorno: RetornoPausaResponse?,
    ) {
        if (status == "INICIO" && inicio != null) {
            withContext(Dispatchers.IO) {
                runCatching { offlineStore.recordOnlineStart(colaborador.id, colaborador.nome, inicio) }
            }
            state = state.copy(
                registrando = false,
                passo = PontoStep.COMPROVANTE,
                codigo = "",
                comprovante = comprovanteInicio(colaborador.nome, inicio),
            )
            return
        }

        if (status == "RETORNO" && retorno != null) {
            withContext(Dispatchers.IO) {
                runCatching { offlineStore.recordOnlineFinish(colaborador.id) }
            }
            state = state.copy(
                registrando = false,
                passo = PontoStep.COMPROVANTE,
                codigo = "",
                comprovante = comprovanteRetorno(colaborador.nome, retorno),
            )
            return
        }

        state = state.copy(
            registrando = false,
            erro = "O servidor confirmou o registro sem informar se foi saída ou retorno. Chame o Supervisor.",
        )
    }

    private suspend fun registrarOffline(colaborador: Colaborador, codigo: String) {
        val temPausaLocal = withContext(Dispatchers.IO) { offlineStore.localOpenPause(colaborador.id) } != null
        if (temPausaLocal) {
            finalizarPausaOffline(colaborador, codigo)
        } else {
            iniciarPausaOffline(colaborador, codigo)
        }
    }

    private suspend fun iniciarPausaOffline(colaborador: Colaborador, codigo: String) {
        val regra = withContext(Dispatchers.IO) { offlineStore.resolveRule() }
        if (regra == null) {
            state = state.copy(
                registrando = false,
                modoOffline = true,
                erro = "Sem conexão e sem os horários do café guardados neste aparelho. Conecte o quiosque à rede uma vez antes de registrar offline.",
            )
            return
        }

        val carencia = carenciaSegundosServidor
        runCatching {
            withContext(Dispatchers.IO) {
                offlineStore.queueOfflineStart(colaborador, codigo, regra, carencia)
            }
        }.onSuccess { pausa ->
            val pendentes = withContext(Dispatchers.IO) { offlineStore.pendingCount() }
            state = state.copy(
                registrando = false,
                passo = PontoStep.COMPROVANTE,
                codigo = "",
                modoOffline = true,
                eventosPendentes = pendentes,
                comprovante = ComprovantePonto(
                    tipo = TipoComprovantePonto.INICIO,
                    nome = colaborador.nome,
                    horarioRegistrado = pausa.inicioLocal,
                    retornoAte = pausa.retornoAteLocal,
                    contagemComecaAs = ZonedDateTime.now(timezone)
                        .plusSeconds(carencia.toLong())
                        .format(timeFormatter),
                    limiteSegundos = pausa.limiteSegundos,
                    carenciaSegundos = carencia,
                    pendenteSincronizacao = true,
                ),
            )
        }.onFailure { error ->
            val pendentes = withContext(Dispatchers.IO) { offlineStore.pendingCount() }
            state = state.copy(
                registrando = false,
                modoOffline = true,
                eventosPendentes = pendentes,
                erro = error.message ?: "Não foi possível salvar a saída offline.",
            )
        }
    }

    private suspend fun finalizarPausaOffline(colaborador: Colaborador, codigo: String) {
        runCatching {
            withContext(Dispatchers.IO) { offlineStore.queueOfflineFinish(colaborador, codigo) }
        }.onSuccess { (pausa, duracao) ->
            val pendentes = withContext(Dispatchers.IO) { offlineStore.pendingCount() }
            val contado = (duracao - pausa.carenciaSegundos).coerceAtLeast(0)
            state = state.copy(
                registrando = false,
                passo = PontoStep.COMPROVANTE,
                codigo = "",
                modoOffline = true,
                eventosPendentes = pendentes,
                comprovante = ComprovantePonto(
                    tipo = TipoComprovantePonto.RETORNO,
                    nome = colaborador.nome,
                    horarioRegistrado = ZonedDateTime.now(timezone).format(timeFormatter),
                    duracaoSegundos = duracao,
                    tempoContadoSegundos = contado,
                    limiteSegundos = pausa.limiteSegundos,
                    carenciaSegundos = pausa.carenciaSegundos,
                    excedeuLimite = contado > pausa.limiteSegundos,
                    pendenteSincronizacao = true,
                ),
            )
        }.onFailure { error ->
            val pendentes = withContext(Dispatchers.IO) { offlineStore.pendingCount() }
            state = state.copy(
                registrando = false,
                modoOffline = true,
                eventosPendentes = pendentes,
                erro = error.message ?: "Não foi possível salvar o retorno offline.",
            )
        }
    }

    fun concluirComprovante() {
        state = state.copy(
            passo = PontoStep.ESCOLHER_PESSOA,
            comprovante = null,
            selecionado = null,
            codigo = "",
            busca = "",
            acaoEsperada = null,
            pausaAberta = null,
            erro = null,
            mensagem = null,
        )
        carregarColaboradores(force = false)
        atualizarConectividadeESincronizar()
    }

    fun limparMensagem() {
        state = state.copy(mensagem = null, erro = null, erroCodigo = null)
    }

    // endregion

    // region Sincronização

    fun sincronizarPendenciasOffline() {
        if (!state.deviceConfigured || state.sincronizandoPendencias) return
        viewModelScope.launch { sincronizarPendenciasOfflineInterno() }
    }

    private suspend fun sincronizarPendenciasOfflineInterno() {
        val quarantined = withContext(Dispatchers.IO) { offlineStore.hasQuarantinedPendingEvents() }
        if (quarantined) {
            val pending = withContext(Dispatchers.IO) { offlineStore.pendingCount() }
            state = state.copy(
                sincronizandoPendencias = false,
                eventosPendentes = pending,
                mensagem = "$pending registro(s) offline estão preservados em quarentena e não serão enviados com esta credencial.",
            )
            return
        }

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
                    offlineStore.recordSyncResults(response.resultados)
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
                deviceAuthorizationState = if (offlineStatus.second) {
                    DeviceAuthorizationState.AUTHORIZED_OFFLINE
                } else {
                    state.deviceAuthorizationState
                },
            )
        }
    }

    private suspend fun marcarServidorOnline(appStatus: AppStatusResponse?) {
        val offlineStatus = withContext(Dispatchers.IO) {
            offlineStore.markServerOk()
            offlineStore.lastServerOkMillis() to offlineStore.pendingCount()
        }
        appStatus?.let {
            carenciaSegundosServidor = it.carenciaSegundos
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
            validadeCodigoSegundos = appStatus?.codigoValidadeSegundos ?: state.validadeCodigoSegundos,
        )
    }

    // endregion

    private fun comprovanteInicio(nome: String, pausa: InicioPausaResponse) = ComprovantePonto(
        tipo = TipoComprovantePonto.INICIO,
        nome = nome,
        horarioRegistrado = pausa.inicioLocal,
        retornoAte = pausa.retornoAteLocal,
        contagemComecaAs = pausa.contagemInicioLocal,
        limiteSegundos = pausa.limiteSegundos,
        carenciaSegundos = pausa.carenciaSegundos,
        foraHorario = pausa.foraHorario,
    )

    private fun comprovanteRetorno(nome: String, pausa: RetornoPausaResponse) = ComprovantePonto(
        tipo = TipoComprovantePonto.RETORNO,
        nome = nome,
        horarioRegistrado = pausa.fimLocal,
        duracaoSegundos = pausa.duracaoSegundos,
        tempoContadoSegundos = pausa.tempoContadoSegundos,
        limiteSegundos = pausa.limiteSegundos,
        carenciaSegundos = pausa.carenciaSegundos,
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
}
