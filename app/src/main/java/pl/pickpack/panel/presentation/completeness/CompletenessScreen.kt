package pl.pickpack.panel.presentation.completeness

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.pickpack.panel.data.DemoContent
import pl.pickpack.panel.domain.BlpptPayload
import pl.pickpack.panel.domain.QrSelectionLogic
import pl.pickpack.panel.domain.QrVariantConfig
import pl.pickpack.panel.domain.QrVariantKey
import pl.pickpack.panel.presentation.theme.WarehouseAmber
import pl.pickpack.panel.presentation.theme.WarehouseGreen
import pl.pickpack.panel.presentation.theme.WarehouseNavy
import pl.pickpack.panel.presentation.theme.WarehouseRed
import pl.pickpack.panel.presentation.theme.WarehouseSlate
import pl.pickpack.panel.viewmodel.MainViewModel

@Composable
fun CompletenessScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    val panel by viewModel.panel.collectAsStateWithLifecycle()
    val snapshot = panel ?: return
    val keys = QrSelectionLogic.allVariantKeys()
    val byKey = snapshot.variants.filter { !it.isDemo }.associateBy { it.key }
    val demoByKey = if (snapshot.settings.demoMode) {
        DemoContent.allVariants().associateBy { it.key }
    } else emptyMap()
    val duplicates = viewModel.duplicatePayloads(snapshot.variants.filter { !it.isDemo })
    var preview by remember { mutableStateOf<QrVariantConfig?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WarehouseNavy)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Ustawienia") }
            Text("Kompletność 14 wariantów", color = WarehouseAmber, fontSize = 22.sp)
        }
        val readyCount = keys.count { key ->
            val c = byKey[key] ?: return@count false
            c.enabled && (!BlpptPayload.resolve(c).isNullOrBlank() || !c.fallbackImagePath.isNullOrBlank())
        }
        Text(
            "Gotowe: $readyCount/14 (template ID → BLPPT_…)" +
                if (snapshot.settings.demoMode) " · DEMO aktywne (nie zastępuje braków produkcyjnych)" else "",
            color = Color.White,
            fontSize = 14.sp,
        )
        if (duplicates.isNotEmpty()) {
            Text(
                "⚠ Identyczna treść w wielu wariantach: " +
                    duplicates.entries.joinToString("; ") { (payload, keysDup) ->
                        "„${payload.take(24)}…” → " + keysDup.joinToString { "${it.groupId.storageKey}:${it.mask}" }
                    },
                color = WarehouseAmber,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(keys, key = { "${it.groupId.storageKey}:${it.mask}" }) { key ->
                val config = byKey[key]
                val resolved = config?.let { BlpptPayload.resolve(it) }
                val ok = config != null && config.enabled &&
                    (!resolved.isNullOrBlank() || !config.fallbackImagePath.isNullOrBlank())
                var templateText by remember(key, config?.templateIdHint) {
                    mutableStateOf(config?.templateIdHint?.toString().orEmpty())
                }
                Column(
                    modifier = Modifier
                        .background(WarehouseSlate, RoundedCornerShape(8.dp))
                        .padding(10.dp),
                ) {
                    Text(
                        key.groupId.defaultName,
                        color = WarehouseAmber,
                        fontSize = 13.sp,
                    )
                    Text(key.label(), color = Color.White, fontSize = 14.sp)
                    Text(
                        when {
                            ok -> "OK · ${resolved ?: "obraz"}"
                            config != null && !config.enabled -> "WYŁĄCZONY"
                            else -> "BRAK template ID"
                        },
                        color = when {
                            ok -> WarehouseGreen
                            config != null && !config.enabled -> WarehouseAmber
                            else -> WarehouseRed
                        },
                        fontSize = 12.sp,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = templateText,
                            onValueChange = { templateText = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Template ID") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        Spacer(Modifier.width(6.dp))
                        Button(onClick = {
                            viewModel.setTemplateId(key, templateText.toIntOrNull())
                        }) { Text("ID") }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (config != null) {
                            Text("Włącz", color = Color.White, fontSize = 12.sp)
                            Switch(
                                checked = config.enabled,
                                onCheckedChange = { viewModel.setVariantEnabled(key, it) },
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Button(
                            onClick = {
                                preview = config
                                    ?: demoByKey[key]
                                    ?: QrVariantConfig(
                                        groupId = key.groupId,
                                        mask = key.mask,
                                        payload = null,
                                        enabled = false,
                                        isDemo = false,
                                        fallbackImagePath = null,
                                        sourceLabel = null,
                                        templateIdHint = templateText.toIntOrNull(),
                                    )
                            },
                        ) { Text("Podgląd") }
                    }
                }
            }
        }
    }

    preview?.let { cfg ->
        PreviewDialog(
            config = cfg,
            encodeQr = viewModel::encodeQr,
            loadFallback = viewModel::loadFallback,
            onDismiss = { preview = null },
            onDelete = {
                viewModel.deleteVariant(QrVariantKey(cfg.groupId, cfg.mask))
                preview = null
            },
        )
    }
}

@Composable
private fun PreviewDialog(
    config: QrVariantConfig,
    encodeQr: (String) -> Bitmap?,
    loadFallback: (String?) -> Bitmap?,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .background(WarehouseSlate, RoundedCornerShape(12.dp))
                .padding(16.dp)
                .width(360.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "${config.groupId.defaultName}: ${QrVariantKey(config.groupId, config.mask).label()}",
                color = Color.White,
            )
            Spacer(Modifier.height(8.dp))
            val bmp = remember(config) {
                BlpptPayload.resolve(config)?.let { encodeQr(it) }
                    ?: loadFallback(config.fallbackImagePath)
            }
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(240.dp),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Text("Brak obrazu / treści", color = WarehouseRed)
            }
            if (config.isDemo) {
                Text(DemoContent.BANNER, color = WarehouseAmber, fontSize = 12.sp)
            }
            Spacer(Modifier.height(8.dp))
            Row {
                TextButton(onClick = onDismiss) { Text("Zamknij") }
                if (!config.isDemo && (config.payload != null || config.fallbackImagePath != null)) {
                    TextButton(onClick = onDelete) { Text("Usuń") }
                }
            }
        }
    }
}
