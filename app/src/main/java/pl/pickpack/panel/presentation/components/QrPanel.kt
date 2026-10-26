package pl.pickpack.panel.presentation.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.pickpack.panel.data.DemoContent
import pl.pickpack.panel.domain.CounterDisplay
import pl.pickpack.panel.domain.CourierState
import pl.pickpack.panel.domain.MissingReason
import pl.pickpack.panel.domain.QrDisplayState
import pl.pickpack.panel.presentation.theme.WarehouseAmber
import pl.pickpack.panel.presentation.theme.WarehouseMuted
import pl.pickpack.panel.presentation.theme.WarehousePaper
import pl.pickpack.panel.presentation.theme.WarehouseRed

@Composable
fun GroupQrCard(
    title: String,
    activeCouriers: List<CourierState>,
    display: QrDisplayState,
    counter: CounterDisplay,
    encodeQr: (String) -> Bitmap?,
    loadFallback: (String?) -> Bitmap?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(Color(0xFF222C3D), RoundedCornerShape(12.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
        )
        Text(
            text = if (activeCouriers.isEmpty()) {
                "Brak aktywnych kurierów"
            } else {
                "Aktywni: " + activeCouriers.joinToString(", ") { it.displayName }
            },
            color = WarehouseMuted,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(WarehousePaper, RoundedCornerShape(8.dp))
                .padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            when (display) {
                is QrDisplayState.Ready -> {
                    val bitmap = remember(display.payload, display.fallbackImagePath) {
                        encodeQr(display.payload) ?: loadFallback(display.fallbackImagePath)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Kod QR",
                                modifier = Modifier
                                    .fillMaxSize(0.92f)
                                    .border(1.dp, Color.Black),
                                contentScale = ContentScale.Fit,
                            )
                        } else {
                            Text(
                                "Nie można wygenerować QR",
                                color = WarehouseRed,
                                textAlign = TextAlign.Center,
                            )
                        }
                        if (display.isDemo) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                DemoContent.BANNER,
                                color = WarehouseAmber,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
                is QrDisplayState.AllPickedUp -> {
                    Text(
                        text = "Wszyscy kurierzy z tej grupy odebrali paczki",
                        color = Color(0xFF1A2332),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp),
                    )
                }
                is QrDisplayState.Missing -> {
                    val reason = when (display.reason) {
                        MissingReason.NOT_CONFIGURED -> "brak konfiguracji"
                        MissingReason.DISABLED -> "wariant wyłączony"
                        MissingReason.CORRUPT -> "uszkodzony / pusty"
                    }
                    Text(
                        text = "Brak skonfigurowanego kodu dla: ${display.variantKey.label()}\n($reason)",
                        color = WarehouseRed,
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        when (counter) {
            CounterDisplay.NotConfigured -> Text(
                "Licznik nieskonfigurowany",
                color = WarehouseMuted,
                fontSize = 13.sp,
            )
            is CounterDisplay.DemoValue -> Text(
                "DEMO licznik: ${counter.waiting}" +
                    (counter.threshold?.let { " / próg $it" } ?: ""),
                color = WarehouseAmber,
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
fun CourierButton(
    courier: CourierState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (courier.isActive) Color(0xFF2F6B4F) else Color(0xFF5A3030)
    val status = if (courier.isActive) "Czeka na odbiór" else "Odebrano"
    val icon = if (courier.isActive) "●" else "○"
    androidx.compose.material3.Button(
        onClick = onClick,
        modifier = modifier.height(72.dp),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = bg),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$icon ${courier.displayName}", fontSize = 16.sp, color = Color.White)
            Text(status, fontSize = 12.sp, color = Color(0xFFE8E8E8))
        }
    }
}

@Composable
fun CourierBar(
    couriers: List<CourierState>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        couriers.forEach { c ->
            CourierButton(
                courier = c,
                onClick = { onToggle(c.id) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}
