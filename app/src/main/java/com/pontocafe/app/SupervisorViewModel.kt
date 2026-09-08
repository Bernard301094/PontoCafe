package com.pontocafe.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pontocafe.app.data.AccessCodeCreatedResponse
import com.pontocafe.app.data.AccessCodeItem
import com.pontocafe.app.data.Colaborador
import com.pontocafe.app.data.OperationalAlertHistoryStore
import com.pontocafe.app.data.PausaSupervisor
import com.pontocafe.app.data.SupervisorReportResponse
import com.pontocafe.app.data.SupervisorRepository
import com.pontocafe.app.notifications.SupervisorAlertNotifier
import com.pontocafe.app.ui.SUPERVISOR_LIVE_ALERT_CRITICAL_THRESHOLD_SECONDS
import com.pontocafe.app.ui.SUPERVISOR_LIVE_ALERT_WARNING_THRESHOLD_SECONDS
import com.pontocafe.app.ui.SupervisorLiveAlertType
import com.pontocafe.app.ui.selectSupervisorLiveAlert
import com.pontocafe.app.ui.limiteEfetivoSegundos
import com.pontocafe.app.ui.tempoAtualSupervisor
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


enum class SupervisorDestination {
    LOGIN,
    AO_VIVO,
    HISTORICO,
    COLABORADORES,
    NOVO_COLABORADOR,
    CODIGOS,
    RELATORIOS,
}

enum class ManualPunchType { INICIO, FIM }

/**
 * Feedback de um registro manual de ponto (ver proposta "Registro Manual de
 * Ponto"). Só existe depois de iniciarPausaManual/finalizarPausaManual
 * responderem com sucesso -- os endpoints ainda não existem no backend.
 */
data class ManualPunchResult(
    val colaboradorNome: String,
    val tipo: ManualPunchType,
    val horarioLocal: String,
)

data class SupervisorUiState(
    val destination: SupervisorDestination = SupervisorDestination.LOGIN,
    val carregando: Boolean = false,
    val pausasAtivas: List<PausaSupervisor> = emptyList(),
    val ultimoRetorno: PausaSupervisor? = null,
    val historico: List<PausaSupervisor> = emptyList(),
    val historicoData: String? = null,
    val colaboradores: List<Colaborador> = emptyList(),
    val relatorio: SupervisorReportResponse? = null,
    val relatorioAnterior: SupervisorReportResponse? = null,
    val relatorioInicio: String? = null,
    val relatorioFim: String? = null,
    val codigosAtivos: List<AccessCodeItem> = emptyList(),
    val codigoEmitido: AccessCodeCreatedResponse? = null,
    val manualPunchResult: ManualPunchResult? = null,
    val colaboradorSelecionado: Colaborador? = null,
    /**
     * Quem tem uma ação individual em curso (emitir ou cancelar código).
     *
     * [carregando] é de tela inteira: passá-lo a cada linha acendia todos os
     * botões "Gerar" da lista ao emitir um código para uma pessoa só.
     */
    val colaboradorOcupadoId: String? = null,
    val sessaoAdministrativa: Boolean = false,
    val ultimaAtualizacaoAoVivoEmMillis: Long? = null,
    val conexaoAoVivoOk: Boolean = true,
    val mensagem: String? = null,
    val erro: String? = null,
)

