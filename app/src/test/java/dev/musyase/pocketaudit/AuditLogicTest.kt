package dev.musyase.pocketaudit

import dev.musyase.pocketaudit.domain.AuditLogic
import dev.musyase.pocketaudit.domain.AuditSnapshot
import dev.musyase.pocketaudit.domain.Condition
import dev.musyase.pocketaudit.domain.InventoryItem
import dev.musyase.pocketaudit.domain.Observation
import dev.musyase.pocketaudit.domain.RowStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class AuditLogicTest {
    private val items = listOf(
        InventoryItem("A", "Meter", "Bench"),
        InventoryItem("B", "Camera", "Shelf"),
        InventoryItem("C", "Tripod", "Shelf"),
    )

    @Test fun pendingItemsBecomeMissingOnlyAfterCompletion() {
        val current = AuditSnapshot(items = items, observations = listOf(Observation("A", Condition.GOOD, "", null, 1)))
        assertEquals(RowStatus.NOT_CHECKED, AuditLogic.rows(current).first { it.code == "B" }.status)
        assertEquals(RowStatus.MISSING, AuditLogic.rows(current.copy(completedAt = 2)).first { it.code == "B" }.status)
        assertEquals(2, AuditLogic.summary(current).remaining)
    }

    @Test fun damagedAndUnlistedCodesAreSeparateIssues() {
        val current = AuditSnapshot(items, listOf(
            Observation("A", Condition.DAMAGED, "Cracked", null, 1),
            Observation("EXTRA", Condition.GOOD, "", null, 2),
        ))
        val rows = AuditLogic.rows(current)
        assertEquals(RowStatus.ATTENTION, rows.first { it.code == "A" }.status)
        assertEquals(RowStatus.UNEXPECTED, rows.first { it.code == "EXTRA" }.status)
        assertEquals(1, AuditLogic.summary(current).attention)
        assertEquals(1, AuditLogic.summary(current).unexpected)
        assertEquals(1, AuditLogic.summary(current).checked)
    }
}
