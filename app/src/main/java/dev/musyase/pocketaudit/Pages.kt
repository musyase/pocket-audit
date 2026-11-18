package dev.musyase.pocketaudit

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import dev.musyase.pocketaudit.domain.AuditLogic
import dev.musyase.pocketaudit.domain.AuditRow
import dev.musyase.pocketaudit.domain.AuditSnapshot
import dev.musyase.pocketaudit.domain.Condition
import dev.musyase.pocketaudit.domain.RowStatus
import dev.musyase.pocketaudit.ui.CameraScanner
import java.io.File
import java.util.UUID

@Composable
fun AuditPage(snapshot: AuditSnapshot, onItem: (String) -> Unit) {
    val summary = remember(snapshot) { AuditLogic.summary(snapshot) }
    val rows = remember(snapshot) { AuditLogic.rows(snapshot) }
    val remaining = rows.filter { it.status == RowStatus.NOT_CHECKED || it.status == RowStatus.MISSING }
    val recent = snapshot.observations.sortedByDescending { it.observedAt }.take(3)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(snapshot.sourceLabel.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(if (summary.complete) "Audit complete" else "Check what is here", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    if (summary.complete) "Unchecked items are listed as missing. Reopen the audit from Report to make changes."
                    else "Scan a label or enter its code. Each check is saved on this device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Progress", style = MaterialTheme.typography.titleMedium)
                        Text("${summary.checked} / ${summary.expected}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    val fraction = if (summary.expected == 0) 0f else summary.checked.toFloat() / summary.expected
                    Box(
                        Modifier.fillMaxWidth().height(8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    ) {
                        Box(
                            Modifier.fillMaxWidth(fraction).height(8.dp)
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Metric(summary.remaining.toString(), if (summary.complete) "Missing" else "To check", Modifier.weight(1f))
                        Metric(summary.attention.toString(), "Needs attention", Modifier.weight(1f))
                        Metric(summary.unexpected.toString(), "Unexpected", Modifier.weight(1f))
                    }
                }
            }
        }
        item { SectionHeading(if (summary.complete) "Missing items" else "Still to check", "${remaining.size} items") }
        if (remaining.isEmpty()) item {
            Text("Every expected item has a check. Review any issues in Report.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(remaining.take(6), key = { "remaining-${it.code}" }) { row -> AuditItemRow(row, onItem) }
        if (remaining.size > 6) item { Text("${remaining.size - 6} more in Inventory", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (recent.isNotEmpty()) {
            item { SectionHeading("Recent checks", "Saved locally") }
            items(recent, key = { "recent-${it.code}" }) { observation ->
                rows.firstOrNull { it.code == observation.code }?.let { AuditItemRow(it, onItem) }
            }
        }
    }
}

@Composable
fun InventoryPage(snapshot: AuditSnapshot, onItem: (String) -> Unit, onImport: () -> Unit, onReset: () -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    val rows = remember(snapshot) { AuditLogic.rows(snapshot) }
    val filtered = rows.filter {
        search.isBlank() || it.code.contains(search, true) || it.item?.name?.contains(search, true) == true || it.item?.location?.contains(search, true) == true
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Expected inventory", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Text("${snapshot.items.size} items from ${snapshot.sourceLabel.lowercase()}. Importing a new list replaces this audit.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onImport, modifier = Modifier.weight(1f).height(50.dp)) {
                    Icon(Icons.Default.FileUpload, contentDescription = null)
                    Text("  Import CSV")
                }
                OutlinedButton(onClick = onReset, modifier = Modifier.height(50.dp)) {
                    Icon(Icons.Default.RestartAlt, contentDescription = "Restore sample")
                    Text("  Sample")
                }
            }
        }
        item {
            OutlinedTextField(
                value = search, onValueChange = { search = it },
                label = { Text("Find code, item, or location") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
        }
        item { SectionHeading("Items", "${filtered.size} shown") }
        if (filtered.isEmpty()) item { Text("No items match this search.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(filtered, key = { it.code }) { row -> AuditItemRow(row, onItem) }
    }
}

@Composable
fun ReportPage(snapshot: AuditSnapshot, onComplete: () -> Unit, onReopen: () -> Unit, onExport: () -> Unit, onItem: (String) -> Unit) {
    val summary = remember(snapshot) { AuditLogic.summary(snapshot) }
    val rows = remember(snapshot) { AuditLogic.rows(snapshot) }
    val ordered = rows.sortedWith(compareBy({ when (it.status) {
        RowStatus.ATTENTION -> 0; RowStatus.UNEXPECTED -> 1; RowStatus.MISSING -> 2;
        RowStatus.NOT_CHECKED -> 3; RowStatus.CHECKED -> 4
    } }, { it.item?.location ?: "" }, { it.code }))
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Audit report", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    if (summary.complete) "Completed. ${summary.remaining} expected items were not found."
                    else "In progress. ${summary.remaining} expected items have not been checked yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Metric(summary.checked.toString(), "Checked", Modifier.weight(1f))
                Metric(summary.attention.toString(), "Damaged / repair", Modifier.weight(1f))
                Metric(summary.unexpected.toString(), "Unexpected", Modifier.weight(1f))
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onExport, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Default.FileDownload, contentDescription = null)
                    Text("  Export report ZIP")
                }
                if (summary.complete) OutlinedButton(onClick = onReopen, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Reopen audit") }
                else OutlinedButton(onClick = onComplete, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Complete audit and mark missing") }
                Text("The ZIP contains a CSV report, attached photos, and SHA-256 checksums. Checksums detect file changes, not authorship.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { SectionHeading("All results", "Issues first") }
        items(ordered, key = { it.code }) { row -> AuditItemRow(row, onItem) }
    }
}

@Composable
private fun SectionHeading(title: String, detail: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(detail, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Metric(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AuditItemRow(row: AuditRow, onItem: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().clickable { onItem(row.code) }.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(row.item?.name ?: "Unlisted code", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${row.code}  ·  ${row.item?.location ?: "Not in inventory"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            StatusTag(row.status)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    }
}

@Composable
private fun StatusTag(status: RowStatus) {
    val colors = MaterialTheme.colorScheme
    val (label, background, foreground) = when (status) {
        RowStatus.CHECKED -> Triple("Checked", colors.primaryContainer, colors.onPrimaryContainer)
        RowStatus.ATTENTION -> Triple("Attention", Color(0xFFFFE0B2), Color(0xFF603A00))
        RowStatus.UNEXPECTED -> Triple("Unexpected", colors.secondaryContainer, colors.onSurface)
        RowStatus.NOT_CHECKED -> Triple("Not checked", colors.surfaceVariant, colors.onSurfaceVariant)
        RowStatus.MISSING -> Triple("Missing", colors.error.copy(alpha = 0.15f), colors.error)
    }
    Surface(color = background, shape = RoundedCornerShape(8.dp)) {
        Text(label, Modifier.padding(horizontal = 9.dp, vertical = 6.dp), color = foreground, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun ScanPage(error: String?, onError: (String) -> Unit, onCode: (String) -> Unit) {
    val context = LocalContext.current
    var permitted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var code by rememberSaveable { mutableStateOf("") }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permitted = granted
        if (!granted) onError("Camera permission was not granted. You can still enter a code.")
    }
    Column(
        Modifier.fillMaxSize().imePadding().padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Point the camera at a QR or barcode label. Scanning runs on this device.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (permitted) CameraScanner(onCode = onCode, onError = onError)
        else Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Column(Modifier.fillMaxWidth().height(190.dp).padding(20.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Camera is optional", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { permission.launch(Manifest.permission.CAMERA) }) { Text("Enable camera") }
            }
        }
        Text("Or enter a code", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = code, onValueChange = { code = it },
            label = { Text("Item code") },
            placeholder = { Text("For example LAB-101") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onCode(code) }),
            modifier = Modifier.fillMaxWidth(),
        )
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        Button(onClick = { onCode(code) }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Open item") }
    }
}

@Composable
fun ItemPage(code: String, snapshot: AuditSnapshot, canEdit: Boolean, onSave: (Condition, String, String?) -> Unit, onRemove: () -> Unit) {
    val context = LocalContext.current
    val item = snapshot.items.firstOrNull { it.code == code }
    val existing = snapshot.observations.firstOrNull { it.code == code }
    var conditionName by rememberSaveable(code) { mutableStateOf(existing?.condition?.name ?: Condition.GOOD.name) }
    var note by rememberSaveable(code) { mutableStateOf(existing?.note.orEmpty()) }
    var photoPath by rememberSaveable(code) { mutableStateOf(existing?.photoPath) }
    var pendingPhoto by rememberSaveable(code) { mutableStateOf<String?>(null) }
    var photoError by remember { mutableStateOf<String?>(null) }
    var submitted by remember(code) { mutableStateOf(false) }
    DisposableEffect(code) {
        onDispose {
            if (!submitted) {
                photoPath?.takeIf { it != existing?.photoPath }?.let { File(it).delete() }
                pendingPhoto?.let { File(it).delete() }
            }
        }
    }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
        if (captured) {
            photoPath?.takeIf { it != existing?.photoPath }?.let { File(it).delete() }
            photoPath = pendingPhoto
        }
        else pendingPhoto?.let { File(it).delete() }
        pendingPhoto = null
    }
    val launchPhoto = {
        try {
            photoError = null
            val folder = File(context.filesDir, "photos").apply { mkdirs() }
            val file = File(folder, "${UUID.randomUUID()}.jpg")
            pendingPhoto = file.absolutePath
            takePhoto.launch(FileProvider.getUriForFile(context, "${context.packageName}.files", file))
        } catch (_: Exception) {
            pendingPhoto?.let { File(it).delete() }
            pendingPhoto = null
            photoError = "Camera unavailable. You can save the check without a photo."
        }
    }
    val requestPhotoPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchPhoto()
        else photoError = "Camera access is needed to attach a photo."
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(code, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(item?.name ?: "Unexpected item", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Text(item?.location ?: "This code is not in the expected inventory.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (existing != null) Text("This code was checked before. Saving updates that check.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                if (!canEdit) Text("This audit is complete. Reopen it from Report to edit checks.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("Condition", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Condition.entries.forEach { condition ->
                        FilterChip(
                            selected = conditionName == condition.name,
                            onClick = { conditionName = condition.name },
                            enabled = canEdit,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                            label = { Text(when (condition) { Condition.GOOD -> "Good"; Condition.DAMAGED -> "Damaged"; Condition.NEEDS_REPAIR -> "Repair" }) },
                        )
                    }
                }
            }
        }
        item {
            OutlinedTextField(
                value = note, onValueChange = { if (it.length <= 500) note = it },
                enabled = canEdit, label = { Text("Note (optional)") },
                supportingText = { Text("${note.length}/500") },
                minLines = 3, maxLines = 5, modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Photo (optional)", style = MaterialTheme.typography.titleMedium)
                val bitmap = remember(photoPath) {
                    photoPath?.let { BitmapFactory.decodeFile(it, BitmapFactory.Options().apply { inSampleSize = 4 }) }
                }
                if (bitmap != null) Image(bitmap.asImageBitmap(), contentDescription = "Attached item photo", contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(14.dp)))
                if (canEdit) OutlinedButton(onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launchPhoto()
                    else requestPhotoPermission.launch(Manifest.permission.CAMERA)
                }, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                    Text(if (bitmap == null) "  Take photo" else "  Replace photo")
                }
                if (photoError != null) Text(photoError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (canEdit) item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { submitted = true; onSave(Condition.valueOf(conditionName), note, photoPath) }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Save check") }
                if (existing != null) TextButton(onClick = onRemove, modifier = Modifier.fillMaxWidth()) { Text("Remove this check") }
            }
        }
    }
}
