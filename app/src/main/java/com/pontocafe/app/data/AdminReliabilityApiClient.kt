package com.pontocafe.app.data

import com.pontocafe.app.BuildConfig
import okhttp3.Interceptor
import org.json.JSONObject
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query


data class CoffeeRuleV2(
    val periodo: String,
    val inicio: String,
    val fim: String,
    val limiteSegundos: Int,
    val limiteMinutos: Int? = null,
    val ativo: Boolean,
    val padraoAtual: Boolean = false,
)
data class CoffeeRulesV2Response(val regras: List<CoffeeRuleV2>)
data class UpdateCoffeeRuleV2Request(
    val inicio: String,
    val fim: String,
    val limiteSegundos: Int,
    val ativo: Boolean,
)
data class UpdateCoffeeRuleV2Response(val regra: CoffeeRuleV2)

data class CollaboratorImportItem(
    val nome: String,
    val setor: String?,
    val turno: String?,
)
data class CollaboratorImportRequest(val colaboradores: List<CollaboratorImportItem>)
data class ImportedCollaborator(
    val id: String,
    val nome: String,
    val setor: String?,
    val turno: String?,
)
data class IgnoredCollaborator(val nome: String, val motivo: String)
data class CollaboratorImportResponse(
    val recebidos: Int,
    val criados: Int,
    val existentes: Int,
    val colaboradoresCriados: List<ImportedCollaborator>,
    val ignorados: List<IgnoredCollaborator>,
)

data class BulkCollaboratorRequest(
    val ids: List<String>,
    val setor: String? = null,
    val turno: String? = null,
    val ativo: Boolean? = null,
)
data class BulkCollaboratorResponse(
    val ok: Boolean,
    val atualizados: Int,
)

data class CollaboratorDeleteResponse(
    val ok: Boolean,
    val colaboradorId: String,
    val nome: String,
    val excluido: Boolean,
    val codigosCancelados: Int = 0,
    val historicoPreservado: Boolean = true,
)

data class CollaboratorHistoryPerson(
    val id: String,
    val nome: String,
    val setor: String?,
    val turno: String?,
    val ativo: Boolean,
    val criadoEm: String,
    val atualizadoEm: String,
)
data class CollaboratorHistorySummary(
    val totalPausas: Int,
    val mediaSegundos: Int?,
    val acimaLimite: Int,
    val foraHorario: Int,
)
data class CollaboratorHistoryPause(
    val id: String,
    val periodo: String,
    val inicioEm: String,
    val fimEm: String?,
    val inicioLocal: String,
    val fimLocal: String?,
    val duracaoSegundos: Int?,
    val tempoContadoSegundos: Int? = null,
    val limiteSegundos: Int,
    val carenciaSegundos: Int = 0,
    val foraHorario: Boolean,
    val excedeuLimite: Boolean,
)
data class CollaboratorAccessCodeEvent(
    val id: String,
    val criadoEm: String,
    val expiraEm: String?,
    val saidaEm: String?,
    val retornoEm: String?,
    val canceladoEm: String?,
    val motivo: String?,
    val emitidoPor: String?,
    val emitidoPorTipo: String?,
)
data class CollaboratorAccessCodeHistory(
    val retencaoDias: Int = 0,
    val eventos: List<CollaboratorAccessCodeEvent> = emptyList(),
)
data class CollaboratorHistoryResponse(
    val colaborador: CollaboratorHistoryPerson,
    val periodoDias: Int,
    val resumo: CollaboratorHistorySummary,
    val pausas: List<CollaboratorHistoryPause>,
    val codigosAcesso: CollaboratorAccessCodeHistory? = null,
)


data class DiagnosticDatabase(
    val status: String,
    val latenciaMs: Int,
    val servidor: String?,
)
data class DiagnosticOperation(
    val colaboradoresAtivos: Int,
    val dispositivosAtivos: Int,
    val pausasAbertas: Int,
    val sessoesAtivas: Int,
)
data class DiagnosticIntegrity(
    val pausasUltimas24h: Int = 0,
    val operacoesProtegidasUltimas24h: Int = 0,
    val registrosPorCodigoUltimas24h: Int = 0,
    val iniciosUltimas24h: Int = 0,
    val retornosUltimas24h: Int = 0,
)
data class DiagnosticFleetDevice(
    val id: String,
    val nome: String,
    val ativo: Boolean,
    val ultimoAcessoEm: String?,
    val telemetriaEm: String?,
    val appVersion: String?,
    val deviceModel: String?,
    val androidVersion: String?,
    val crashCount: Int = 0,
    val stallCount: Int = 0,
    val alertaSaude: Boolean = false,
    val desatualizado: Boolean = false,
)
data class DiagnosticFleet(
    val totalAtivos: Int = 0,
    val comTelemetriaRecente: Int = 0,
    val semTelemetriaRecente: Int = 0,
    val desatualizados: Int = 0,
    val alertasSaude: Int = 0,
    val dispositivos: List<DiagnosticFleetDevice>? = emptyList(),
)
data class DiagnosticAccessCodes(
    val pendentes: Int = 0,
    val emUso: Int = 0,
    val tentativasInvalidasUltimas24h: Int = 0,
)
data class DiagnosticConfiguration(
    val timezone: String,
    val sessaoHoras: Int,
    val codigoValidadeSegundos: Int = 0,
    val codigoMaxTentativas: Int = 0,
    val carenciaSegundos: Int = 0,
    val offlineMaxHoras: Int,
    val retencaoCodigosDias: Int = 0,
    val androidMaisRecente: String,
    val androidMinimo: String,
)
data class DiagnosticResponse(
    val status: String,
    val requestId: String,
    val banco: DiagnosticDatabase,
    val operacao: DiagnosticOperation,
    val integridade: DiagnosticIntegrity? = null,
    val frota: DiagnosticFleet? = null,
    val codigosAcesso: DiagnosticAccessCodes? = null,
    val configuracao: DiagnosticConfiguration,
)

