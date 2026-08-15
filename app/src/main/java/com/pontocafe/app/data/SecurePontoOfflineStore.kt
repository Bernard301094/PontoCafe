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
import java.util.UUID
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

data class PontoOfflineSnapshot(
    val eventos: List<OfflinePontoEvent> = emptyList(),
    val pausasAbertas: List<LocalOpenPause> = emptyList(),
    val regras: List<RegraCafe> = emptyList(),
    val regrasAtualizadasEmMillis: Long = 0L,
    val ultimoServidorOkEmMillis: Long = 0L,
)

class SecurePontoOfflineStore(context: Context) {
    private val prefs = context.getSharedPreferences("pontocafe_offline_secure", Context.MODE_PRIVATE)
    private val keyAlias = "pontocafe_offline_key"
    private val payloadKey = "offline_snapshot"
    private val gson = Gson()
    private val timezone = ZoneId.of("America/Fortaleza")
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    @Synchronized
    fun snapshot(): PontoOfflineSnapshot = readInternal()

    @Synchronized
    fun pendingEvents(): List<OfflinePontoEvent> = readInternal().eventos

    @Synchronized
    fun pendingCount(): Int = readInternal().eventos.size

    @Synchronized
    fun lastServerOkMillis(): Long = readInternal().ultimoServidorOkEmMillis

    @Synchronized
    fun canOperateOffline(maxOfflineMillis: Long): Boolean {
        val lastOk = readInternal().ultimoServidorOkEmMillis
        return lastOk > 0L && System.currentTimeMillis() - lastOk <= maxOfflineMillis
    }

    @Synchronized
    fun markServerOk() {
        val current = readInternal()
        saveInternal(current.copy(ultimoServidorOkEmMillis = System.currentTimeMillis()))
    }

    @Synchronized
    fun saveRules(rules: List<RegraCafe>) {
        val current = readInternal()
        saveInternal(
            current.copy(
                regras = rules,
                regrasAtualizadasEmMillis = System.currentTimeMillis(),
                ultimoServidorOkEmMillis = System.currentTimeMillis(),
            ),
        )
    }

    @Synchronized
    fun currentRule(now: ZonedDateTime = ZonedDateTime.now(timezone)): RegraCafe? {
        val currentTime = now.toLocalTime()
        return readInternal().regras.firstOrNull { rule ->
            runCatching {
                val start = LocalTime.parse(rule.inicio)
                val end = LocalTime.parse(rule.fim)
                !currentTime.isBefore(start) && currentTime.isBefore(end)
            }.getOrDefault(false)
        }
    }

    @Synchronized
    fun localOpenPause(collaboratorId: String): LocalOpenPause? =
        readInternal().pausasAbertas.firstOrNull { it.colaboradorId == collaboratorId }

    @Synchronized
    fun recordOnlineStart(collaboratorId: String, nome: String, pause: IniciarPausaResponse) {
        val current = readInternal()
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
        saveInternal(
            current.copy(
                pausasAbertas = current.pausasAbertas.filterNot { it.colaboradorId == collaboratorId } + localPause,
                ultimoServidorOkEmMillis = System.currentTimeMillis(),
            ),
        )
    }

    @Synchronized
    fun recordOnlineFinish(collaboratorId: String) {
        val current = readInternal()
        saveInternal(
            current.copy(
                pausasAbertas = current.pausasAbertas.filterNot { it.colaboradorId == collaboratorId },
                ultimoServidorOkEmMillis = System.currentTimeMillis(),
            ),
        )
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
        val current = readInternal()
        require(current.eventos.size < MAX_PENDING_EVENTS) { "Há muitos registros offline aguardando sincronização." }
        require(current.pausasAbertas.none { it.colaboradorId == colaborador.id }) { "Já existe uma pausa aberta neste dispositivo." }
        require(embedding.isNotEmpty() && embedding.all { it.isFinite() }) { "A biometria offline é inválida." }

        val now = ZonedDateTime.now(timezone)
        val event = OfflinePontoEvent(
            eventId = UUID.randomUUID().toString(),
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
        saveInternal(
            current.copy(
                eventos = current.eventos + event,
                pausasAbertas = current.pausasAbertas + localPause,
            ),
        )
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
        val current = readInternal()
        require(current.eventos.size < MAX_PENDING_EVENTS) { "Há muitos registros offline aguardando sincronização." }
        require(embedding.isNotEmpty() && embedding.all { it.isFinite() }) { "A biometria offline é inválida." }
        val open = current.pausasAbertas.firstOrNull { it.colaboradorId == colaborador.id }
            ?: error("Não existe pausa local aberta para este colaborador.")
        val now = ZonedDateTime.now(timezone)
        val event = OfflinePontoEvent(
            eventId = UUID.randomUUID().toString(),
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
        saveInternal(
            current.copy(
                eventos = current.eventos + event,
                pausasAbertas = current.pausasAbertas.filterNot { it.colaboradorId == colaborador.id },
            ),
        )
        return open to duration
    }

    @Synchronized
    fun removeProcessed(eventIds: Collection<String>) {
        if (eventIds.isEmpty()) return
        val ids = eventIds.toHashSet()
        val current = readInternal()
        saveInternal(current.copy(eventos = current.eventos.filterNot { it.eventId in ids }))
    }

    @Synchronized
    fun clear() {
        prefs.edit().remove(payloadKey).apply()
    }

    private fun readInternal(): PontoOfflineSnapshot {
        val payload = prefs.getString(payloadKey, null) ?: return PontoOfflineSnapshot()
        return runCatching {
            val bytes = Base64.decode(payload, Base64.NO_WRAP)
            require(bytes.size > 12)
            val iv = bytes.copyOfRange(0, 12)
            val encrypted = bytes.copyOfRange(12, bytes.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            val json = String(cipher.doFinal(encrypted), Charsets.UTF_8)
            gson.fromJson(json, PontoOfflineSnapshot::class.java) ?: PontoOfflineSnapshot()
        }.getOrElse {
            prefs.edit().remove(payloadKey).apply()
            PontoOfflineSnapshot()
        }
    }

    private fun saveInternal(snapshot: PontoOfflineSnapshot) {
        val plaintext = gson.toJson(snapshot).toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(plaintext)
        val payload = Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
        prefs.edit().putString(payloadKey, payload).apply()
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
