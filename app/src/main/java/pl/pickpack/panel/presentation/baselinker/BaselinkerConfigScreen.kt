package pl.pickpack.panel.presentation.baselinker

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import pl.pickpack.panel.domain.BlStatus
import pl.pickpack.panel.domain.GroupId
import pl.pickpack.panel.presentation.settings.DeliveryMappingEditor
import pl.pickpack.panel.presentation.theme.WarehouseAmber
import pl.pickpack.panel.presentation.theme.WarehouseNavy
import pl.pickpack.panel.presentation.theme.WarehouseSlate
import pl.pickpack.panel.viewmodel.MainViewModel

@Composable
fun BaselinkerConfigScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    val panel by viewModel.panel.collectAsStateWithLifecycle()
    val snapshot = panel ?: return
    val pool = snapshot.settings.poolFilter

    var tokenInput by remember { mutableStateOf("") }
    var intervalText by remember(pool.syncIntervalSec) {
        mutableStateOf(pool.syncIntervalSec.toString())
    }
    var statuses by remember { mutableStateOf<List<BlStatus>>(emptyList()) }
    var statusError by remember { mutableStateOf<String?>(null) }
    var assigningGroup by remember { mutableStateOf(GroupId.PICK_PACK_1) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WarehouseNavy)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        TextButton(onClick = onBack) { Text("← Ustawienia") }
        Text("BaseLinker — API i liczniki", color = WarehouseAmber, fontSize = 22.sp)
        Text(
            "Read-only · ${pl.pickpack.panel.data.baselinker.BaseLinkerClient.ENDPOINT}",
            color = Color.Gray,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(12.dp))

        Text("Token API (Account → API) — Keystore, bez eksportu", color = Color.White)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = tokenInput,
                onValueChange = { tokenInput = it },
                label = {
                    Text(
                        if (viewModel.hasToken()) "Token zapisany — wklej nowy, by zamienić"
                        else "Wklej token"
                    )
                },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { viewModel.saveToken(tokenInput); tokenInput = "" }) {
                Text("Zapisz")
            }
            TextButton(onClick = { viewModel.saveToken(null); tokenInput = "" }) {
                Text("Usuń")
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Statusy puli PP1 / PP2 (osobne status_id)", color = WarehouseAmber)
        Text(
            "Pick/Pack 1 i 2 to dwa różne statusy BL. Licznik pod QR grupy = getOrders(status_id tej grupy). " +
                "Wybierz grupę, potem chip statusu → zapis + auto-sync.",
            color = Color.Gray,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(6.dp))
        GroupStatusCard(
            title = GroupId.PICK_PACK_1.defaultName,
            statusId = pool.statusIdFor(GroupId.PICK_PACK_1),
            selected = assigningGroup == GroupId.PICK_PACK_1,
            onSelectGroup = { assigningGroup = GroupId.PICK_PACK_1 },
            onClear = {
                viewModel.updatePoolFilter { it.withStatusId(GroupId.PICK_PACK_1, null) }
            },
            onManualId = { id ->
                viewModel.updatePoolFilter { it.withStatusId(GroupId.PICK_PACK_1, id) }
            },
        )
        Spacer(Modifier.height(6.dp))
        GroupStatusCard(
            title = GroupId.PICK_PACK_2.defaultName,
            statusId = pool.statusIdFor(GroupId.PICK_PACK_2),
            selected = assigningGroup == GroupId.PICK_PACK_2,
            onSelectGroup = { assigningGroup = GroupId.PICK_PACK_2 },
            onClear = {
                viewModel.updatePoolFilter { it.withStatusId(GroupId.PICK_PACK_2, null) }
            },
            onManualId = { id ->
                viewModel.updatePoolFilter { it.withStatusId(GroupId.PICK_PACK_2, id) }
            },
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            viewModel.loadStatuses { result ->
                result.onSuccess {
                    statuses = it
                    statusError = null
                }.onFailure {
                    statusError = it.message
                    statuses = emptyList()
                }
            }
        }) { Text("Pobierz listę statusów BL") }
        Text(
            "Przypisywanie chipów do: ${assigningGroup.defaultName}",
            color = WarehouseAmber,
            fontSize = 12.sp,
        )
        statusError?.let { Text(it, color = Color.Red, fontSize = 12.sp) }
        statuses.forEach { s ->
            val selectedForGroup = pool.statusIdFor(assigningGroup) == s.id
            FilterChip(
                selected = selectedForGroup,
                onClick = {
                    val next = if (selectedForGroup) null else s.id
                    viewModel.updatePoolFilter { it.withStatusId(assigningGroup, next) }
                },
                label = { Text("${s.id} — ${s.name}") },
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
        if (!pool.isGroupConfigured(GroupId.PICK_PACK_1) || !pool.isGroupConfigured(GroupId.PICK_PACK_2)) {
            Text(
                "⚠ Ustaw status_id dla obu grup (PP1 i PP2), żeby oba liczniki działały.",
                color = Color.Red,
                fontSize = 12.sp,
            )
        }

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Tylko confirmed", color = Color.White, modifier = Modifier.weight(1f))
            Switch(
                checked = pool.onlyConfirmed,
                onCheckedChange = { v -> viewModel.updatePoolFilter { it.copy(onlyConfirmed = v) } },
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = intervalText,
                onValueChange = { intervalText = it },
                label = { Text("Interwał sync (s), domyślnie 60") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(220.dp),
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                val sec = intervalText.toIntOrNull()?.coerceIn(15, 600) ?: 60
                viewModel.updatePoolFilter { it.copy(syncIntervalSec = sec) }
            }) { Text("Zapisz interwał") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { viewModel.syncNow() }) { Text("Sync teraz") }
        }

        Text(
            "Sync: ${snapshot.syncState.orderCountCached} zamówień w cache · " +
                (snapshot.syncState.lastError ?: "OK") +
                " · bez PII klientów",
            color = Color.LightGray,
            fontSize = 12.sp,
        )

        Spacer(Modifier.height(16.dp))
        Text("Mapowanie dostawy → kurier", color = WarehouseAmber)
        Text(
            "Pełna edycja wzorców także w Ustawieniach → sektor 3. " +
                "API nie filtruje po delivery — match client-side (contains).",
            color = Color.Gray,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(8.dp))
        DeliveryMappingEditor(
            pool = pool,
            onUpdate = { next -> viewModel.updatePoolFilter { next } },
        )

        Spacer(Modifier.height(12.dp))
        Text(
            "Ograniczenia API: brak metody na szablony dobierania / ich QR; " +
                "getOrderPickPackHistory ≠ licznik puli; max 100 zamówień/strona; " +
                "limit ~100 req/min. Alerty progowe (Etap 3) domyślnie off dla szacunków.",
            color = Color.Gray,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun GroupStatusCard(
    title: String,
    statusId: Int?,
    selected: Boolean,
    onSelectGroup: () -> Unit,
    onClear: () -> Unit,
    onManualId: (Int) -> Unit,
) {
    var manual by remember(statusId) { mutableStateOf(statusId?.toString().orEmpty()) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) Color(0xFF2A3A28) else WarehouseSlate)
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilterChip(
                selected = selected,
                onClick = onSelectGroup,
                label = { Text(if (selected) "Aktywne przypisanie" else "Ustaw dla tej grupy") },
            )
            Spacer(Modifier.width(8.dp))
            Text(title, color = Color.White, fontSize = 14.sp)
            Spacer(Modifier.weight(1f))
            Text(
                statusId?.let { "status_id $it" } ?: "brak",
                color = if (statusId != null) WarehouseAmber else Color.Gray,
                fontSize = 12.sp,
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = manual,
                onValueChange = { manual = it.filter { ch -> ch.isDigit() } },
                label = { Text("status_id ręcznie") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            Spacer(Modifier.width(6.dp))
            Button(onClick = {
                manual.toIntOrNull()?.let(onManualId)
            }) { Text("Zapisz") }
            TextButton(onClick = {
                manual = ""
                onClear()
            }) { Text("Clear") }
        }
    }
}