interface AdminReliabilityApi {
    @GET("admin/regras-cafe") suspend fun coffeeRules(): CoffeeRulesV2Response
    @PUT("admin/regras-cafe/{periodo}")
    suspend fun updateCoffeeRule(
        @Path("periodo") period: String,
        @Body body: UpdateCoffeeRuleV2Request,
    ): UpdateCoffeeRuleV2Response

    @GET("gestao/colaboradores/{id}/historico")
    suspend fun collaboratorHistory(
        @Path("id") collaboratorId: String,
        @Query("dias") days: Int = 30,
    ): CollaboratorHistoryResponse

    @POST("gestao/colaboradores/importar")
    suspend fun importCollaborators(@Body body: CollaboratorImportRequest): CollaboratorImportResponse

    @PUT("gestao/colaboradores/lote")
    suspend fun updateCollaborators(@Body body: BulkCollaboratorRequest): BulkCollaboratorResponse

    @POST("gestao/colaboradores/{id}/excluir")
    suspend fun deleteCollaborator(@Path("id") collaboratorId: String): CollaboratorDeleteResponse



    @GET("admin/diagnostico") suspend fun diagnostic(): DiagnosticResponse
}

class AdminReliabilityRepository(
    private val api: AdminReliabilityApi,
) {
    suspend fun coffeeRules() = api.coffeeRules().regras
    suspend fun updateCoffeeRule(period: String, start: String, end: String, limitSeconds: Int, active: Boolean) =
        api.updateCoffeeRule(period, UpdateCoffeeRuleV2Request(start, end, limitSeconds, active)).regra

    suspend fun collaboratorHistory(id: String, days: Int = 30) = api.collaboratorHistory(id, days)
    suspend fun importCollaborators(items: List<CollaboratorImportItem>) =
        api.importCollaborators(CollaboratorImportRequest(items))
    suspend fun updateCollaborators(ids: List<String>, sector: String?, shift: String?, active: Boolean?) =
        api.updateCollaborators(BulkCollaboratorRequest(ids, sector, shift, active))
    suspend fun deleteCollaborator(collaboratorId: String) = api.deleteCollaborator(collaboratorId)

    suspend fun diagnostic() = api.diagnostic()

    companion object {
        fun message(error: Throwable): String {
            if (error is HttpException) {
                // 404/405/501 aqui significa que a rota nao existe no Worker, nao
                // que a pessoa fez algo errado. Repassar o "Rota nao encontrada"
                // cru do backend, com requestId, so confunde quem opera o turno.
                if (error.code() == 404 || error.code() == 405 || error.code() == 501) {
                    return "Esta função ainda não está disponível no servidor. " +
                        "Avise a equipe responsável pelo sistema."
                }
                val body = runCatching { error.response()?.errorBody()?.string() }.getOrNull()
                val json = runCatching { body?.let(::JSONObject) }.getOrNull()
                val message = json?.optString("erro")?.takeIf { it.isNotBlank() }
                val code = json?.optString("codigo")?.takeIf { it.isNotBlank() }
                val requestId = json?.optString("requestId")?.takeIf { it.isNotBlank() }
                return buildString {
                    append(message ?: "Falha na comunicação com o servidor (${error.code()}).")
                    if (code != null || requestId != null) {
                        append("\n")
                        append(listOfNotNull(code?.let { "Código $it" }, requestId?.let { "ID $it" }).joinToString(" · "))
                    }
                }
            }
            return error.message ?: "Não foi possível concluir a operação."
        }
    }
}

object AdminReliabilityApiClient {
    fun create(sessionStore: SecureAdminSessionStore): AdminReliabilityRepository {
        val authInterceptor = Interceptor { chain ->
            val request = chain.request().newBuilder().apply {
                sessionStore.read()?.takeIf { it.isNotBlank() }?.let { header("Authorization", "Bearer $it") }
                header("X-App-Version", BuildConfig.VERSION_NAME)
            }.build()
            chain.proceed(request)
        }
        val client = PontoHttpClients.baseBuilder().addInterceptor(authInterceptor).build()
        val retrofit = Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        return AdminReliabilityRepository(retrofit.create(AdminReliabilityApi::class.java))
    }
}
