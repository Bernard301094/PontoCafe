package com.pontocafe.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class LocalFaceMatcherTest {

    @Test
    fun `templates da mesma pessoa nao competem pela margem`() {
        val bernard = collaborator("bernard", "Bernard")
        val outro = collaborator("outro", "Outro")
        val catalog = catalog(
            limiar = 0.80,
            margem = 0.05,
            templates = listOf(
                template(bernard, listOf(1f, 0f), "BASE"),
                template(bernard, listOf(0.999f, 0.001f), "TOUCA"),
                template(outro, listOf(0f, 1f), "BASE"),
            ),
        )

        val match = LocalFaceMatcher.match(floatArrayOf(1f, 0f), catalog)

        assertNotNull(match)
        assertEquals(bernard.id, match!!.colaborador.id)
    }

    @Test
    fun `margem continua sendo aplicada entre pessoas diferentes`() {
        val primeira = collaborator("a", "Pessoa A")
        val segunda = collaborator("b", "Pessoa B")
        val catalog = catalog(
            limiar = 0.80,
            margem = 0.05,
            templates = listOf(
                template(primeira, listOf(1f, 0f), "BASE"),
                template(segunda, listOf(0.995f, 0.10f), "BASE"),
            ),
        )

        val match = LocalFaceMatcher.match(floatArrayOf(1f, 0f), catalog)

        assertNull(match)
    }

    @Test
    fun `melhor variacao com acessorio identifica a mesma pessoa`() {
        val pessoa = collaborator("a", "Pessoa A")
        val outra = collaborator("b", "Pessoa B")
        val catalog = catalog(
            limiar = 0.70,
            margem = 0.10,
            templates = listOf(
                template(pessoa, listOf(0f, 1f), "BASE"),
                template(pessoa, listOf(1f, 0f), "TOUCA_OCULOS"),
                template(outra, listOf(0.45f, 0.55f), "BASE"),
            ),
        )

        val match = LocalFaceMatcher.match(floatArrayOf(1f, 0f), catalog)

        assertNotNull(match)
        assertEquals(pessoa.id, match!!.colaborador.id)
    }

    private fun collaborator(id: String, name: String) = Colaborador(
        id = id,
        nome = name,
        setor = "Produção",
        turno = "A",
        rostoCadastrado = true,
    )

    private fun template(
        collaborator: Colaborador,
        embedding: List<Float>,
        type: String,
    ) = CachedFaceTemplate(
        colaborador = collaborator,
        embedding = embedding,
        modelo = "FaceNet 128D · LiteRT",
        versaoModelo = "facenet-128d-160-v1",
        atualizadoEm = "2026-08-17T00:00:00Z",
        templateId = "${collaborator.id}-$type",
        tipo = type,
    )

    private fun catalog(
        limiar: Double,
        margem: Double,
        templates: List<CachedFaceTemplate>,
    ) = CachedFaceCatalog(
        versao = "test",
        modelo = "FaceNet 128D · LiteRT",
        versaoModelo = "facenet-128d-160-v1",
        limiar = limiar,
        margem = margem,
        templates = templates,
        sincronizadoEmMillis = 0L,
    )
}
