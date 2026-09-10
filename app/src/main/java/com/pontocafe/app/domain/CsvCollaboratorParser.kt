package com.pontocafe.app.domain

import com.pontocafe.app.data.CollaboratorImportItem


data class CsvImportPreview(
    val valid: List<CollaboratorImportItem>,
    val errors: List<String>,
)

object CsvCollaboratorParser {
    fun parse(text: String): CsvImportPreview {
        val normalized = text.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
        val lines = normalized.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.isEmpty()) return CsvImportPreview(emptyList(), listOf("O arquivo CSV está vazio."))

        val delimiter = detectDelimiter(lines.first())
        val first = parseLine(lines.first(), delimiter).map { normalizeHeader(it) }
        val hasHeader = first.any { it in setOf("nome", "name", "setor", "sector", "turno", "shift") }
        val header = if (hasHeader) first else listOf("nome", "setor", "turno")
        val dataLines = if (hasHeader) lines.drop(1) else lines

        val nameIndex = header.indexOfFirst { it == "nome" || it == "name" }.takeIf { it >= 0 } ?: 0
        val sectorIndex = header.indexOfFirst { it == "setor" || it == "sector" }.takeIf { it >= 0 } ?: 1
        val shiftIndex = header.indexOfFirst { it == "turno" || it == "shift" }.takeIf { it >= 0 } ?: 2

        val valid = mutableListOf<CollaboratorImportItem>()
        val errors = mutableListOf<String>()

        dataLines.forEachIndexed { index, rawLine ->
            val rowNumber = index + if (hasHeader) 2 else 1
            val fields = parseLine(rawLine, delimiter)
            val name = fields.getOrNull(nameIndex).orEmpty().trim()
            val sector = fields.getOrNull(sectorIndex)?.trim()?.takeIf { it.isNotEmpty() }
            val shift = fields.getOrNull(shiftIndex)?.trim()?.takeIf { it.isNotEmpty() }

            when {
                name.length < 2 -> errors += "Linha $rowNumber: nome ausente ou inválido."
                name.length > 160 -> errors += "Linha $rowNumber: nome excede 160 caracteres."
                sector != null && sector.length > 120 -> errors += "Linha $rowNumber: setor excede 120 caracteres."
                shift != null && shift.length > 80 -> errors += "Linha $rowNumber: turno excede 80 caracteres."
                else -> valid += CollaboratorImportItem(name, sector, shift)
            }
        }

        val deduplicated = valid.distinctBy {
            Triple(it.nome.trim().lowercase(), it.setor.orEmpty().trim().lowercase(), it.turno.orEmpty().trim().lowercase())
        }
        if (deduplicated.size != valid.size) {
            errors += "${valid.size - deduplicated.size} linha(s) duplicada(s) no próprio arquivo foram ignoradas."
        }

        return CsvImportPreview(deduplicated, errors)
    }

    private fun normalizeHeader(value: String): String = value.trim().lowercase()
        .replace("á", "a")
        .replace("ã", "a")
        .replace("â", "a")
        .replace("é", "e")
        .replace("í", "i")
        .replace("ó", "o")
        .replace("õ", "o")
        .replace("ú", "u")

    private fun detectDelimiter(line: String): Char {
        val candidates = listOf(';', ',', '\t')
        return candidates.maxByOrNull { candidate -> line.count { it == candidate } } ?: ';'
    }

    private fun parseLine(line: String, delimiter: Char): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var index = 0

        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' && quoted && index + 1 < line.length && line[index + 1] == '"' -> {
                    current.append('"')
                    index += 1
                }
                char == '"' -> quoted = !quoted
                char == delimiter && !quoted -> {
                    result += current.toString()
                    current.clear()
                }
                else -> current.append(char)
            }
            index += 1
        }
        result += current.toString()
        return result
    }
}
