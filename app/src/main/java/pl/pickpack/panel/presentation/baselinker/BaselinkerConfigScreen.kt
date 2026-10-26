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
import pl.pickpack.panel.domain.DefaultCouriers
import pl.pickpack.panel.domain.DeliveryMapping
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
    var statusIdsText by remember(pool.statusIds) {
        mutableStateOf(pool.statusIds.joinToString(","))
    }
    var intervalText by remember(pool.syncIntervalSec) {
        mutableStateOf(pool.syncIntervalSec.toString())
    }
    var statuses by remember { mutableStateOf<List<BlStatus>>(emptyList()) }
    var statusError by remember { mutableStateOf<String?>(null) }

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
        Text("Statusy puli (status_id z getOrderStatusList)", color = WarehouseAmber)
        OutlinedTextField(
            value = statusIdsText,
            onValueChange = { statusIdsText = it },
            label = { Text("ID statusów, przecinek np. 1051,1291") },
            modifier = Modifier.fillMaxWidth(),
        )
        Row {
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
            }) { Text("Pobierz listę statusów") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                val ids = statusIdsText.split(",")
                    .mapNotNull { it.trim().toIntOrNull() }
                viewModel.updatePoolFilter { it.copy(statusIds = ids) }
            }) { Text("Zapisz statusy") }
        }
        statusError?.let { Text(it, color = Color.Red, fontSize = 12.sp) }
        statuses.forEach { s ->
            FilterChip(
                selected = s.id in pool.statusIds,
                onClick = {
                    val next = if (s.id in pool.statusIds) {
                        pool.statusIds - s.id
                    } else {
                        pool.statusIds + s.id
                    }
                    statusIdsText = next.joinToString(",")
                    viewModel.updatePoolFilter { it.copy(statusIds = next) }
                },
                label = { Text("${s.id} — ${s.name}") },
                modifier = Modifier.padding(vertical = 2.dp),
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
            "delivery_method_id i/lub fragment nazwy (getOrders). Licznik = szacunek — API nie filtruje po szablonie dobierania.",
            color = Color.Gray,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(8.dp))
        DefaultCouriers.ALL.forEach { courier ->
            val existing = pool.deliveryMappings.filter { it.courierId == courier.id }
            var methodId by remember(courier.id, existing) {
                mutableStateOf(existing.firstOrNull()?.deliveryMethodId?.toString().orEmpty())
            }
            var nameContains by remember(courier.id, existing) {
                mutableStateOf(existing.firstOrNull()?.nameContains.orEmpty())
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(WarehouseSlate)
                    .padding(10.dp),
            ) {
                Text("${courier.defaultName} (${courier.groupId.defaultName})", color = Color.White)
                Row {
                    OutlinedTextField(
                        value = methodId,
                        onValueChange = { methodId = it },
                        label = { Text("delivery_method_id") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = nameContains,
                        onValueChange = { nameContains = it },
                        label = { Text("nazwa zawiera…") },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        val mapping = DeliveryMapping(
                            courierId = courier.id,
                            deliveryMethodId = methodId.toIntOrNull(),
                            nameContains = nameContains.ifBlank { null },
                        )
                        viewModel.updatePoolFilter { cfg ->
                            val others = cfg.deliveryMappings.filterNot { it.courierId == courier.id }
                            cfg.copy(
                                deliveryMappings = if (mapping.isValid()) others + mapping else others,
                            )
                        }
                    }) { Text("Zapisz") }
                }
            }
            Spacer(Modifier.height(6.dp))
        }

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