class SupervisorViewModel(
    private val repository: SupervisorRepository,
    private val applicationContext: Context,
) : ViewModel() {
    var state by mutableStateOf(
        SupervisorUiState(
            destination = if (repository.hasSession()) SupervisorDestination.AO_VIVO else SupervisorDestination.LOGIN,
            sessaoAdministrativa = repository.usingAdminSession(),
        ),
    )
        private set

    private val liveAlertHistoryStore by lazy { OperationalAlertHistoryStore(applicationContext) }
    private var liveAlertMonitoringJob: Job? = null
    private var liveAlertBaseline: Map<String, PausaSupervisor>? = null
    private var liveAlertOverdueBaseline: Set<String> = emptySet()
    private var liveAlertWarningBaseline: Set<String> = emptySet()
    private var liveAlertCriticalBaseline: Set<String> = emptySet()
    private var atualizacaoAoVivoEmAndamento = false
    private var atualizacaoPausasEmAndamento = false
    private var atualizacaoRetornoEmAndamento = false


    init {
        if (repository.hasSession()) atualizarAoVivo()
    }

    fun prepararEntrada() {
        if (state.destination == SupervisorDestination.LOGIN && repository.hasSession()) {
            state = state.copy(
                sessaoAdministrativa = repository.usingAdminSession(),
                erro = null,
                mensagem = null,
            )
            atualizarAoVivo()
        }
    }

    fun login(email: String, senha: String) {
        viewModelScope.launch {
            state = state.copy(carregando = true, erro = null, mensagem = null)
            runCatching { repository.signIn(email.trim().lowercase(), senha) }
                .onSuccess {
                    state = state.copy(sessaoAdministrativa = false)
                    atualizarAoVivoInterno()
                }
                .onFailure {
                    state = state.copy(carregando = false, erro = SupervisorRepository.message(it))
                }
        }
    }

    fun atualizarAoVivo() {
        if (atualizacaoAoVivoEmAndamento || atualizacaoPausasEmAndamento) return
        viewModelScope.launch { atualizarAoVivoInterno() }
    }

    private fun recoverFromAuthFailure(error: Throwable) {
        repository.clearActiveSession()
        state = SupervisorUiState(
            destination = SupervisorDestination.LOGIN,
            erro = SupervisorRepository.sessionRecoveryMessage(error),
            conexaoAoVivoOk = false,
        )
    }

    fun atualizarPausasAoVivoSilencioso() {
        if (
            state.destination != SupervisorDestination.AO_VIVO ||
            atualizacaoAoVivoEmAndamento ||
            atualizacaoPausasEmAndamento
        ) return

        viewModelScope.launch {
            atualizacaoPausasEmAndamento = true
            try {
                runCatching { repository.pausasAtivas() }
                    .onSuccess { pausas ->
                        state = state.copy(
                            pausasAtivas = pausas,
                            ultimaAtualizacaoAoVivoEmMillis = System.currentTimeMillis(),
                            conexaoAoVivoOk = true,
                        )
                    }
                    .onFailure { error ->
                        if (SupervisorRepository.isAuthFailure(error)) {
                            recoverFromAuthFailure(error)
                        } else {
                            state = state.copy(conexaoAoVivoOk = false)
                        }
                    }
            } finally {
                atualizacaoPausasEmAndamento = false
            }
        }
    }

    fun atualizarUltimoRetornoSilencioso() {
        if (
            state.destination != SupervisorDestination.AO_VIVO ||
            atualizacaoAoVivoEmAndamento ||
            atualizacaoRetornoEmAndamento
        ) return

        viewModelScope.launch {
            atualizacaoRetornoEmAndamento = true
            try {
                runCatching { repository.historico(LocalDate.now().toString()) }
                    .onSuccess { historico ->
                        state = state.copy(ultimoRetorno = historico.ultimoRetorno())
                    }
                    .onFailure { error ->
                        if (SupervisorRepository.isAuthFailure(error)) {
                            recoverFromAuthFailure(error)
                        }
                    }
            } finally {
                atualizacaoRetornoEmAndamento = false
            }
        }
    }

    /**
     * Tab-independent live-alert monitor: system notifications for departures,
     * returns and pause-limit crossings must keep working while the
     * supervisor is on any tab (Pessoas/Relatórios), not only the "Ao Vivo"
     * screen. Deliberately separate from [atualizarPausasAoVivoSilencioso] —
     * that method (and its AO_VIVO gate) is untouched and keeps driving the
     * Live tab's own on-screen display; this owns notification delivery only,
     * as the single source of truth for "was this event notified". Intended
     * to be started/stopped from SupervisorAreaShell's composition lifetime
     * (any tab open -> running; back to Ponto -> stopped) — it does not, by
     * itself, survive the app being backgrounded or the process dying.
     */
    fun startLiveAlertMonitoring() {
        if (liveAlertMonitoringJob?.isActive == true) return
        liveAlertBaseline = null
        liveAlertOverdueBaseline = emptySet()
        liveAlertWarningBaseline = emptySet()
        liveAlertCriticalBaseline = emptySet()
        liveAlertMonitoringJob = viewModelScope.launch {
            while (true) {
                runCatching { pollLiveAlertsOnce() }
                delay(LIVE_ALERT_MONITOR_INTERVAL_MILLIS)
            }
        }
    }

    fun stopLiveAlertMonitoring() {
        liveAlertMonitoringJob?.cancel()
        liveAlertMonitoringJob = null
    }

    private suspend fun pollLiveAlertsOnce() {
        if (!repository.hasSession()) return
        val pausas = withContext(Dispatchers.IO) { repository.pausasAtivas() }
        val agora = System.currentTimeMillis()
        val atual = pausas.associateBy { it.id }
        val excessosAtuais = atual.values
            .filter { tempoAtualSupervisor(it, agora) > it.limiteEfetivoSegundos }
            .mapTo(mutableSetOf()) { it.id }
        val criticosAtuais = atual.values
            .filter {
                val remaining = it.limiteEfetivoSegundos - tempoAtualSupervisor(it, agora)
                remaining in 0..SUPERVISOR_LIVE_ALERT_CRITICAL_THRESHOLD_SECONDS
            }
            .mapTo(mutableSetOf()) { it.id }
        val avisosAtuais = atual.values
            .filter {
                val remaining = it.limiteEfetivoSegundos - tempoAtualSupervisor(it, agora)
                remaining in (SUPERVISOR_LIVE_ALERT_CRITICAL_THRESHOLD_SECONDS + 1)..SUPERVISOR_LIVE_ALERT_WARNING_THRESHOLD_SECONDS
            }
            .mapTo(mutableSetOf()) { it.id }
        val anterior = liveAlertBaseline

        if (anterior == null) {
            liveAlertBaseline = atual
            liveAlertOverdueBaseline = excessosAtuais
            liveAlertWarningBaseline = avisosAtuais
            liveAlertCriticalBaseline = criticosAtuais
            return
        }

        val novas = atual.filterKeys { it !in anterior }.values.toList()
        val retornos = anterior.filterKeys { it !in atual }.values.toList()
        val novosExcessos = excessosAtuais.filter { it !in liveAlertOverdueBaseline }.mapNotNull(atual::get)
        val novosCriticos = criticosAtuais.filter { it !in liveAlertCriticalBaseline }.mapNotNull(atual::get)
        val novosAvisos = avisosAtuais.filter { it !in liveAlertWarningBaseline }.mapNotNull(atual::get)

        liveAlertBaseline = atual
        liveAlertOverdueBaseline = excessosAtuais
        liveAlertWarningBaseline = avisosAtuais
        liveAlertCriticalBaseline = criticosAtuais

        val alert = selectSupervisorLiveAlert(
            novas = novas,
            retornos = retornos,
            novosExcessos = novosExcessos,
            novosCriticos = novosCriticos,
            novosAvisos = novosAvisos,
            alertId = System.nanoTime(),
        ) ?: return

        // O estágio crítico de 15 s é deliberadamente visual e permanece no
        // centro de alertas da tela Ao Vivo; a notificação do Android já
        // ocorre em ~60 s (aviso) e volta a ocorrer somente se o limite for
        // efetivamente excedido, evitando ruído excessivo.
        if (alert.type == SupervisorLiveAlertType.CRITICO.name) return

        val wasDuplicate = liveAlertHistoryStore.record(
            id = alert.id,
            type = alert.type,
            title = alert.title,
            message = alert.message,
        )
        if (wasDuplicate) return

        SupervisorAlertNotifier.notify(
            context = applicationContext,
            eventType = alert.type,
            title = alert.title,
            message = alert.message,
            uniqueKey = alert.id,
        )
    }

    private suspend fun atualizarAoVivoInterno() {
        if (atualizacaoAoVivoEmAndamento || atualizacaoPausasEmAndamento) return
        atualizacaoAoVivoEmAndamento = true
        try {
            runCatching {
                val pausas = repository.pausasAtivas()
                val colaboradores = repository.collaborators()
                val historicoHoje = repository.historico(LocalDate.now().toString())
                Triple(pausas, colaboradores, historicoHoje.ultimoRetorno())
            }
                .onSuccess { (pausas, colaboradores, ultimoRetorno) ->
                    state = state.copy(
                        destination = SupervisorDestination.AO_VIVO,
                        carregando = false,
                        pausasAtivas = pausas,
                        ultimoRetorno = ultimoRetorno,
                        colaboradores = colaboradores,
                        sessaoAdministrativa = repository.usingAdminSession(),
                        ultimaAtualizacaoAoVivoEmMillis = System.currentTimeMillis(),
                        conexaoAoVivoOk = true,
                        codigoEmitido = null,
                        erro = null,
                    )
                }
                .onFailure { error ->
                    if (SupervisorRepository.isAuthFailure(error)) {
                        recoverFromAuthFailure(error)
                    } else {
                        state = state.copy(
                            destination = SupervisorDestination.AO_VIVO,
                            carregando = false,
                            conexaoAoVivoOk = false,
                            erro = "Sem conexão com o servidor. Sua sessão foi preservada e os últimos dados continuam disponíveis.",
                        )
                    }
                }
        } finally {
            atualizacaoAoVivoEmAndamento = false
        }
    }

    fun abrirHistorico(data: String = LocalDate.now().toString()) {
        viewModelScope.launch {
            state = state.copy(
                destination = SupervisorDestination.HISTORICO,
                carregando = true,
                historicoData = data,
                erro = null,
                mensagem = null,
            )
            runCatching { repository.historico(data) }
                .onSuccess { historico ->
                    state = state.copy(
                        destination = SupervisorDestination.HISTORICO,
                        carregando = false,
                        historico = historico,
                        historicoData = data,
                    )
                }
                .onFailure {
                    state = state.copy(carregando = false, erro = SupervisorRepository.message(it))
                }
        }
    }

    /**
     * Navega primeiro, busca depois.
     *
     * As abas trocavam de `destination` dentro do `onSuccess`: cada toque era
     * uma ida à rede inteira olhando para a tela anterior, e o que já estava em
     * memória reaparecia "do zero" a cada visita. Agora a tela troca na hora, o
     * que já foi carregado continua visível, e o indicador só aparece quando
     * não há nada para mostrar.
     */
    fun abrirCodigos() {
        val temDados = state.colaboradores.isNotEmpty()
        state = state.copy(
            destination = SupervisorDestination.CODIGOS,
            carregando = !temDados,
            erro = null,
            mensagem = null,
            codigoEmitido = null,
        )
        viewModelScope.launch {
            runCatching { repository.collaborators() to repository.accessCodes() }
                .onSuccess { (colaboradores, codigos) ->
                    state = state.copy(
                        carregando = false,
                        colaboradores = colaboradores,
                        codigosAtivos = codigos.codigos,
                    )
                }
                .onFailure {
                    state = state.copy(carregando = false, erro = SupervisorRepository.message(it))
                }
        }
    }

    fun atualizarCodigos() {
        viewModelScope.launch {
            runCatching { repository.accessCodes() }
                .onSuccess { state = state.copy(codigosAtivos = it.codigos) }
        }
    }

    /**
     * Emite o passe de café.
     *
     * O motivo é opcional: no fluxo normal a pessoa simplesmente vai tomar café,
     * e exigir uma justificativa a cada pausa transformaria a ação mais comum do
     * Supervisor num formulário. Quando há algo a registrar, o campo continua lá.
     */
    fun emitirCodigo(colaborador: Colaborador, motivo: String?) {
        if (state.carregando) return
        viewModelScope.launch {
            state = state.copy(
                carregando = true,
                colaboradorOcupadoId = colaborador.id,
                erro = null,
                mensagem = null,
                codigoEmitido = null,
            )
            runCatching { repository.createAccessCode(colaborador.id, motivo) }
                .onSuccess { codigo ->
                    state = state.copy(
                        carregando = false,
                        colaboradorOcupadoId = null,
                        codigoEmitido = codigo,
                        mensagem = null,
                        erro = null,
                    )
                    atualizarCodigos()
                }
                .onFailure {
                    state = state.copy(
                        carregando = false,
                        colaboradorOcupadoId = null,
                        erro = SupervisorRepository.message(it),
                    )
                }
        }
    }

    fun cancelarCodigo(colaborador: Colaborador) {
        if (state.carregando) return
        viewModelScope.launch {
            state = state.copy(
                carregando = true,
                colaboradorOcupadoId = colaborador.id,
                erro = null,
                mensagem = null,
            )
            runCatching { repository.cancelAccessCode(colaborador.id) }
                .onSuccess {
                    state = state.copy(
                        carregando = false,
                        colaboradorOcupadoId = null,
                        codigoEmitido = if (state.codigoEmitido?.colaboradorId == colaborador.id) {
                            null
                        } else {
                            state.codigoEmitido
                        },
                        mensagem = "Código de ${colaborador.nome} cancelado.",
                        erro = null,
                    )
                    atualizarCodigos()
                }
                .onFailure {
                    state = state.copy(
                        carregando = false,
                        colaboradorOcupadoId = null,
                        erro = SupervisorRepository.message(it),
                    )
                }
        }
    }

    /**
     * Registra manualmente a saída de [colaborador] -- uso excepcional para
     * quando a pessoa esqueceu de registrar no quiosque ou o código falhou
     * do horário continua exigindo autorizarPausa). A sessão do Supervisor
     * substitui o verificacaoToken biométrico; por isso o motivo é
     * obrigatório e o servidor audita quem fez o registro. Endpoint ainda
     * não existe no backend.
     */
    fun registrarPausaManual(colaborador: Colaborador, motivo: String) {
        if (motivo.trim().length < 2) {
            state = state.copy(erro = "Informe o motivo do registro manual.")
            return
        }
        viewModelScope.launch {
            state = state.copy(carregando = true, erro = null, mensagem = null)
            runCatching { repository.iniciarPausaManual(colaborador.id, motivo) }
                .onSuccess { resposta ->
                    state = state.copy(
                        carregando = false,
                        mensagem = "Saída registrada manualmente para ${colaborador.nome}.",
                        manualPunchResult = ManualPunchResult(colaborador.nome, ManualPunchType.INICIO, resposta.inicioLocal),
                    )
                    atualizarPausasAoVivoSilencioso()
                }
                .onFailure {
                    state = state.copy(carregando = false, erro = SupervisorRepository.message(it))
                }
        }
    }

    /**
     * Fecha manualmente a pausa aberta do colaborador de [pausa] -- resolve tanto
     * "a pessoa esqueceu de marcar o retorno" quanto "o reconhecimento falhou ao
     * voltar".
     *
     * Envia `pausa.colaboradorId`, não `pausa.id`: o Worker procura a pausa aberta
     * do colaborador, e mandar o id da pausa fazia a validação recusar o corpo.
     * O horário de retorno não é configurável -- o servidor sempre grava `now()`.
     */
    fun finalizarPausaManual(pausa: PausaSupervisor, motivo: String) {
        if (motivo.trim().length < 2) {
            state = state.copy(erro = "Informe o motivo do registro manual.")
            return
        }
        viewModelScope.launch {
            state = state.copy(carregando = true, erro = null, mensagem = null)
            runCatching { repository.finalizarPausaManual(pausa.colaboradorId, motivo) }
                .onSuccess { resposta ->
                    state = state.copy(
                        carregando = false,
                        mensagem = "Retorno registrado manualmente para ${pausa.nome}.",
                        manualPunchResult = ManualPunchResult(pausa.nome, ManualPunchType.FIM, resposta.fimLocal),
                    )
                    atualizarPausasAoVivoSilencioso()
                }
                .onFailure {
                    state = state.copy(carregando = false, erro = SupervisorRepository.message(it))
                }
        }
    }

    fun limparRegistroManual() {
        state = state.copy(manualPunchResult = null)
    }

    fun limparCodigoEmitido() {
        state = state.copy(
            codigoEmitido = null,
            mensagem = null,
            erro = null,
        )
    }

    fun abrirRelatorios(dias: Int = 7) {
        val fim = LocalDate.now()
        val inicio = fim.minusDays((dias.coerceAtLeast(1) - 1).toLong())
        carregarRelatorio(inicio.toString(), fim.toString())
    }

    fun carregarRelatorio(inicio: String, fim: String) {
        viewModelScope.launch {
            state = state.copy(
                destination = SupervisorDestination.RELATORIOS,
                carregando = true,
                relatorioInicio = inicio,
                relatorioFim = fim,
                relatorioAnterior = null,
                erro = null,
                mensagem = null,
            )

            val inicioDate = runCatching { LocalDate.parse(inicio) }.getOrNull()
            val fimDate = runCatching { LocalDate.parse(fim) }.getOrNull()

            runCatching {
                val atual = repository.report(inicio, fim)
                val anterior = if (inicioDate != null && fimDate != null && !fimDate.isBefore(inicioDate)) {
                    val dias = (ChronoUnit.DAYS.between(inicioDate, fimDate) + 1L).coerceAtLeast(1L)
                    val fimAnterior = inicioDate.minusDays(1)
                    val inicioAnterior = fimAnterior.minusDays(dias - 1L)
                    runCatching { repository.report(inicioAnterior.toString(), fimAnterior.toString()) }.getOrNull()
                } else {
                    null
                }
                atual to anterior
            }
                .onSuccess { (atual, anterior) ->
                    state = state.copy(
                        destination = SupervisorDestination.RELATORIOS,
                        carregando = false,
                        relatorio = atual,
                        relatorioAnterior = anterior,
                    )
                }
                .onFailure {
                    state = state.copy(carregando = false, erro = SupervisorRepository.message(it))
                }
        }
    }

    suspend fun baixarRelatorioCsv(): ByteArray {
        val inicio = state.relatorioInicio ?: LocalDate.now().toString()
        val fim = state.relatorioFim ?: inicio
        return repository.reportCsv(inicio, fim)
    }

    fun abrirColaboradores() {
        val temDados = state.colaboradores.isNotEmpty()
        state = state.copy(
            destination = SupervisorDestination.COLABORADORES,
            carregando = !temDados,
            colaboradorSelecionado = null,
            erro = null,
            mensagem = null,
        )
        viewModelScope.launch {
            runCatching { repository.collaborators() }
                .onSuccess { state = state.copy(carregando = false, colaboradores = it) }
                .onFailure {
                    state = state.copy(carregando = false, erro = SupervisorRepository.message(it))
                }
        }
    }

    fun abrirNovoColaborador() {
        state = state.copy(
            destination = SupervisorDestination.NOVO_COLABORADOR,
            erro = null,
            mensagem = null,
        )
    }

    fun criarColaborador(nome: String, setor: String, turno: String) {
        if (nome.trim().length < 2) {
            state = state.copy(erro = "Informe o nome do colaborador.")
            return
        }

        viewModelScope.launch {
            state = state.copy(carregando = true, erro = null, mensagem = null)
            runCatching { repository.createCollaborator(nome, setor, turno) }
                .onSuccess { colaborador ->
                    state = state.copy(
                        carregando = false,
                        destination = SupervisorDestination.COLABORADORES,
                        colaboradorSelecionado = colaborador,
                        colaboradores = upsertCollaborator(state.colaboradores, colaborador),
                        mensagem = "${colaborador.nome} cadastrado. Gere um código quando ele for tomar café.",
                    )
                }
                .onFailure {
                    state = state.copy(carregando = false, erro = SupervisorRepository.message(it))
                }
        }
    }

    /**
     * The candidate embedding looked like a likely duplicate of another
     * collaborator's already-enrolled face (best-effort, local-catalog-only
     * check). A supervisor explicitly confirmed this is a false positive
     * (e.g. identical twins) and the enrollment should proceed anyway, using
     * the same samples already captured — no need to re-scan.
     */
    /** Discards the pending duplicate-flagged enrollment; the sequence must be redone. */
    fun excluirColaborador(colaborador: Colaborador) {
        viewModelScope.launch {
            state = state.copy(carregando = true, erro = null, mensagem = null)
            runCatching { repository.deleteCollaborator(colaborador.id) }
                .onSuccess {
                    state = state.copy(
                        carregando = false,
                        colaboradores = state.colaboradores.filterNot { item -> item.id == colaborador.id },
                        mensagem = "${colaborador.nome} foi removido dos colaboradores ativos e seus códigos pendentes foram cancelados.",
                    )
                    runCatching { repository.collaborators() }
                        .onSuccess { refreshed ->
                            state = state.copy(
                                colaboradores = refreshed.filterNot { item -> item.id == colaborador.id },
                            )
                        }
                }
                .onFailure {
                    state = state.copy(carregando = false, erro = SupervisorRepository.message(it))
                }
        }
    }

    fun voltarColaboradores() {
        state = state.copy(
            destination = SupervisorDestination.COLABORADORES,
            colaboradorSelecionado = null,
            erro = null,
        )
    }

    fun voltarAoVivo() {
        state = state.copy(
            destination = SupervisorDestination.AO_VIVO,
            codigoEmitido = null,
            erro = null,
            mensagem = null,
        )
        atualizarAoVivo()
    }

    fun sair() {
        viewModelScope.launch {
            repository.signOutSupervisor()
            state = SupervisorUiState(destination = SupervisorDestination.LOGIN)
        }
    }

    fun limparAviso() {
        state = state.copy(erro = null, mensagem = null)
    }

    fun formatarTempo(segundos: Int): String = "%02d:%02d".format(segundos / 60, segundos % 60)

    private fun List<PausaSupervisor>.ultimoRetorno(): PausaSupervisor? =
        asSequence()
            .filter { !it.fimLocal.isNullOrBlank() }
            .maxWithOrNull(
                compareBy<PausaSupervisor> { it.data.orEmpty() }
                    .thenBy { it.fimLocal.orEmpty() }
                    .thenBy { it.inicioLocal },
            )

    private fun upsertCollaborator(
        collaborators: List<Colaborador>,
        updated: Colaborador,
    ): List<Colaborador> {
        val found = collaborators.any { it.id == updated.id }
        return (if (found) {
            collaborators.map { if (it.id == updated.id) updated else it }
        } else {
            collaborators + updated
        }).sortedBy { it.nome.lowercase() }
    }

    override fun onCleared() {
        stopLiveAlertMonitoring()
        super.onCleared()
    }

    companion object {
        private const val LIVE_ALERT_MONITOR_INTERVAL_MILLIS = 15_000L
    }
}

class SupervisorViewModelFactory(private val creator: () -> SupervisorViewModel) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(SupervisorViewModel::class.java))
        return creator() as T
    }
}
