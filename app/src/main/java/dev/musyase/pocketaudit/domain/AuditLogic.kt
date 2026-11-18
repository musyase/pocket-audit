package dev.musyase.pocketaudit.domain

enum class Condition { GOOD, DAMAGED, NEEDS_REPAIR }
enum class RowStatus { CHECKED, ATTENTION, UNEXPECTED, NOT_CHECKED, MISSING }

data class Observation(
    val code: String,
    val condition: Condition,
    val note: String,
    val photoPath: String?,
    val observedAt: Long,
)

data class AuditSnapshot(
    val items: List<InventoryItem> = emptyList(),
    val observations: List<Observation> = emptyList(),
    val completedAt: Long? = null,
    val sourceLabel: String = "Inventory",
)

data class AuditRow(
    val code: String,
    val item: InventoryItem?,
    val observation: Observation?,
    val status: RowStatus,
)

data class AuditSummary(
    val expected: Int,
    val checked: Int,
    val attention: Int,
    val unexpected: Int,
    val remaining: Int,
    val complete: Boolean,
)

object AuditLogic {
    fun rows(snapshot: AuditSnapshot): List<AuditRow> {
        val observed = snapshot.observations.associateBy { it.code }
        val expected = snapshot.items.map { item ->
            val observation = observed[item.code]
            val status = when {
                observation == null && snapshot.completedAt != null -> RowStatus.MISSING
                observation == null -> RowStatus.NOT_CHECKED
                observation.condition == Condition.GOOD -> RowStatus.CHECKED
                else -> RowStatus.ATTENTION
            }
            AuditRow(item.code, item, observation, status)
        }
        val codes = snapshot.items.mapTo(mutableSetOf()) { it.code }
        val unexpected = snapshot.observations.filter { it.code !in codes }
            .sortedBy { it.code }
            .map { AuditRow(it.code, null, it, RowStatus.UNEXPECTED) }
        return expected + unexpected
    }

    fun summary(snapshot: AuditSnapshot): AuditSummary {
        val expected = snapshot.items.mapTo(mutableSetOf()) { it.code }
        val known = snapshot.observations.filter { it.code in expected }
        return AuditSummary(
            expected = expected.size,
            checked = known.size,
            attention = known.count { it.condition != Condition.GOOD },
            unexpected = snapshot.observations.count { it.code !in expected },
            remaining = expected.size - known.size,
            complete = snapshot.completedAt != null,
        )
    }
}
