package com.pontocafe.app.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
                template(bernard, vectorList(1f, 0f), "BASE_A"),
                template(bernard, vectorList(0.999f, 0.001f), "BASE_B"),
                template(outro, vectorList(0f, 1f), "BASE"),
            ),
        )

        val match = LocalFaceMatcher.match(vector(1f, 0f), catalog)

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
                template(primeira, vectorList(1f, 0f), "BASE"),
                template(segunda, vectorList(0.995f, 0.10f), "BASE"),
            ),
        )

        val match = LocalFaceMatcher.match(vector(1f, 0f), catalog)

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
                template(primeira, vectorList(1f, 0f), "BASE"),
                template(segunda, vectorList(0f, 1f), "BASE"),
            ),
        )
        val primary = vector(1f, 0f)
        val fallback = vector(0f, 1f)

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
                template(pessoa, vectorList(1f, 0f), "BASE"),
                template(outra, vectorList(0f, 1f), "BASE"),
            ),
        )
        val primary = vector(0.70f, 0.70f)
        val fallback = vector(0.99f, 0.01f)

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
                template(primeira, vectorList(1f, 0f), "BASE"),
                template(segunda, vectorList(0.995f, 0.10f), "BASE"),
            ),
        )

        val resolved = LocalFaceMatcher.matchBest(
            listOf(
                vector(0.7f, 0.7f),
                vector(1f, 0f),
            ),
            catalog,
        )

        assertNull(resolved)
    }

    @Test
    fun `indice preparado troca junto com a lista do catalogo mesmo na mesma versao`() {
        val primeira = collaborator("a", "Pessoa A")
        val segunda = collaborator("b", "Pessoa B")
        val primeiroCatalogo = catalog(
            limiar = 0.80,
            margem = 0.05,
            templates = listOf(template(primeira, vectorList(1f, 0f), "BASE")),
        )
        val catalogoAtualizado = catalog(
            limiar = 0.80,
            margem = 0.05,
            templates = listOf(template(segunda, vectorList(0f, 1f), "BASE")),
        )

        assertEquals(primeira.id, LocalFaceMatcher.match(vector(1f, 0f), primeiroCatalogo)?.colaborador?.id)
        assertNull(LocalFaceMatcher.match(vector(0f, 1f), primeiroCatalogo))

        val atualizado = LocalFaceMatcher.match(vector(0f, 1f), catalogoAtualizado)

        assertNotNull(atualizado)
        assertEquals(segunda.id, atualizado!!.colaborador.id)
        assertNull(LocalFaceMatcher.match(vector(1f, 0f), catalogoAtualizado))
    }

    @Test
    fun `embedding corrompido ou com dimensao errada e recusado`() {
        val pessoa = collaborator("a", "Pessoa A")
        val currentCatalog = catalog(
            limiar = 0.72,
            margem = 0.06,
            templates = listOf(template(pessoa, vectorList(1f, 0f), "BASE")),
        )

        val nonFinite = vector(1f, 0f).also { it[4] = Float.NaN }
        val invalidNonFinite = LocalFaceMatcher.evaluateDetailed(nonFinite, currentCatalog)
        val invalidDimension = LocalFaceMatcher.evaluateDetailed(floatArrayOf(1f, 0f), currentCatalog)

        assertNull(invalidNonFinite.match)
        assertEquals(LocalFaceRejectionReason.INVALID_EMBEDDING, invalidNonFinite.rejectionReason)
        assertNull(invalidDimension.match)
        assertEquals(LocalFaceRejectionReason.INVALID_EMBEDDING, invalidDimension.rejectionReason)
    }

    @Test
    fun `template de outra versao de modelo nao participa`() {
        val pessoa = collaborator("a", "Pessoa A")
        val incompatible = template(pessoa, vectorList(1f, 0f), "BASE")
            .copy(versaoModelo = "facenet-incompativel")
        val evaluation = LocalFaceMatcher.evaluateDetailed(
            vector(1f, 0f),
            catalog(0.72, 0.06, listOf(incompatible)),
        )

        assertNull(evaluation.match)
        assertEquals(LocalFaceRejectionReason.NO_COMPATIBLE_TEMPLATE, evaluation.rejectionReason)
        assertEquals(0, evaluation.validTemplateCount)
    }

    @Test
    fun `fallbacks contraditorios entre pessoas sao recusados`() {
        val primeira = collaborator("a", "Pessoa A")
        val segunda = collaborator("b", "Pessoa B")
        val currentCatalog = catalog(
            limiar = 0.72,
            margem = 0.06,
            templates = listOf(
                template(primeira, vectorList(1f, 0f), "BASE"),
                template(segunda, vectorList(0f, 1f), "BASE"),
            ),
        )

        val result = LocalFaceMatcher.matchBest(
            listOf(vector(0.70f, 0.70f), vector(1f, 0f), vector(0f, 1f)),
            currentCatalog,
            announce = false,
        )

        assertNull(result)
    }

    @Test
    fun `avaliacao detalhada informa top1 top2 e margem`() {
        val primeira = collaborator("a", "Pessoa A")
        val segunda = collaborator("b", "Pessoa B")
        val evaluation = LocalFaceMatcher.evaluateDetailed(
            vector(1f, 0f),
            catalog(
                limiar = 0.72,
                margem = 0.06,
                templates = listOf(
                    template(primeira, vectorList(0.90f, 0.4358899f), "BASE"),
                    template(segunda, vectorList(0.82f, 0.5723635f), "BASE"),
                ),
            ),
        )

        assertNotNull(evaluation.match)
        assertEquals(0.90, evaluation.bestScore!!, 0.0001)
        assertEquals(0.82, evaluation.secondScore!!, 0.0001)
        assertEquals(0.08, evaluation.margin!!, 0.0001)
        assertTrue(evaluation.validTemplateCount == 2)
    }

    @Test
    fun `cadastro duplicado e detectado quando o score alcanca o limiar de reconhecimento`() {
        val existente = collaborator("a", "Pessoa A")
        val novo = collaborator("b", "Pessoa B")
        val currentCatalog = catalog(
            limiar = 0.80,
            margem = 0.05,
            templates = listOf(template(existente, vectorList(1f, 0f), "BASE")),
        )

        val result = LocalFaceMatcher.evaluateEnrollmentDuplicate(
            candidateEmbedding = vector(1f, 0f),
            catalog = currentCatalog,
            excludeCollaboratorId = novo.id,
        )

        assertTrue(result.duplicate)
        assertEquals(existente.id, result.matchedCollaborador?.id)
    }

    @Test
    fun `cadastro duplicado nao dispara abaixo do limiar de reconhecimento`() {
        val existente = collaborator("a", "Pessoa A")
        val novo = collaborator("b", "Pessoa B")
        val currentCatalog = catalog(
            limiar = 0.80,
            margem = 0.05,
            templates = listOf(template(existente, vectorList(1f, 0f), "BASE")),
        )

        val result = LocalFaceMatcher.evaluateEnrollmentDuplicate(
            candidateEmbedding = vector(0.70f, 0.70f),
            catalog = currentCatalog,
            excludeCollaboratorId = novo.id,
        )

        assertTrue(!result.duplicate)
    }

    @Test
    fun `cadastro duplicado ignora os proprios templates do colaborador sendo recadastrado`() {
        val pessoa = collaborator("a", "Pessoa A")
        val currentCatalog = catalog(
            limiar = 0.80,
            margem = 0.05,
            templates = listOf(template(pessoa, vectorList(1f, 0f), "BASE")),
        )

        val result = LocalFaceMatcher.evaluateEnrollmentDuplicate(
            candidateEmbedding = vector(1f, 0f),
            catalog = currentCatalog,
            excludeCollaboratorId = pessoa.id,
        )

        assertTrue(!result.duplicate)
        assertNull(result.matchedCollaborador)
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

    private fun vector(first: Float, second: Float): FloatArray =
        FloatArray(FACE_EMBEDDING_DIMENSION).also {
            it[0] = first
            it[1] = second
        }

    private fun vectorList(first: Float, second: Float): List<Float> =
        vector(first, second).toList()
}
