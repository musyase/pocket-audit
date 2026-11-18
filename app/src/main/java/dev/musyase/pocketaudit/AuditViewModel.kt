package dev.musyase.pocketaudit

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import dev.musyase.pocketaudit.data.AuditDatabase
import dev.musyase.pocketaudit.data.AuditMetaEntity
import dev.musyase.pocketaudit.data.ItemEntity
import dev.musyase.pocketaudit.data.ObservationEntity
import dev.musyase.pocketaudit.domain.ArchiveWriter
import dev.musyase.pocketaudit.domain.AuditSnapshot
import dev.musyase.pocketaudit.domain.Condition
import dev.musyase.pocketaudit.domain.InventoryCsv
import dev.musyase.pocketaudit.domain.InventoryItem
import dev.musyase.pocketaudit.domain.Observation
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AuditViewModel(private val app: Application) : AndroidViewModel(app) {
    private val database = AuditDatabase.get(app)
    private val dao = database.auditDao()
    private val preferences = app.getSharedPreferences("pocket-audit", 0)
    val ready = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)

    val snapshot = combine(dao.items(), dao.observations(), dao.meta()) { items, observations, meta ->
        AuditSnapshot(
            items.map { InventoryItem(it.code, it.name, it.location) },
            observations.map {
                Observation(it.code, Condition.valueOf(it.condition), it.note, it.photoPath, it.observedAt)
            },
            meta?.completedAt,
            meta?.sourceLabel ?: "Inventory",
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AuditSnapshot())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (!preferences.getBoolean("initialized", false)) {
                    replace(InventoryCsv.parse(app.assets.open("demo_inventory.csv").bufferedReader().use { it.readText() }), "Fictional sample")
                    preferences.edit().putBoolean("initialized", true).apply()
                }
            } catch (error: Exception) {
                message.value = "Could not load the sample inventory: ${error.message}"
            } finally {
                ready.value = true
            }
        }
    }

    suspend fun readImport(uri: Uri): List<InventoryItem> = withContext(Dispatchers.IO) {
        val input = app.contentResolver.openInputStream(uri) ?: throw IOException("Could not open the selected file.")
        val bytes = ByteArrayOutputStream()
        input.use { stream ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                bytes.write(buffer, 0, read)
                if (bytes.size() > 5 * 1024 * 1024) throw IOException("The CSV file is larger than 5 MiB.")
            }
        }
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val text = try { decoder.decode(ByteBuffer.wrap(bytes.toByteArray())).toString() }
            catch (_: Exception) { throw IOException("The CSV file must use UTF-8.") }
        InventoryCsv.parse(text)
    }

    fun import(items: List<InventoryItem>) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { replace(items, "Imported CSV"); preferences.edit().putBoolean("initialized", true).apply() }
                .onSuccess { message.value = "Imported ${items.size} items. The previous audit was replaced." }
                .onFailure { message.value = "Import failed: ${it.message}" }
        }
    }

    fun resetDemo() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { replace(InventoryCsv.parse(app.assets.open("demo_inventory.csv").bufferedReader().use { it.readText() }), "Fictional sample") }
                .onSuccess { message.value = "Fictional sample restored." }
                .onFailure { message.value = "Could not restore the sample: ${it.message}" }
        }
    }

    private suspend fun replace(items: List<InventoryItem>, sourceLabel: String) {
        database.withTransaction {
            dao.clearObservations()
            dao.clearItems()
            dao.putItems(items.map { ItemEntity(it.code, it.name, it.location) })
            dao.putMeta(AuditMetaEntity(completedAt = null, sourceLabel = sourceLabel))
        }
        app.filesDir.resolve("photos").listFiles()?.forEach { if (it.isFile && it.extension == "jpg") it.delete() }
    }

    fun save(code: String, condition: Condition, note: String, photoPath: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val oldPhoto = dao.observation(code)?.photoPath
            dao.putObservation(ObservationEntity(code, condition.name, note.trim(), photoPath, System.currentTimeMillis()))
            if (oldPhoto != null && oldPhoto != photoPath) deletePhoto(oldPhoto)
            message.value = "Saved $code."
        }
    }

    fun remove(code: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val oldPhoto = dao.observation(code)?.photoPath
            dao.removeObservation(code)
            if (oldPhoto != null) deletePhoto(oldPhoto)
            message.value = "Removed the check for $code."
        }
    }

    private fun deletePhoto(path: String) {
        val root = app.filesDir.resolve("photos").canonicalFile
        val file = java.io.File(path).canonicalFile
        if (file.parentFile == root) file.delete()
    }

    fun complete() {
        viewModelScope.launch(Dispatchers.IO) {
            dao.putMeta(AuditMetaEntity(completedAt = System.currentTimeMillis(), sourceLabel = snapshot.value.sourceLabel))
            message.value = "Audit completed. Unchecked items are now listed as missing."
        }
    }

    fun reopen() {
        viewModelScope.launch(Dispatchers.IO) {
            dao.putMeta(AuditMetaEntity(completedAt = null, sourceLabel = snapshot.value.sourceLabel))
            message.value = "Audit reopened."
        }
    }

    suspend fun export(uri: Uri, current: AuditSnapshot) = withContext(Dispatchers.IO) {
        val stream = app.contentResolver.openOutputStream(uri) ?: throw IOException("Could not create the export file.")
        stream.use { ArchiveWriter.write(it, current, app.filesDir.resolve("photos")) }
    }

    fun clearMessage() { message.value = null }
}
