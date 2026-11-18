package dev.musyase.pocketaudit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.musyase.pocketaudit.domain.AuditSnapshot
import dev.musyase.pocketaudit.domain.InventoryItem
import dev.musyase.pocketaudit.ui.PocketTheme
import java.time.LocalDate
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PocketTheme { PocketApp() } }
    }
}

private enum class Section(val label: String) { AUDIT("Audit"), INVENTORY("Inventory"), REPORT("Report") }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
private fun PocketApp(vm: AuditViewModel = viewModel()) {
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val ready by vm.ready.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var sectionName by rememberSaveable { mutableStateOf(Section.AUDIT.name) }
    var screen by rememberSaveable { mutableStateOf("home") }
    var selectedCode by rememberSaveable { mutableStateOf("") }
    var pendingImport by remember { mutableStateOf<List<InventoryItem>?>(null) }
    var resetPrompt by remember { mutableStateOf(false) }
    var finishPrompt by remember { mutableStateOf(false) }
    var scanError by remember { mutableStateOf<String?>(null) }
    val section = Section.valueOf(sectionName)
    val wide = LocalActivity.current?.let { calculateWindowSizeClass(it).widthSizeClass != WindowWidthSizeClass.Compact } ?: false

    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message!!)
            vm.clearMessage()
        }
    }

    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching { vm.readImport(uri) }
                .onSuccess { pendingImport = it }
                .onFailure { snackbar.showSnackbar(it.message ?: "Could not read the CSV file.") }
        }
    }
    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            runCatching { vm.export(uri, vm.snapshot.value) }
                .onSuccess { snackbar.showSnackbar("Report archive saved.") }
                .onFailure { snackbar.showSnackbar("Export failed: ${it.message}") }
        }
    }

    fun openItem(code: String) {
        val trimmed = code.trim()
        scanError = when {
            trimmed.isEmpty() -> "Enter a code to continue."
            trimmed.length > 128 || trimmed.any { it.isISOControl() } -> "Codes must be 1 to 128 printable characters."
            else -> null
        }
        if (scanError == null) {
            selectedCode = trimmed
            screen = "item"
        }
    }

    BackHandler(screen != "home") { screen = "home" }

    if (pendingImport != null) {
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Replace this audit?") },
            text = { Text("The CSV contains ${pendingImport!!.size} items. Importing it will remove the current inventory, checks, and completion state from the app. Export the current report first if you need it.") },
            confirmButton = { TextButton(onClick = { vm.import(pendingImport!!); pendingImport = null; sectionName = Section.AUDIT.name }) { Text("Import and replace") } },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Cancel") } },
        )
    }
    if (resetPrompt) {
        AlertDialog(
            onDismissRequest = { resetPrompt = false },
            title = { Text("Restore fictional sample?") },
            text = { Text("This replaces the current inventory and checks. Export your report first if you need it.") },
            confirmButton = { TextButton(onClick = { vm.resetDemo(); resetPrompt = false; sectionName = Section.AUDIT.name }) { Text("Restore sample") } },
            dismissButton = { TextButton(onClick = { resetPrompt = false }) { Text("Cancel") } },
        )
    }
    if (finishPrompt) {
        AlertDialog(
            onDismissRequest = { finishPrompt = false },
            title = { Text("Complete this audit?") },
            text = { Text("Items you have not checked will be listed as missing. You can reopen the audit later.") },
            confirmButton = { TextButton(onClick = { vm.complete(); finishPrompt = false }) { Text("Complete audit") } },
            dismissButton = { TextButton(onClick = { finishPrompt = false }) { Text("Keep checking") } },
        )
    }

    Row(Modifier.fillMaxSize()) {
        if (wide && screen == "home") {
            NavigationRail {
                Section.entries.forEach { destination ->
                    NavigationRailItem(
                        selected = destination == section,
                        onClick = { sectionName = destination.name },
                        icon = { Icon(sectionIcon(destination), contentDescription = null) },
                        label = { Text(destination.label) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                }
            }
        }
        Scaffold(
            modifier = Modifier.weight(1f),
            topBar = {
                TopAppBar(
                    title = { Text(when (screen) { "scan" -> "Scan a code"; "item" -> "Item check"; else -> section.label }) },
                    navigationIcon = {
                        if (screen != "home") IconButton(onClick = { screen = "home" }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            },
            bottomBar = {
                if (!wide && screen == "home") NavigationBar {
                    Section.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = destination == section,
                            onClick = { sectionName = destination.name },
                            icon = { Icon(sectionIcon(destination), contentDescription = null) },
                            label = { Text(destination.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        )
                    }
                }
            },
            floatingActionButton = {
                if (screen == "home" && section != Section.REPORT && snapshot.completedAt == null && ready) {
                    ExtendedFloatingActionButton(
                        onClick = { scanError = null; screen = "scan" },
                        icon = { Icon(Icons.Default.CameraAlt, contentDescription = null) },
                        text = { Text("Scan code") },
                    )
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { inner ->
            Box(Modifier.fillMaxSize().padding(inner)) {
                if (!ready) {
                    Text("Loading inventory...", Modifier.align(Alignment.Center), style = MaterialTheme.typography.bodyLarge)
                } else {
                    Column(Modifier.align(Alignment.TopCenter).widthIn(max = 860.dp).fillMaxWidth()) {
                        when (screen) {
                            "scan" -> ScanPage(scanError, onError = { scanError = it }, onCode = ::openItem)
                            "item" -> ItemPage(
                                code = selectedCode,
                                snapshot = snapshot,
                                canEdit = snapshot.completedAt == null,
                                onSave = { condition, note, photo -> vm.save(selectedCode, condition, note, photo); screen = "home" },
                                onRemove = { vm.remove(selectedCode); screen = "home" },
                            )
                            else -> when (section) {
                                Section.AUDIT -> AuditPage(snapshot, onItem = ::openItem)
                                Section.INVENTORY -> InventoryPage(snapshot, onItem = ::openItem,
                                    onImport = { importLauncher.launch(arrayOf("text/*", "application/vnd.ms-excel", "application/octet-stream")) },
                                    onReset = { resetPrompt = true })
                                Section.REPORT -> ReportPage(snapshot,
                                    onComplete = { finishPrompt = true },
                                    onReopen = vm::reopen,
                                    onExport = { exportLauncher.launch("pocket-audit-${LocalDate.now()}.zip") },
                                    onItem = ::openItem)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun sectionIcon(section: Section) = when (section) {
    Section.AUDIT -> Icons.AutoMirrored.Filled.ListAlt
    Section.INVENTORY -> Icons.Default.Inventory2
    Section.REPORT -> Icons.Default.Assessment
}
