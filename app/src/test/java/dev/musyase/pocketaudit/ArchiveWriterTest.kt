package dev.musyase.pocketaudit

import dev.musyase.pocketaudit.domain.ArchiveWriter
import dev.musyase.pocketaudit.domain.AuditSnapshot
import dev.musyase.pocketaudit.domain.Condition
import dev.musyase.pocketaudit.domain.InventoryItem
import dev.musyase.pocketaudit.domain.Observation
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ArchiveWriterTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun archiveIncludesPhotoCsvAndMatchingManifestHash() {
        val photos = temporary.newFolder("photos")
        val photo = photos.resolve("capture.jpg").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val snapshot = AuditSnapshot(
            items = listOf(InventoryItem("A", "Meter", "Bench"), InventoryItem("B", "Light", "Shelf")),
            observations = listOf(Observation("A", Condition.DAMAGED, "Glass, cracked", photo.absolutePath, 0)),
            completedAt = 123,
        )
        val output = ByteArrayOutputStream()
        ArchiveWriter.write(output, snapshot, photos)
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(output.toByteArray().inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes()
            }
        }
        assertEquals(setOf("report.csv", "photos/1.jpg", "manifest.json"), entries.keys)
        val report = entries.getValue("report.csv").toString(Charsets.UTF_8)
        assertTrue(report.contains("missing"))
        assertTrue(report.contains("\"Glass, cracked\""))
        assertEquals(listOf<Byte>(1, 2, 3, 4), entries.getValue("photos/1.jpg").toList())
        val manifest = entries.getValue("manifest.json").toString(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(entries.getValue("report.csv"))
            .joinToString("") { "%02x".format(it) }
        assertTrue(manifest.contains(digest))
        assertTrue(manifest.contains("\"audit_complete\":true"))
    }
}
