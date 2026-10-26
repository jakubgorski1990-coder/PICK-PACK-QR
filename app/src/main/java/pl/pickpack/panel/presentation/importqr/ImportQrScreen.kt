package pl.pickpack.panel.presentation.importqr

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.pickpack.panel.domain.GroupId
import pl.pickpack.panel.domain.QrVariantKey
import pl.pickpack.panel.presentation.theme.WarehouseAmber
import pl.pickpack.panel.presentation.theme.WarehouseNavy
import pl.pickpack.panel.viewmodel.MainViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportQrScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    val panel by viewModel.panel.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var group by remember { mutableStateOf(GroupId.PICK_PACK_1) }
    var mask by remember { mutableIntStateOf(7) }
    var payload by remember { mutableStateOf("") }
    var pageIndex by remember { mutableIntStateOf(0) }
    var groupExpanded by remember { mutableStateOf(false) }
    var maskExpanded by remember { mutableStateOf(false) }

    val key = QrVariantKey(group, mask)

    val imageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        viewModel.importImage(uri, key, payload.ifBlank { null })
    }

    val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        viewModel.importPdf(uri, pageIndex, key, payload.ifBlank { null })
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        viewModel.exportConfig { json ->
            context.contentResolver.openOutputStream(uri)?.use {
                it.write(json.toByteArray(Charsets.UTF_8))
            }
        }
    }

    val importConfigLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        context.contentResolver.openInputStream(uri)?.use { input ->
            val json = input.bufferedReader().readText()
            viewModel.importConfig(json)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WarehouseNavy)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        TextButton(onClick = onBack) { Text("← Ustawienia") }
        Text("Import kodów QR", color = WarehouseAmber, fontSize = 24.sp)
        Text(
            "PNG / JPG / PDF / wklejenie treści — bez API BaseLinker",
            color = Color.Gray,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(16.dp))

        ExposedDropdownMenuBox(expanded = groupExpanded, onExpandedChange = { groupExpanded = it }) {
            OutlinedTextField(
                value = group.defaultName,
                onValueChange = {},
                readOnly = true,
                label = { Text("Grupa") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(groupExpanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = groupExpanded, onDismissRequest = { groupExpanded = false }) {
                GroupId.entries.forEach {
                    DropdownMenuItem(
                        text = { Text(it.defaultName) },
                        onClick = { group = it; groupExpanded = false },
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        ExposedDropdownMenuBox(expanded = maskExpanded, onExpandedChange = { maskExpanded = it }) {
            OutlinedTextField(
                value = "$mask — ${key.label()}",
                onValueChange = {},
                readOnly = true,
                label = { Text("Kombinacja (1–7)") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(maskExpanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = maskExpanded, onDismissRequest = { maskExpanded = false }) {
                (1..7).forEach { m ->
                    val k = QrVariantKey(group, m)
                    DropdownMenuItem(
                        text = { Text("$m — ${k.label()}") },
                        onClick = { mask = m; maskExpanded = false },
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = payload,
            onValueChange = { payload = it },
            label = { Text("Treść QR (opcjonalnie przy obrazie; wymagane przy wklejeniu)") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )

        Spacer(Modifier.height(12.dp))
        Row {
            Button(onClick = {
                imageLauncher.launch(arrayOf("image/png", "image/jpeg", "image/*"))
            }) { Text("Import PNG/JPG") }
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = pageIndex.toString(),
                onValueChange = { pageIndex = it.toIntOrNull()?.coerceAtLeast(0) ?: 0 },
                label = { Text("Strona PDF (0…)") },
                modifier = Modifier.width(140.dp),
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                pdfLauncher.launch(arrayOf("application/pdf"))
            }) { Text("Import PDF") }
        }

        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            if (payload.isNotBlank()) viewModel.importPayload(key, payload)
        }) { Text("Zapisz wklejoną treść") }

        Spacer(Modifier.height(20.dp))
        Text("Eksport / import konfiguracji (bez sekretów API)", color = WarehouseAmber)
        Spacer(Modifier.height(8.dp))
        Row {
            Button(onClick = { exportLauncher.launch("pick-pack-config.json") }) {
                Text("Eksport JSON")
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                importConfigLauncher.launch(arrayOf("application/json", "text/*"))
            }) { Text("Import JSON") }
        }

        Spacer(Modifier.height(16.dp))
        Text("Aktualne warianty produkcyjne", color = Color.White)
        panel?.variants?.filter { !it.isDemo }?.forEach { v ->
            Text(
                "${v.groupId.defaultName} mask=${v.mask} enabled=${v.enabled} " +
                    "payload=${v.payload?.take(40) ?: "—"} " +
                    "img=${v.fallbackImagePath?.let { File(it).name } ?: "—"}",
                color = Color.LightGray,
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
    }
}
