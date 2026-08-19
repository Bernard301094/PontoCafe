package com.pontocafe.app.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
                template(bernard, listOf(1f, 0f), "BASE_A"),
                template(bernard, listOf(0.999f, 0.001f), "BASE_B"),
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
    fun `match adaptativo preserva prioridade da tentativa canonica`() {
        val primeira = collaborator("a", "Pessoa A")
        val segunda = collaborator("b", "Pessoa B")
        val catalog = catalog(
            limiar = 0.80,
            margem = 0.05,
            templates = listOf(
                template(primeira, listOf(1f, 0f), "BASE"),
                template(segunda, listOf(0f, 1f), "BASE"),
            ),
        )
        val primary = floatArrayOf(1f, 0f)
        val fallback = floatArrayOf(0f, 1f)

        val resolved = LocalFaceMatcher.matchBest(listOf(primary, fallback), catalog)

        assertNotNull(resolved)
        assertEquals(primeira.id, resolved!!.match.colaborador.id)
        assertEquals(0, resolved.candidateIndex)
        assertArrayEquals(primary, resolved.embedding, 0f)
    }

    @Test
    fun `match adaptativo usa recorte alternativo somente quando canonico falha`() {
        val pessoa = collaborator("a", "Pessoa A")
        val outra = collaborator("b", "Pessoa B")
        val catalog = catalog(
            limiar = 0.80,
            margem = 0.08,
            templates = listOf(
                template(pessoa, listOf(1f, 0f), "BASE"),
                template(outra, listOf(0f, 1f), "BASE"),
            ),
        )
        val primary = floatArrayOf(0.70f, 0.70f)
        val fallback = floatArrayOf(0.99f, 0.01f)

        val resolved = LocalFaceMatcher.matchBest(listOf(primary, fallback), catalog)

        assertNotNull(resolved)
        assertEquals(pessoa.id, resolved!!.match.colaborador.id)
        assertEquals(1, resolved.candidateIndex)
        assertArrayEquals(fallback, resolved.embedding, 0f)
    }

    @Test
    fun `recorte alternativo nao ignora margem de seguranca`() {
        val primeira = collaborator("a", "Pessoa A")
        val segunda = collaborator("b", "Pessoa B")
        val catalog = catalog(
            limiar = 0.80,
            margem = 0.08,
            templates = listOf(
                template(primeira, listOf(1f, 0f), "BASE"),
                template(segunda, listOf(0.995f, 0.10f), "BASE"),
            ),
        )

        val resolved = LocalFaceMatcher.matchBest(
            listOf(
                floatArrayOf(0.7f, 0.7f),
                floatArrayOf(1f, 0f),
            ),
            catalog,
        )

        assertNull(resolved)
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
        atualizadoEm = "2026-08-19T00:00:00Z",
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
