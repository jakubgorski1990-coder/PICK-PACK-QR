package pl.pickpack.panel.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.pickpack.panel.domain.GroupId
import pl.pickpack.panel.presentation.theme.WarehouseAmber
import pl.pickpack.panel.presentation.theme.WarehouseNavy
import pl.pickpack.panel.presentation.theme.WarehouseSlate
import pl.pickpack.panel.viewmodel.MainViewModel

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenImport: () -> Unit,
    onOpenCompleteness: () -> Unit,
    onOpenBaselinker: () -> Unit,
) {
    val panel by viewModel.panel.collectAsStateWithLifecycle()
    val pinUnlocked by viewModel.pinUnlocked.collectAsStateWithLifecycle()
    val snapshot = panel ?: return

    var pinInput by remember { mutableStateOf("") }
    var showNewDayConfirm by remember { mutableStateOf(false) }
    var pinError by remember { mutableStateOf(false) }

    if (snapshot.settings.pinEnabled && !pinUnlocked) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(WarehouseNavy)
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("PIN do ustawień", color = Color.White, fontSize = 22.sp)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = pinInput,
                onValueChange = { pinInput = it; pinError = false },
                label = { Text("PIN") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                isError = pinError,
            )
            if (pinError) Text("Nieprawidłowy PIN", color = Color.Red)
            Spacer(Modifier.height(12.dp))
            Row {
                TextButton(onClick = onBack) { Text("Wróć") }
                Spacer(Modifier.width(12.dp))
                Button(onClick = {
                    viewModel.unlockWithPin(pinInput) { ok -> pinError = !ok }
                }) { Text("Odblokuj") }
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WarehouseNavy)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = {
                viewModel.lockPin()
                onBack()
            }) { Text("← Panel") }
            Text("Ustawienia", color = WarehouseAmber, fontSize = 22.sp)
        }
        Spacer(Modifier.height(8.dp))

        // ——— 1. Panel / wyświetlanie ———
        SettingsSector("1. Panel / wyświetlanie") {
            Text("Widoczne kody QR", color = Color.LightGray, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = snapshot.settings.visibleCodeCount == 1,
                    onClick = { viewModel.updateSettings { it.copy(visibleCodeCount = 1) } },
                    label = { Text("1 kod") },
                )
                FilterChip(
                    selected = snapshot.settings.visibleCodeCount == 2,
                    onClick = { viewModel.updateSettings { it.copy(visibleCodeCount = 2) } },
                    label = { Text("2 kody") },
                )
            }
            Spacer(Modifier.height(6.dp))
            snapshot.groups.sortedBy { it.orderIndex }.forEach { g ->
                var name by remember(g.id, g.displayName) { mutableStateOf(g.displayName) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(WarehouseSlate)
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(g.id.defaultName) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    Switch(
                        checked = g.visible,
                        onCheckedChange = {
                            viewModel.updateGroup(g.copy(visible = it, displayName = name))
                        },
                    )
                    Button(onClick = { viewModel.updateGroup(g.copy(displayName = name)) }) {
                        Text("Zapisz")
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            Row {
                Button(onClick = {
                    viewModel.updateSettings {
                        it.copy(groupOrder = listOf(GroupId.PICK_PACK_1, GroupId.PICK_PACK_2))
                    }
                }) { Text("Kolejność 1→2") }
                Spacer(Modifier.width(6.dp))
                Button(onClick = {
                    viewModel.updateSettings {
                        it.copy(groupOrder = listOf(GroupId.PICK_PACK_2, GroupId.PICK_PACK_1))
                    }
                }) { Text("2→1") }
            }
            Spacer(Modifier.height(4.dp))
            Text("Nazwy kurierów", color = Color.LightGray, fontSize = 12.sp)
            snapshot.couriers.forEach { c ->
                var name by remember(c.id, c.displayName) { mutableStateOf(c.displayName) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(c.id) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    Spacer(Modifier.width(6.dp))
                    Button(onClick = { viewModel.renameCourier(c.id, name) }) { Text("Zapisz") }
                }
                Spacer(Modifier.height(2.dp))
            }
            SettingSwitch(
                label = "Keep screen on",
                checked = snapshot.settings.keepScreenOn,
                onChange = { v -> viewModel.updateSettings { it.copy(keepScreenOn = v) } },
            )
            SettingSwitch(
                label = "Tryb DEMO (14 testowych QR)",
                checked = snapshot.settings.demoMode,
                onChange = { v -> viewModel.updateSettings { it.copy(demoMode = v) } },
            )
            var timeoutText by remember(snapshot.settings.imienneTimeoutSec) {
                mutableStateOf(snapshot.settings.imienneTimeoutSec.toString())
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = timeoutText,
                    onValueChange = { timeoutText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Timeout IMIENNE (s)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(160.dp),
                    singleLine = true,
                )
                Spacer(Modifier.width(6.dp))
                Button(onClick = {
                    val sec = timeoutText.toIntOrNull()?.coerceIn(5, 300) ?: 15
                    viewModel.updateSettings { it.copy(imienneTimeoutSec = sec) }
                }) { Text("Zapisz") }
            }
            Spacer(Modifier.height(4.dp))
            Text("QR imienne (osoba + template ID)", color = Color.LightGray, fontSize = 12.sp)
            var newName by remember { mutableStateOf("") }
            var newTemplate by remember { mutableStateOf("") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Imię") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                Spacer(Modifier.width(6.dp))
                OutlinedTextField(
                    value = newTemplate,
                    onValueChange = { newTemplate = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Template ID") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(120.dp),
                    singleLine = true,
                )
                Spacer(Modifier.width(6.dp))
                Button(onClick = {
                    viewModel.addNamedPerson(newName, newTemplate)
                    newName = ""; newTemplate = ""
                }) { Text("Dodaj") }
            }
            snapshot.namedEntries.filter { !it.isDemo }.forEach { entry ->
                var name by remember(entry.id, entry.displayName) { mutableStateOf(entry.displayName) }
                var tid by remember(entry.id, entry.templateId) {
                    mutableStateOf(entry.templateId?.toString().orEmpty())
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = tid,
                        onValueChange = { tid = it.filter { ch -> ch.isDigit() } },
                        modifier = Modifier.width(100.dp),
                        singleLine = true,
                    )
                    TextButton(onClick = { viewModel.moveNamedPerson(entry.id, -1) }) { Text("↑") }
                    TextButton(onClick = { viewModel.moveNamedPerson(entry.id, 1) }) { Text("↓") }
                    Button(onClick = {
                        viewModel.updateNamedPerson(
                            entry.copy(displayName = name.trim(), templateId = tid.toIntOrNull()),
                        )
                    }) { Text("Zapisz") }
                    TextButton(onClick = { viewModel.deleteNamedPerson(entry.id) }) { Text("Usuń") }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = onOpenImport) { Text("Import QR") }
                Button(onClick = onOpenCompleteness) { Text("Kompletność 14") }
            }
            Spacer(Modifier.height(4.dp))
            Text("PIN (opcjonalny)", color = Color.LightGray, fontSize = 12.sp)
            var newPin by remember { mutableStateOf("") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { newPin = it },
                    label = { Text("Nowy PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                Spacer(Modifier.width(6.dp))
                Button(onClick = {
                    viewModel.setPin(newPin.ifBlank { null })
                    newPin = ""
                }) { Text(if (newPin.isBlank()) "Wyłącz PIN" else "Ustaw PIN") }
            }
        }

        // ——— 2. BaseLinker API / sync ———
        SettingsSector("2. BaseLinker API / sync") {
            Text(
                "QR = BLPPT_ + ID (6 cyfr). Prefill: " +
                    "${GroupId.PICK_PACK_1.defaultTemplateId} / ${GroupId.PICK_PACK_2.defaultTemplateId}",
                color = Color.Gray,
                fontSize = 12.sp,
            )
            Text(
                if (snapshot.settings.baselinkerTokenPresent) "Token: zapisany (Keystore)"
                else "Token: brak",
                color = Color.LightGray,
                fontSize = 12.sp,
            )
            Text(
                "Sync: ${snapshot.syncState.orderCountCached} w cache · " +
                    (snapshot.syncState.lastError ?: "OK"),
                color = Color.Gray,
                fontSize = 11.sp,
            )
            Button(onClick = onOpenBaselinker) {
                Text("Token, statusy puli, interwał sync…")
            }
        }

        // ——— 3. Mapowanie kurierów / metody dostawy ———
        SettingsSector("3. Mapowanie kurierów / metody dostawy") {
            DeliveryMappingEditor(
                pool = snapshot.settings.poolFilter,
                onUpdate = { next -> viewModel.updatePoolFilter { next } },
            )
        }

        // ——— 4. Statusy imienne ———
        SettingsSector("4. Statusy imienne") {
            Text(
                "Licznik = ile zamówień w status_id. Alert MP3 gdy wiek > próg (date_in_status).",
                color = Color.Gray,
                fontSize = 12.sp,
            )
            val alertCfg = snapshot.settings.namedStatusAlert
            val alertUi = snapshot.namedAlert
            SettingSwitch(
                label = "Włącz alert imienny",
                checked = alertCfg.enabled,
                onChange = { v -> viewModel.updateNamedStatusAlert { it.copy(enabled = v) } },
            )
            var threshText by remember(alertCfg.thresholdMinutes) {
                mutableStateOf(alertCfg.thresholdMinutes.toString())
            }
            var coolText by remember(alertCfg.cooldownMinutes) {
                mutableStateOf(alertCfg.cooldownMinutes.toString())
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = threshText,
                    onValueChange = { threshText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Próg (min)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                Spacer(Modifier.width(6.dp))
                OutlinedTextField(
                    value = coolText,
                    onValueChange = { coolText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Cooldownoldown") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                Spacer(Modifier.width(6.dp))
                Button(onClick = {
                    viewModel.updateNamedStatusAlert {
                        it.copy(
                            thresholdMinutes = threshText.toIntOrNull()?.coerceIn(1, 24 * 60) ?: 30,
                            cooldownMinutes = coolText.toIntOrNull()?.coerceIn(1, 120) ?: 10,
                        )
                    }
                }) { Text("Zapisz") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = { viewModel.testNamedAlertSound() }) { Text("Test MP3") }
                Button(onClick = { viewModel.checkNamedAlertNow() }) { Text("Sprawdź teraz") }
            }
            Text(
                when {
                    alertUi.lastError != null -> "Check: błąd — ${alertUi.lastError}"
                    alertUi.active -> "Wskaźnik: AKTYWNY (${alertUi.overdueCount})"
                    else -> "Wskaźnik: spokój"
                },
                color = if (alertUi.active) WarehouseAmber else Color.LightGray,
                fontSize = 11.sp,
            )
            var newStatusId by remember { mutableStateOf("") }
            var newStatusName by remember { mutableStateOf("") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newStatusId,
                    onValueChange = { newStatusId = it.filter { ch -> ch.isDigit() } },
                    label = { Text("statusId") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(120.dp),
                    singleLine = true,
                )
                Spacer(Modifier.width(6.dp))
                OutlinedTextField(
                    value = newStatusName,
                    onValueChange = { newStatusName = it },
                    label = { Text("Nazwa") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                Spacer(Modifier.width(6.dp))
                Button(onClick = {
                    viewModel.upsertNamedStatus(newStatusId.toIntOrNull() ?: 0, newStatusName)
                    newStatusId = ""; newStatusName = ""
                }) { Text("Dodaj") }
            }
            alertCfg.statuses.forEach { ref ->
                var name by remember(ref.statusId, ref.displayName) { mutableStateOf(ref.displayName) }
                val countText = when {
                    !alertUi.countsReliable -> "—"
                    else -> (alertUi.countsByStatusId[ref.statusId] ?: 0).toString()
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                ) {
                    Text("${ref.statusId}", color = WarehouseAmber, modifier = Modifier.width(72.dp), fontSize = 12.sp)
                    Text(countText, color = Color.White, fontSize = 12.sp, modifier = Modifier.width(28.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    Button(onClick = { viewModel.upsertNamedStatus(ref.statusId, name) }) { Text("Zapisz") }
                    TextButton(onClick = { viewModel.deleteNamedStatus(ref.statusId) }) { Text("Usuń") }
                }
            }
        }

        // ——— 5. Godziny graniczne + alerty audio ———
        SettingsSector("5. Godziny graniczne kurierów + alerty audio") {
            SettingSwitch(
                label = "Dźwięk przy przełączaniu kuriera (Odebrane / Czeka)",
                checked = snapshot.settings.courierToggleSoundEnabled,
                onChange = { v -> viewModel.updateSettings { it.copy(courierToggleSoundEnabled = v) } },
            )
            val deadlines = snapshot.settings.courierDeadlines
            SettingSwitch(
                label = "Alerty godzin kurierów (−30 / −25 min)",
                checked = deadlines.enabled,
                onChange = { v -> viewModel.updateCourierDeadlines { it.copy(enabled = v) } },
            )
            Text(
                "Europe/Warsaw · 1× na slot/dzień · wspólna kolejka audio",
                color = Color.Gray,
                fontSize = 11.sp,
            )
            snapshot.couriers.forEach { c ->
                val d = deadlines.forCourier(c.id)
                var hh by remember(c.id, d.hour) { mutableStateOf(d.hour?.toString().orEmpty()) }
                var mm by remember(c.id, d.minute) {
                    mutableStateOf(d.minute?.toString()?.padStart(2, '0').orEmpty())
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(c.displayName, color = Color.White, modifier = Modifier.width(100.dp), fontSize = 12.sp)
                    OutlinedTextField(
                        value = hh,
                        onValueChange = { hh = it.filter { ch -> ch.isDigit() }.take(2) },
                        label = { Text("HH") },
                        modifier = Modifier.width(64.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    Text(":", color = Color.White)
                    OutlinedTextField(
                        value = mm,
                        onValueChange = { mm = it.filter { ch -> ch.isDigit() }.take(2) },
                        label = { Text("MM") },
                        modifier = Modifier.width(64.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    Spacer(Modifier.width(4.dp))
                    Button(onClick = {
                        val h = hh.toIntOrNull()
                        val m = mm.toIntOrNull() ?: 0
                        if (h != null && h in 0..23 && m in 0..59) {
                            viewModel.setCourierDeadline(c.id, h, m)
                        }
                    }) { Text("Zapisz") }
                    TextButton(onClick = {
                        hh = ""; mm = ""
                        viewModel.setCourierDeadline(c.id, null, null)
                    }) { Text("Clear") }
                    TextButton(onClick = { viewModel.testKurierSound(c.id) }) { Text("▶") }
                }
                Spacer(Modifier.height(2.dp))
            }
            SettingSwitch(
                label = "Alerty progowe (Etap 3 — stub)",
                checked = snapshot.settings.alertThresholdEnabled,
                onChange = { v -> viewModel.updateSettings { it.copy(alertThresholdEnabled = v) } },
            )
            SettingSwitch(
                label = "TTS (Etap 3 — stub)",
                checked = snapshot.settings.ttsEnabled,
                onChange = { v -> viewModel.updateSettings { it.copy(ttsEnabled = v) } },
            )
        }

        // ——— 6. Dzień / reset ———
        SettingsSector("6. Dzień / reset") {
            SettingSwitch(
                label = "Reset dzienny Europe/Warsaw (domyślnie off)",
                checked = snapshot.settings.dailyResetEnabled,
                onChange = { v -> viewModel.updateSettings { it.copy(dailyResetEnabled = v) } },
            )
            Button(onClick = { showNewDayConfirm = true }) {
                Text("Nowy dzień — wszyscy aktywni")
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    if (showNewDayConfirm) {
        AlertDialog(
            onDismissRequest = { showNewDayConfirm = false },
            title = { Text("Nowy dzień?") },
            text = { Text("Ustawić wszystkich kurierów jako „Czeka na odbiór”?") },
            confirmButton = {
                TextButton(onClick = {
                    showNewDayConfirm = false
                    viewModel.confirmNewDay()
                }) { Text("Tak") }
            },
            dismissButton = {
                TextButton(onClick = { showNewDayConfirm = false }) { Text("Anuluj") }
            },
        )
    }
}

@Composable
private fun SettingsSector(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(Color(0xFF182230))
            .padding(12.dp),
    ) {
        Text(
            title,
            color = WarehouseAmber,
            fontSize = 15.sp,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        content()
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color.White, modifier = Modifier.weight(1f), fontSize = 13.sp)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
