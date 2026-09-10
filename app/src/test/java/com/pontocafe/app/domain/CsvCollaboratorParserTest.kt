package com.pontocafe.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvCollaboratorParserTest {
    @Test
    fun `le csv brasileiro com ponto e virgula`() {
        val preview = CsvCollaboratorParser.parse(
            "Nome;Setor;Turno\nJoão Paulo Pereira;Produção;A\nMaria Silva;Envase;B",
        )

        assertEquals(2, preview.valid.size)
        assertEquals("João Paulo Pereira", preview.valid.first().nome)
        assertEquals("Produção", preview.valid.first().setor)
        assertEquals("A", preview.valid.first().turno)
    }

    @Test
    fun `remove duplicados dentro do arquivo`() {
        val preview = CsvCollaboratorParser.parse(
            "Nome;Setor;Turno\nAna;Produção;A\nana;produção;a",
        )

        assertEquals(1, preview.valid.size)
        assertTrue(preview.errors.any { it.contains("duplicada", ignoreCase = true) })
    }

    @Test
    fun `aceita campos entre aspas`() {
        val preview = CsvCollaboratorParser.parse(
            "Nome,Setor,Turno\n\"Silva, João\",Produção,A",
        )

        assertEquals(1, preview.valid.size)
        assertEquals("Silva, João", preview.valid.first().nome)
    }
}
