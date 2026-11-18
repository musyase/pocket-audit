package dev.musyase.pocketaudit

import dev.musyase.pocketaudit.domain.CsvProblem
import dev.musyase.pocketaudit.domain.InventoryCsv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InventoryCsvTest {
    @Test fun readsBomQuotesCommasAndMultilineCells() {
        val items = InventoryCsv.parse("\uFEFFlocation,code,name\r\n\"Bench, east\",A-1,\"Meter \"\"pro\"\"\"\r\nShelf,A-2,\"Light\nstand\"\r\n")
        assertEquals(2, items.size)
        assertEquals("A-1", items[0].code)
        assertEquals("Bench, east", items[0].location)
        assertEquals("Meter \"pro\"", items[0].name)
        assertEquals("Light\nstand", items[1].name)
    }

    @Test fun rejectsDuplicateWithSourceLine() {
        val error = expectProblem("code,name,location\nX,One,Shelf\nX,Two,Shelf\n")
        assertTrue(error.contains("Line 3"))
        assertTrue(error.contains("X"))
    }

    @Test fun rejectsMissingHeaderAndMalformedQuote() {
        assertTrue(expectProblem("code,name\nX,One\n").contains("location"))
        assertTrue(expectProblem("code,name,location\nX,\"Open,Shelf\n").contains("not closed"))
    }

    @Test fun rejectsExtraColumnsAndBlankRequiredFields() {
        assertTrue(expectProblem("code,name,location\nX,One,Shelf,Extra\n").contains("Line 2"))
        assertTrue(expectProblem("code,name,location\n,One,Shelf\n").contains("code is empty"))
    }

    @Test fun rejectsCodesThatCannotBeEnteredAndProtectsSpreadsheetOutput() {
        assertTrue(expectProblem("code,name,location\n\"A\nB\",One,Shelf\n").contains("printable"))
        val csv = InventoryCsv.encode(listOf(listOf("code", "note"), listOf("=HYPERLINK(1)", "+SUM(1,2)")), protectSpreadsheet = true)
        assertTrue(csv.contains("'=HYPERLINK(1)"))
        assertTrue(csv.contains("'+SUM(1,2)"))
    }

    private fun expectProblem(text: String): String = try {
        InventoryCsv.parse(text)
        throw AssertionError("Expected CSV to fail")
    } catch (error: CsvProblem) { error.message.orEmpty() }
}
