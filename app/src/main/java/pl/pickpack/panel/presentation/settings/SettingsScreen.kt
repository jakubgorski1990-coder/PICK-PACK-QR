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
                    viewModel.unlockWithPin(pinInput) { ok ->
                        pinError = !ok
                    }
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
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = {
                viewModel.lockPin()
                onBack()
            }) { Text("← Panel") }
            Text("Ustawienia", color = WarehouseAmber, fontSize = 24.sp)
        }
        Spacer(Modifier.height(16.dp))

        SectionTitle("Widoczne kody QR")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = snapshot.settings.visibleCodeCount == 1,
                onClick = { viewModel.updateSettings { it.copy(visibleCodeCount = 1) } },
                label = { Text("1 kod (zakładki)") },
            )
            FilterChip(
                selected = snapshot.settings.visibleCodeCount == 2,
                onClick = { viewModel.updateSettings { it.copy(visibleCodeCount = 2) } },
                label = { Text("2 kody obok siebie") },
            )
        }

        Spacer(Modifier.height(12.dp))
        SectionTitle("Kolejność / widoczność grup")
        snapshot.groups.sortedBy { it.orderIndex }.forEach { g ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(WarehouseSlate)
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                var name by remember(g.id, g.displayName) { mutableStateOf(g.displayName) }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(g.id.defaultName) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                Spacer(Modifier.width(8.dp))
                Text("Widoczna", color = Color.White)
                Switch(
                    checked = g.visible,
                    onCheckedChange = {
                        viewModel.updateGroup(g.copy(visible = it, displayName = name))
                    },
                )
                Button(onClick = {
                    viewModel.updateGroup(g.copy(displayName = name))
                }) { Text("Zapisz") }
            }
            Spacer(Modifier.height(6.dp))
        }
        Row {
            Button(onClick = {
                viewModel.updateSettings {
                    it.copy(groupOrder = listOf(GroupId.PICK_PACK_1, GroupId.PICK_PACK_2))
                }
            }) { Text("Kolejność: 1 → 2") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                viewModel.updateSettings {
                    it.copy(groupOrder = listOf(GroupId.PICK_PACK_2, GroupId.PICK_PACK_1))
                }
            }) { Text("Kolejność: 2 → 1") }
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle("Kurierzy (nazwy)")
        snapshot.couriers.forEach { c ->
            var name by remember(c.id, c.displayName) { mutableStateOf(c.displayName) }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(c.id) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = { viewModel.renameCourier(c.id, name) }) { Text("Zapisz") }
            }
            Spacer(Modifier.height(4.dp))
        }

        Spacer(Modifier.height(16.dp))
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
        SettingSwitch(
            label = "Reset dzienny Europe/Warsaw (domyślnie off)",
            checked = snapshot.settings.dailyResetEnabled,
            onChange = { v -> viewModel.updateSettings { it.copy(dailyResetEnabled = v) } },
        )
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

        Spacer(Modifier.height(12.dp))
        SectionTitle("PIN (opcjonalny)")
        var newPin by remember { mutableStateOf("") }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newPin,
                onValueChange = { newPin = it },
                label = { Text("Nowy PIN") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { viewModel.setPin(newPin.ifBlank { null }); newPin = "" }) {
                Text(if (newPin.isBlank()) "Wyłącz PIN" else "Ustaw PIN")
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle("BaseLinker")
        Text(
            "QR = BLPPT_ + ID (6 cyfr). Prefill pełnych zestawów: " +
                "${GroupId.PICK_PACK_1.defaultTemplateId} / ${GroupId.PICK_PACK_2.defaultTemplateId}",
            color = Color.White,
            fontSize = 13.sp,
        )
        Text(
            if (snapshot.settings.baselinkerTokenPresent) "Token: zapisany (Keystore)"
            else "Token: brak",
            color = Color.LightGray,
            fontSize = 13.sp,
        )
        Button(onClick = onOpenBaselinker) { Text("Konfiguracja API / filtrów / sync") }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { showNewDayConfirm = true }) { Text("Nowy dzień — wszyscy aktywni") }
            Button(onClick = onOpenImport) { Text("Import QR") }
            Button(onClick = onOpenCompleteness) { Text("Kompletność 14 + template ID") }
        }
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
private fun SectionTitle(text: String) {
    Text(text, color = WarehouseAmber, fontSize = 18.sp, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color.White, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
