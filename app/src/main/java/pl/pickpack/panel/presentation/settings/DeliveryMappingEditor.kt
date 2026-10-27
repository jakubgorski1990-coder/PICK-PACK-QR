package pl.pickpack.panel.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.pickpack.panel.domain.CourierDef
import pl.pickpack.panel.domain.DefaultCouriers
import pl.pickpack.panel.domain.DeliveryMapping
import pl.pickpack.panel.domain.PoolFilterConfig
import pl.pickpack.panel.presentation.theme.WarehouseAmber
import pl.pickpack.panel.presentation.theme.WarehouseSlate

/**
 * Per-courier delivery → courier mapping: many case-insensitive „zawiera” patterns + optional method id.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeliveryMappingEditor(
    pool: PoolFilterConfig,
    onUpdate: (PoolFilterConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            "Wiele wzorców na kuriera — match case-insensitive „zawiera” w delivery_method " +
                "(np. DHL: „DHL”, „STD_2do”). Opcjonalnie dokładne delivery_method_id. " +
                "Liczniki wariantów częściowych używają tego mapowania.",
            color = Color.Gray,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(8.dp))
        DefaultCouriers.ALL.forEach { courier ->
            CourierPatternCard(
                courier = courier,
                mapping = pool.deliveryMappings.find { it.courierId == courier.id }
                    ?: DeliveryMapping(courier.id),
                onChange = { next ->
                    onUpdate(
                        pool.copy(
                            deliveryMappings = pool.deliveryMappings
                                .filterNot { it.courierId == courier.id }
                                .let { if (next.isValid()) it + next.normalized() else it },
                        ),
                    )
                },
            )
            Spacer(Modifier.height(6.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CourierPatternCard(
    courier: CourierDef,
    mapping: DeliveryMapping,
    onChange: (DeliveryMapping) -> Unit,
) {
    var newPattern by remember(courier.id) { mutableStateOf("") }
    var methodIdText by remember(courier.id, mapping.deliveryMethodId) {
        mutableStateOf(mapping.deliveryMethodId?.toString().orEmpty())
    }
    val patterns = mapping.allNamePatterns()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(WarehouseSlate)
            .padding(10.dp),
    ) {
        Text(
            "${courier.defaultName} · ${courier.groupId.defaultName}",
            color = Color.White,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(4.dp))
        if (patterns.isEmpty()) {
            Text("Brak wzorców nazwy", color = Color.Gray, fontSize = 11.sp)
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                patterns.forEach { pattern ->
                    FilterChip(
                        selected = true,
                        onClick = { onChange(mapping.withPatternRemoved(pattern)) },
                        label = { Text("$pattern ✕", fontSize = 11.sp) },
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newPattern,
                onValueChange = { newPattern = it },
                label = { Text("Wzorzec (zawiera…)") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            Spacer(Modifier.width(6.dp))
            Button(
                onClick = {
                    if (newPattern.isNotBlank()) {
                        onChange(mapping.withPatternAdded(newPattern))
                        newPattern = ""
                    }
                },
            ) { Text("Dodaj") }
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = methodIdText,
                onValueChange = { methodIdText = it.filter { ch -> ch.isDigit() } },
                label = { Text("delivery_method_id (opcjonalnie)") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Spacer(Modifier.width(6.dp))
            TextButton(
                onClick = {
                    onChange(
                        mapping.copy(deliveryMethodId = methodIdText.toIntOrNull()).normalized(),
                    )
                },
            ) { Text("Zapisz ID") }
            TextButton(
                onClick = {
                    methodIdText = ""
                    onChange(mapping.copy(deliveryMethodId = null).normalized())
                },
            ) { Text("Clear ID") }
        }
        if (patterns.isNotEmpty() || mapping.deliveryMethodId != null) {
            Text(
                "Przykład match: „${patterns.firstOrNull() ?: mapping.deliveryMethodId}”",
                color = WarehouseAmber,
                fontSize = 10.sp,
            )
        }
    }
}
