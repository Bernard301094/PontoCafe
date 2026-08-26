package com.pontocafe.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.google.gson.Gson
import com.pontocafe.app.BuildConfig
import java.security.KeyStore
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec


data class OfflinePontoEvent(
    val eventId: String,
    val acao: String,
    val colaboradorId: String,
    val nome: String,
    val ocorridoEm: String,
    val score: Double,
    val embedding: List<Float>,
    val appVersion: String,
    val modelo: String,
    val versaoModelo: String,
)

data class LocalOpenPause(
    val colaboradorId: String,
    val nome: String,
    val periodo: String,
    val inicioEmMillis: Long,
    val inicioLocal: String,
    val limiteSegundos: Int,
    val retornoAteLocal: String,
)

data class LocalCompletedPause(
    val colaboradorId: String,
    val nome: String,
    val periodo: String,
    val dataLocal: String,
    val inicioLocal: String,
    val fimLocal: String,
    val duracaoSegundos: Int,
    val limiteSegundos: Int,
)

data class OfflineSyncFailure(
    val eventId: String,
    val nome: String,
    val acao: String,
    val mensagem: String,
    val tentativas: Int,
    val ultimaTentativaEmMillis: Long,
)

data class SyncCenterEvent(
    val eventId: String,
    val nome: String,
    val acao: String,
    val ocorridoEm: String,
    val appVersion: String,
    val falha: OfflineSyncFailure?,
)

data class SyncCenterSnapshot(
    val pending: List<SyncCenterEvent>,
    val failures: List<OfflineSyncFailure>,
    val lastServerOkMillis: Long,
    val rulesUpdatedAtMillis: Long,
    val quarantined: Boolean = false,
    val quarantineReason: String? = null,
    val quarantinedAtMillis: Long = 0L,
)

data class PontoOfflineSnapshot(
    val eventos: List<OfflinePontoEvent> = emptyList(),
    val pausasAbertas: List<LocalOpenPause> = emptyList(),
    val regras: List<RegraCafe> = emptyList(),
    val regrasAtualizadasEmMillis: Long = 0L,
    val ultimoServidorOkEmMillis: Long = 0L,
    val falhasSincronizacao: List<OfflineSyncFailure> = emptyList(),
    val pausasConcluidas: List<LocalCompletedPause>? = emptyList(),
    val eventosEmQuarentena: Boolean = false,
    val motivoQuarentena: String? = null,
    val quarentenaEmMillis: Long = 0L,
)

/**
 * Fila de eventos pendentes de sincronização -- inclui o embedding facial
 * bruto de cada batida, então pode crescer para centenas de KB em uso
 * pesado (até MAX_PENDING_EVENTS batidas).
 */
private data class OfflineEventsPayload(
    val eventos: List<OfflinePontoEvent> = emptyList(),
)

/**
 * Tudo que não é a fila de eventos: regras, pausas locais, estado de
 * sincronização/quarentena. Pequeno e muda com muito mais frequência que os
 * eventos (ex.: markServerOk() a cada heartbeat), por isso vive numa chave
 * separada -- ver comentário em saveMeta().
 */
private data class OfflineMetaPayload(
    val pausasAbertas: List<LocalOpenPause> = emptyList(),
    val regras: List<RegraCafe> = emptyList(),
    val regrasAtualizadasEmMillis: Long = 0L,
    val ultimoServidorOkEmMillis: Long = 0L,
    val falhasSincronizacao: List<OfflineSyncFailure> = emptyList(),
    val pausasConcluidas: List<LocalCompletedPause>? = emptyList(),
    val eventosEmQuarentena: Boolean = false,
    val motivoQuarentena: String? = null,
    val quarentenaEmMillis: Long = 0L,
)

class SecurePontoOfflineStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("pontocafe_offline_secure", Context.MODE_PRIVATE)
    private val keyAlias = "pontocafe_offline_key"
    private val eventsKey = "offline_events"
    private val metaKey = "offline_meta"
    private val legacyPayloadKey = "offline_snapshot"
    private val gson = Gson()
    private val timezone = ZoneId.of("America/Fortaleza")
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val operationJournal = PontoOperationJournal(appContext)

    @Volatile
    private var cachedEvents: OfflineEventsPayload? = null

    @Volatile
    private var cachedMeta: OfflineMetaPayload? = null

    @Synchronized
    fun snapshot(): PontoOfflineSnapshot = combinedSnapshot()

    @Synchronized
    fun pendingEvents(): List<OfflinePontoEvent> = readEvents().eventos

    @Synchronized
    fun pendingCount(): Int = readEvents().eventos.size

    @Synchronized
    fun lastServerOkMillis(): Long = readMeta().ultimoServidorOkEmMillis

    @Synchronized
    fun hasQuarantinedPendingEvents(): Boolean =
        readMeta().eventosEmQuarentena && readEvents().eventos.isNotEmpty()

    /**
     * Remote credential revocation must never destroy unsynchronized point
     * events. Legacy events do not carry an originating device id, so they are
     * preserved but blocked from automatic replay under a future credential.
     */
    @Synchronized
    fun quarantinePendingEvents(reason: String) {
        val meta = readMeta()
        if (readEvents().eventos.isEmpty()) {
            saveMeta(
                meta.copy(
                    ultimoServidorOkEmMillis = 0L,
                    eventosEmQuarentena = false,
                    motivoQuarentena = null,
                    quarentenaEmMillis = 0L,
                ),
                durable = true,
            )
            return
        }
        saveMeta(
            meta.copy(
                ultimoServidorOkEmMillis = 0L,
                eventosEmQuarentena = true,
                motivoQuarentena = reason.take(160),
                quarentenaEmMillis = System.currentTimeMillis(),
            ),
            durable = true,
        )
    }

    @Synchronized
    fun syncCenterSnapshot(): SyncCenterSnapshot {
        val events = readEvents()
        val meta = readMeta()
        val failureById = meta.falhasSincronizacao.associateBy { it.eventId }
        return SyncCenterSnapshot(
            pending = events.eventos.map { event ->
                SyncCenterEvent(
                    eventId = event.eventId,
                    nome = event.nome,
                    acao = event.acao,
                    ocorridoEm = event.ocorridoEm,
                    appVersion = event.appVersion,
                    falha = failureById[event.eventId],
                )
            },
            failures = meta.falhasSincronizacao.filter { failure -> events.eventos.any { it.eventId == failure.eventId } },
            lastServerOkMillis = meta.ultimoServidorOkEmMillis,
            rulesUpdatedAtMillis = meta.regrasAtualizadasEmMillis,
            quarantined = meta.eventosEmQuarentena && events.eventos.isNotEmpty(),
            quarantineReason = meta.motivoQuarentena,
            quarantinedAtMillis = meta.quarentenaEmMillis,
        )
    }

    @Synchronized
    fun canOperateOffline(maxOfflineMillis: Long): Boolean {
        val meta = readMeta()
        if (meta.eventosEmQuarentena && readEvents().eventos.isNotEmpty()) return false
        val lastOk = meta.ultimoServidorOkEmMillis
        return lastOk > 0L && System.currentTimeMillis() - lastOk <= maxOfflineMillis
    }

    @Synchronized
    fun markServerOk() {
        saveMeta(readMeta().copy(ultimoServidorOkEmMillis = System.currentTimeMillis()))
    }

    @Synchronized
    fun saveRules(rules: List<RegraCafe>) {
        saveMeta(
            readMeta().copy(
                regras = rules,
                regrasAtualizadasEmMillis = System.currentTimeMillis(),
                ultimoServidorOkEmMillis = System.currentTimeMillis(),
            ),
        )
    }

    @Synchronized
    fun currentRule(now: ZonedDateTime = ZonedDateTime.now(timezone)): RegraCafe? {
        val currentTime = now.toLocalTime()
        return readMeta().regras.firstOrNull { rule ->
            runCatching {
                val start = LocalTime.parse(rule.inicio)
                val end = LocalTime.parse(rule.fim)
                !currentTime.isBefore(start) && currentTime.isBefore(end)
            }.getOrDefault(false)
        }
    }

    @Synchronized
    fun localOpenPause(collaboratorId: String): LocalOpenPause? =
        readMeta().pausasAbertas.firstOrNull { it.colaboradorId == collaboratorId }

    @Synchronized
    fun completedPauseToday(collaboratorId: String, periodo: String): LocalCompletedPause? {
        val today = ZonedDateTime.now(timezone).format(dateFormatter)
        return readMeta().pausasConcluidas.orEmpty().firstOrNull {
            it.colaboradorId == collaboratorId && it.periodo == periodo && it.dataLocal == today
        }
    }

    @Synchronized
    fun recordOnlineStart(collaboratorId: String, nome: String, pause: IniciarPausaResponse) {
        val meta = readMeta()
        val startedMillis = runCatching { Instant.parse(pause.inicioEm).toEpochMilli() }
            .getOrDefault(System.currentTimeMillis())
        val localPause = LocalOpenPause(
            colaboradorId = collaboratorId,
            nome = nome,
            periodo = pause.periodo,
            inicioEmMillis = startedMillis,
            inicioLocal = pause.inicioLocal,
            limiteSegundos = pause.limiteSegundos,
            retornoAteLocal = pause.retornoAteLocal,
        )
        saveMeta(
            meta.copy(
                pausasAbertas = meta.pausasAbertas.filterNot { it.colaboradorId == collaboratorId } + localPause,
                ultimoServidorOkEmMillis = System.currentTimeMillis(),
            ),
            durable = true,
        )
        operationJournal.completeForCollaborator(collaboratorId)
    }

    @Synchronized
    fun recordOnlineFinish(collaboratorId: String) {
        val meta = readMeta()
        val open = meta.pausasAbertas.firstOrNull { it.colaboradorId == collaboratorId }
        val now = ZonedDateTime.now(timezone)
        val completed = open?.let {
            LocalCompletedPause(
                colaboradorId = it.colaboradorId,
                nome = it.nome,
                periodo = it.periodo,
                dataLocal = now.format(dateFormatter),
                inicioLocal = it.inicioLocal,
                fimLocal = now.format(timeFormatter),
                duracaoSegundos = ((now.toInstant().toEpochMilli() - it.inicioEmMillis) / 1000L)
                    .coerceAtLeast(0L)
                    .coerceAtMost(Int.MAX_VALUE.toLong())
                    .toInt(),
                limiteSegundos = it.limiteSegundos,
            )
        }
        val today = now.format(dateFormatter)
        val completedToday = meta.pausasConcluidas.orEmpty().filter { it.dataLocal == today }.toMutableList()
        if (completed != null) {
            completedToday.removeAll { it.colaboradorId == completed.colaboradorId && it.periodo == completed.periodo }
            completedToday += completed
        }
        saveMeta(
            meta.copy(
                pausasAbertas = meta.pausasAbertas.filterNot { it.colaboradorId == collaboratorId },
                pausasConcluidas = completedToday,
                ultimoServidorOkEmMillis = System.currentTimeMillis(),
            ),
            durable = true,
        )
        operationJournal.completeForCollaborator(collaboratorId)
    }

    @Synchronized
    fun queueOfflineStart(
        colaborador: Colaborador,
        score: Double,
        embedding: FloatArray,
        model: String,
        modelVersion: String,
        rule: RegraCafe,
    ): LocalOpenPause {
        val events = readEvents()
        val meta = readMeta()
        require(!meta.eventosEmQuarentena) {
            "Existem registros offline preservados de uma credencial anterior. Conecte este dispositivo ao servidor antes de registrar novos pontos offline."
        }
        require(events.eventos.size < MAX_PENDING_EVENTS) { "Há muitos registros offline aguardando sincronização." }
        require(meta.pausasAbertas.none { it.colaboradorId == colaborador.id }) { "Já existe uma pausa aberta neste dispositivo." }
        require(embedding.isNotEmpty() && embedding.all { it.isFinite() }) { "A biometria offline é inválida." }

        val operationId = operationJournal.prepare(colaborador.id, embedding)
        val now = ZonedDateTime.now(timezone)
        val today = now.format(dateFormatter)
        val completed = meta.pausasConcluidas.orEmpty().firstOrNull {
            it.colaboradorId == colaborador.id && it.periodo == rule.periodo && it.dataLocal == today
        }
        if (completed != null) {
            val repeatedAttempt = OfflinePontoEvent(
                eventId = operationId,
                acao = "INICIAR",
                colaboradorId = colaborador.id,
                nome = colaborador.nome,
                ocorridoEm = now.toInstant().toString(),
                score = score,
                embedding = embedding.toList(),
                appVersion = BuildConfig.VERSION_NAME,
                modelo = model,
                versaoModelo = modelVersion,
            )
            saveBoth(
                events.copy(eventos = events.eventos + repeatedAttempt),
                meta.copy(pausasConcluidas = meta.pausasConcluidas.orEmpty().filter { it.dataLocal == today }),
                durable = true,
            )
            operationJournal.complete(operationId)
            val minutos = completed.duracaoSegundos / 60
            val segundos = completed.duracaoSegundos % 60
            val duracao = if (segundos > 0) "${minutos} min ${segundos} s" else "${minutos} min"
            val periodoLabel = if (completed.periodo == "MANHA") "manhã" else "tarde"
            error(
                "Pausa da $periodoLabel já utilizada hoje. Saída: ${completed.inicioLocal} · Retorno: ${completed.fimLocal} · Duração: $duracao. Esta nova tentativa foi registrada e será enviada ao servidor quando a conexão voltar.",
            )
        }

        val event = OfflinePontoEvent(
            eventId = operationId,
            acao = "INICIAR",
            colaboradorId = colaborador.id,
            nome = colaborador.nome,
            ocorridoEm = now.toInstant().toString(),
            score = score,
            embedding = embedding.toList(),
            appVersion = BuildConfig.VERSION_NAME,
            modelo = model,
            versaoModelo = modelVersion,
        )
        val localPause = LocalOpenPause(
            colaboradorId = colaborador.id,
            nome = colaborador.nome,
            periodo = rule.periodo,
            inicioEmMillis = now.toInstant().toEpochMilli(),
            inicioLocal = now.format(timeFormatter),
            limiteSegundos = rule.limiteSegundos,
            retornoAteLocal = now.plusSeconds(rule.limiteSegundos.toLong()).format(timeFormatter),
        )
        saveBoth(
            events.copy(eventos = events.eventos + event),
            meta.copy(
                pausasAbertas = meta.pausasAbertas + localPause,
                pausasConcluidas = meta.pausasConcluidas.orEmpty().filter { it.dataLocal == today },
            ),
            durable = true,
        )
        operationJournal.complete(operationId)
        return localPause
    }

    @Synchronized
    fun queueOfflineFinish(
        colaborador: Colaborador,
        score: Double,
        embedding: FloatArray,
        model: String,
        modelVersion: String,
    ): Pair<LocalOpenPause, Int> {
        val events = readEvents()
        val meta = readMeta()
        require(!meta.eventosEmQuarentena) {
            "Existem registros offline preservados de uma credencial anterior. Conecte este dispositivo ao servidor antes de registrar novos pontos offline."
        }
        require(events.eventos.size < MAX_PENDING_EVENTS) { "Há muitos registros offline aguardando sincronização." }
        require(embedding.isNotEmpty() && embedding.all { it.isFinite() }) { "A biometria offline é inválida." }
        val open = meta.pausasAbertas.firstOrNull { it.colaboradorId == colaborador.id }
            ?: error("Não existe pausa local aberta para este colaborador.")
        val operationId = operationJournal.prepare(colaborador.id, embedding)
        val now = ZonedDateTime.now(timezone)
        val event = OfflinePontoEvent(
            eventId = operationId,
            acao = "FINALIZAR",
            colaboradorId = colaborador.id,
            nome = colaborador.nome,
            ocorridoEm = now.toInstant().toString(),
            score = score,
            embedding = embedding.toList(),
            appVersion = BuildConfig.VERSION_NAME,
            modelo = model,
            versaoModelo = modelVersion,
        )
        val duration = ((now.toInstant().toEpochMilli() - open.inicioEmMillis) / 1000L)
            .coerceAtLeast(0L)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
        val completed = LocalCompletedPause(
            colaboradorId = open.colaboradorId,
            nome = open.nome,
            periodo = open.periodo,
            dataLocal = now.format(dateFormatter),
            inicioLocal = open.inicioLocal,
            fimLocal = now.format(timeFormatter),
            duracaoSegundos = duration,
            limiteSegundos = open.limiteSegundos,
        )
        val today = now.format(dateFormatter)
        val completedToday = meta.pausasConcluidas.orEmpty().filter {
            it.dataLocal == today && !(it.colaboradorId == completed.colaboradorId && it.periodo == completed.periodo)
        } + completed
        saveBoth(
            events.copy(eventos = events.eventos + event),
            meta.copy(
                pausasAbertas = meta.pausasAbertas.filterNot { it.colaboradorId == colaborador.id },
                pausasConcluidas = completedToday,
            ),
            durable = true,
        )
        operationJournal.complete(operationId)
        return open to duration
    }

    @Synchronized
    fun recordSyncResults(results: List<OfflineSyncResult>) {
        if (results.isEmpty()) return
        val events = readEvents()
        val meta = readMeta()
        val processed = results.filter {
            it.status.equals("PROCESSADO", true) ||
                it.status.equals("OK", true) ||
                it.status.equals("SINCRONIZADO", true) ||
                it.status.equals("RECONCILIADO", true)
        }.map { it.eventId }.toSet()
        val pendingIds = events.eventos.map { it.eventId }.toSet() - processed
        val currentFailures = meta.falhasSincronizacao.associateBy { it.eventId }.toMutableMap()
        val now = System.currentTimeMillis()
        val remainingEvents = events.eventos.filterNot { it.eventId in processed }

        results.forEach { result ->
            if (result.eventId in processed) {
                currentFailures.remove(result.eventId)
            } else if (result.eventId in pendingIds) {
                val event = events.eventos.firstOrNull { it.eventId == result.eventId } ?: return@forEach
                val previous = currentFailures[result.eventId]
                currentFailures[result.eventId] = OfflineSyncFailure(
                    eventId = result.eventId,
                    nome = event.nome,
                    acao = event.acao,
                    mensagem = result.mensagem?.take(300) ?: "O servidor não processou este registro.",
                    tentativas = (previous?.tentativas ?: 0) + 1,
                    ultimaTentativaEmMillis = now,
                )
            }
        }

        saveBoth(
            events.copy(eventos = remainingEvents),
            meta.copy(
                falhasSincronizacao = currentFailures.values.filter { it.eventId in pendingIds }.sortedByDescending { it.ultimaTentativaEmMillis },
                ultimoServidorOkEmMillis = now,
                eventosEmQuarentena = meta.eventosEmQuarentena && remainingEvents.isNotEmpty(),
                motivoQuarentena = meta.motivoQuarentena.takeIf { meta.eventosEmQuarentena && remainingEvents.isNotEmpty() },
                quarentenaEmMillis = meta.quarentenaEmMillis.takeIf { meta.eventosEmQuarentena && remainingEvents.isNotEmpty() } ?: 0L,
            ),
        )
        processed.forEach(operationJournal::complete)
    }

    @Synchronized
    fun removeProcessed(eventIds: Collection<String>) {
        if (eventIds.isEmpty()) return
        val ids = eventIds.toHashSet()
        val events = readEvents()
        val meta = readMeta()
        val remainingEvents = events.eventos.filterNot { it.eventId in ids }
        val keepQuarantine = meta.eventosEmQuarentena && remainingEvents.isNotEmpty()
        saveBoth(
            events.copy(eventos = remainingEvents),
            meta.copy(
                falhasSincronizacao = meta.falhasSincronizacao.filterNot { it.eventId in ids },
                eventosEmQuarentena = keepQuarantine,
                motivoQuarentena = meta.motivoQuarentena.takeIf { keepQuarantine },
                quarentenaEmMillis = meta.quarentenaEmMillis.takeIf { keepQuarantine } ?: 0L,
            ),
        )
        ids.forEach(operationJournal::complete)
    }

    @Synchronized
    fun clear() {
        cachedEvents = OfflineEventsPayload()
        cachedMeta = OfflineMetaPayload()
        prefs.edit().remove(eventsKey).remove(metaKey).remove(legacyPayloadKey).apply()
        operationJournal.clear()
    }

    private fun combinedSnapshot(): PontoOfflineSnapshot {
        val events = readEvents()
        val meta = readMeta()
        return PontoOfflineSnapshot(
            eventos = events.eventos,
            pausasAbertas = meta.pausasAbertas,
            regras = meta.regras,
            regrasAtualizadasEmMillis = meta.regrasAtualizadasEmMillis,
            ultimoServidorOkEmMillis = meta.ultimoServidorOkEmMillis,
            falhasSincronizacao = meta.falhasSincronizacao,
            pausasConcluidas = meta.pausasConcluidas,
            eventosEmQuarentena = meta.eventosEmQuarentena,
            motivoQuarentena = meta.motivoQuarentena,
            quarentenaEmMillis = meta.quarentenaEmMillis,
        )
    }

    private fun readEvents(): OfflineEventsPayload {
        cachedEvents?.let { return it }
        migrateLegacyIfNeeded()
        cachedEvents?.let { return it }
        val payload = prefs.getString(eventsKey, null) ?: return OfflineEventsPayload().also { cachedEvents = it }
        val decoded = runCatching { decrypt(payload, OfflineEventsPayload::class.java) }
            .getOrNull() ?: run {
            prefs.edit().remove(eventsKey).apply()
            OfflineEventsPayload()
        }
        cachedEvents = decoded
        return decoded
    }

    private fun readMeta(): OfflineMetaPayload {
        cachedMeta?.let { return it }
        migrateLegacyIfNeeded()
        cachedMeta?.let { return it }
        val payload = prefs.getString(metaKey, null) ?: return OfflineMetaPayload().also { cachedMeta = it }
        val decoded = runCatching { decrypt(payload, OfflineMetaPayload::class.java) }
            .getOrNull() ?: run {
            prefs.edit().remove(metaKey).apply()
            OfflineMetaPayload()
        }
        cachedMeta = decoded
        return decoded
    }

    /**
     * Instalações existentes têm a fila inteira sob uma única chave legada
     * (formato pré-partição). Decifra esse blob uma única vez, grava os dois
     * novos payloads atomicamente (mesmo editor, um só commit) e só então
     * remove a chave antiga -- se o processo morrer no meio, a chave antiga
     * ainda está lá e a migração é refeita do zero na próxima leitura, sem
     * risco de perder eventos pendentes.
     */
    private fun migrateLegacyIfNeeded() {
        if (prefs.contains(eventsKey) || prefs.contains(metaKey)) return
        val legacyPayload = prefs.getString(legacyPayloadKey, null) ?: return
        val legacy = runCatching { decrypt(legacyPayload, PontoOfflineSnapshot::class.java) }
            .getOrNull() ?: run {
            prefs.edit().remove(legacyPayloadKey).apply()
            PontoOfflineSnapshot()
        }
        val events = OfflineEventsPayload(eventos = legacy.eventos)
        val meta = OfflineMetaPayload(
            pausasAbertas = legacy.pausasAbertas,
            regras = legacy.regras,
            regrasAtualizadasEmMillis = legacy.regrasAtualizadasEmMillis,
            ultimoServidorOkEmMillis = legacy.ultimoServidorOkEmMillis,
            falhasSincronizacao = legacy.falhasSincronizacao,
            pausasConcluidas = legacy.pausasConcluidas,
            eventosEmQuarentena = legacy.eventosEmQuarentena,
            motivoQuarentena = legacy.motivoQuarentena,
            quarentenaEmMillis = legacy.quarentenaEmMillis,
        )
        val editor = prefs.edit()
            .putString(eventsKey, encrypt(events))
            .putString(metaKey, encrypt(meta))
            .remove(legacyPayloadKey)
        check(editor.commit()) { "Não foi possível migrar o estado offline do Ponto para o novo formato." }
        cachedEvents = events
        cachedMeta = meta
    }

    /**
     * Escritas que representam uma saída/retorno usam commit() porque só depois
     * dessa confirmação em disco podemos apagar o operationId idempotente. As
     * demais atualizações de cache/telemetria continuam usando apply().
     */
    private fun saveMeta(meta: OfflineMetaPayload, durable: Boolean = false) {
        val editor = prefs.edit().putString(metaKey, encrypt(meta))
        if (durable) {
            check(editor.commit()) { "Não foi possível persistir o estado local do Ponto." }
        } else {
            editor.apply()
        }
        cachedMeta = meta
    }

    /**
     * Usada pelos únicos métodos que precisam adicionar/remover um evento
     * (com seu embedding) e mexer em pausas/quarentena no mesmo instante. Os
     * dois valores vão para chaves separadas mas dentro do MESMO editor, então
     * o commit()/apply() único continua atômico entre eles -- nenhuma garantia
     * de durabilidade foi perdida em relação ao blob único anterior.
     */
    private fun saveBoth(events: OfflineEventsPayload, meta: OfflineMetaPayload, durable: Boolean = false) {
        val editor = prefs.edit()
            .putString(eventsKey, encrypt(events))
            .putString(metaKey, encrypt(meta))
        if (durable) {
            check(editor.commit()) { "Não foi possível persistir o estado local do Ponto." }
        } else {
            editor.apply()
        }
        cachedEvents = events
        cachedMeta = meta
    }

    private fun <T> encrypt(payload: T): String {
        val plaintext = gson.toJson(payload).toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(plaintext)
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    private fun <T> decrypt(payload: String, type: Class<T>): T? {
        val bytes = Base64.decode(payload, Base64.NO_WRAP)
        require(bytes.size > 12)
        val iv = bytes.copyOfRange(0, 12)
        val encrypted = bytes.copyOfRange(12, bytes.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        val json = String(cipher.doFinal(encrypted), Charsets.UTF_8)
        return gson.fromJson(json, type)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        private const val MAX_PENDING_EVENTS = 500
    }
}
