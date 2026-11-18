package dev.musyase.pocketaudit.domain

data class InventoryItem(val code: String, val name: String, val location: String)

class CsvProblem(message: String) : IllegalArgumentException(message)

object InventoryCsv {
    private data class ParsedRow(val cells: List<String>, val line: Int)

    fun parse(text: String): List<InventoryItem> {
        if ('\u0000' in text) throw CsvProblem("The file contains a null character.")
        val rows = parseRows(text.removePrefix("\uFEFF"))
        if (rows.isEmpty()) throw CsvProblem("The CSV file is empty.")
        val header = rows.first().cells.map { it.trim().lowercase() }
        val codeIndex = header.indexOf("code")
        val nameIndex = header.indexOf("name")
        val locationIndex = header.indexOf("location")
        if (codeIndex < 0 || nameIndex < 0 || locationIndex < 0) {
            throw CsvProblem("The header needs code, name, and location columns.")
        }
        if (header.count { it == "code" } != 1 || header.count { it == "name" } != 1 ||
            header.count { it == "location" } != 1) {
            throw CsvProblem("The header contains a repeated required column.")
        }
        val result = mutableListOf<InventoryItem>()
        val seen = mutableSetOf<String>()
        for (row in rows.drop(1)) {
            if (row.cells.size != header.size) throw CsvProblem("Line ${row.line}: expected ${header.size} columns, found ${row.cells.size}.")
            val code = row.cells[codeIndex].trim()
            val name = row.cells[nameIndex].trim()
            val location = row.cells[locationIndex].trim()
            if (code.isEmpty()) throw CsvProblem("Line ${row.line}: code is empty.")
            if (code.length > 128 || code.any { it.isISOControl() }) throw CsvProblem("Line ${row.line}: code must have 1 to 128 printable characters.")
            if (name.isEmpty()) throw CsvProblem("Line ${row.line}: name is empty.")
            if (name.length > 300) throw CsvProblem("Line ${row.line}: name is longer than 300 characters.")
            if (location.isEmpty()) throw CsvProblem("Line ${row.line}: location is empty.")
            if (location.length > 200) throw CsvProblem("Line ${row.line}: location is longer than 200 characters.")
            if (!seen.add(code)) throw CsvProblem("Line ${row.line}: code $code appears more than once.")
            result += InventoryItem(code, name, location)
            if (result.size > 5000) throw CsvProblem("The file has more than 5,000 items.")
        }
        if (result.isEmpty()) throw CsvProblem("The file has a header but no items.")
        return result
    }

    private fun parseRows(text: String): List<ParsedRow> {
        val rows = mutableListOf<ParsedRow>()
        val cells = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var quoteClosed = false
        var atStart = true
        var line = 1
        var rowLine = 1
        var i = 0
        fun endField() {
            cells += field.toString()
            field.clear()
            atStart = true
            quoteClosed = false
        }
        fun endRow() {
            endField()
            if (cells.any { it.isNotBlank() }) rows += ParsedRow(cells.toList(), rowLine)
            cells.clear()
            rowLine = line
        }
        while (i < text.length) {
            val c = text[i]
            if (quoted) {
                when {
                    c == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
                        field.append('"')
                        i++
                    }
                    c == '"' -> { quoted = false; quoteClosed = true }
                    c == '\r' && i + 1 < text.length && text[i + 1] == '\n' -> {
                        field.append('\n'); i++; line++
                    }
                    c == '\n' || c == '\r' -> { field.append('\n'); line++ }
                    else -> field.append(c)
                }
            } else {
                when {
                    c == '"' && atStart -> { quoted = true; atStart = false }
                    c == '"' -> throw CsvProblem("Line $line: quote inside an unquoted field.")
                    c == ',' -> endField()
                    c == '\n' || c == '\r' -> {
                        if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                        line++
                        endRow()
                    }
                    quoteClosed -> throw CsvProblem("Line $line: text after a closing quote.")
                    else -> { field.append(c); atStart = false }
                }
            }
            i++
        }
        if (quoted) throw CsvProblem("Line $rowLine: a quoted field is not closed.")
        if (field.isNotEmpty() || cells.isNotEmpty() || quoteClosed) endRow()
        return rows
    }

    fun encode(rows: List<List<String>>, protectSpreadsheet: Boolean = false): String = buildString {
        rows.forEachIndexed { index, row ->
            if (index > 0) append("\r\n")
            row.forEachIndexed { column, value ->
                if (column > 0) append(',')
                val safe = if (protectSpreadsheet && value.firstOrNull() in listOf('=', '+', '-', '@')) "'$value" else value
                val escaped = safe.replace("\"", "\"\"")
                if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
                    append('"').append(escaped).append('"')
                } else append(safe)
            }
        }
        append("\r\n")
    }
}
