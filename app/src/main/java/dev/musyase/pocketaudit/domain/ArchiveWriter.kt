package dev.musyase.pocketaudit.domain

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ArchiveWriter {
    private data class StoredFile(val name: String, val sha256: String, val bytes: Long)

    fun write(output: OutputStream, snapshot: AuditSnapshot, photosRoot: File) {
        val rows = AuditLogic.rows(snapshot)
        val photoNames = rows.filter { it.observation?.photoPath != null }
            .mapIndexed { index, row -> row.code to "photos/${index + 1}.jpg" }.toMap()
        val csv = mutableListOf(listOf("code", "name", "location", "status", "condition", "note", "observed_at_utc", "photo_file"))
        rows.forEach { row ->
            csv += listOf(
                row.code,
                row.item?.name.orEmpty(),
                row.item?.location.orEmpty(),
                row.status.name.lowercase(),
                row.observation?.condition?.name?.lowercase().orEmpty(),
                row.observation?.note.orEmpty(),
                row.observation?.observedAt?.let { Instant.ofEpochMilli(it).toString() }.orEmpty(),
                photoNames[row.code].orEmpty(),
            )
        }
        val entries = mutableListOf<StoredFile>()
        ZipOutputStream(output).use { zip ->
            val report = InventoryCsv.encode(csv, protectSpreadsheet = true).toByteArray(Charsets.UTF_8)
            entries += add(zip, "report.csv", ByteArrayInputStream(report))
            val root = photosRoot.canonicalFile
            for (row in rows) {
                val path = row.observation?.photoPath ?: continue
                val file = File(path).canonicalFile
                if (file.parentFile != root || !file.isFile) throw IOException("Photo for ${row.code} is unavailable.")
                entries += add(zip, photoNames.getValue(row.code), file.inputStream())
            }
            val manifest = buildString {
                append("{\"schema_version\":1,\"generated_at_utc\":")
                append(json(Instant.now().toString()))
                append(",\"audit_complete\":${snapshot.completedAt != null},\"files\":[")
                entries.forEachIndexed { index, entry ->
                    if (index > 0) append(',')
                    append("{\"name\":${json(entry.name)},\"sha256\":${json(entry.sha256)},\"bytes\":${entry.bytes}}")
                }
                append("]}\n")
            }
            add(zip, "manifest.json", ByteArrayInputStream(manifest.toByteArray(Charsets.UTF_8)))
        }
    }

    private fun add(zip: ZipOutputStream, name: String, input: InputStream): StoredFile {
        val digest = MessageDigest.getInstance("SHA-256")
        var count = 0L
        zip.putNextEntry(ZipEntry(name))
        input.use { stream ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                zip.write(buffer, 0, read)
                digest.update(buffer, 0, read)
                count += read
            }
        }
        zip.closeEntry()
        return StoredFile(name, digest.digest().joinToString("") { "%02x".format(it) }, count)
    }

    private fun json(value: String): String = buildString {
        append('"')
        value.forEach { c ->
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c.code < 32) append("\\u%04x".format(c.code)) else append(c)
            }
        }
        append('"')
    }
}
