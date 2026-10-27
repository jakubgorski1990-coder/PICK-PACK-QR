package pl.pickpack.panel.presentation.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import kotlin.math.min

/**
 * Group QR card optimized for landscape: chrome is tiny; QR fills remaining space.
 */
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
            .background(Color(0xFF1A2332), RoundedCornerShape(6.dp))
            .padding(horizontal = 4.dp, vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(
                text = if (activeCouriers.isEmpty()) {
                    "—"
                } else {
                    activeCouriers.joinToString(" · ") { it.displayName }
                },
                color = WarehouseMuted,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 6.dp),
                textAlign = TextAlign.End,
            )
        }

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 2.dp)
                .background(WarehousePaper, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) {
            val side = min(maxWidth.value, maxHeight.value).dp
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
                                    .size(side)
                                    .padding(2.dp)
                                    .border(1.dp, Color.Black),
                                contentScale = ContentScale.Fit,
                            )
                        } else {
                            Text(
                                "Nie można wygenerować QR",
                                color = WarehouseRed,
                                textAlign = TextAlign.Center,
                                fontSize = 13.sp,
                            )
                        }
                        if (display.isDemo) {
                            Text(
                                DemoContent.BANNER,
                                color = WarehouseAmber,
                                fontSize = 10.sp,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
                is QrDisplayState.AllPickedUp -> {
                    Text(
                        text = "Wszyscy kurierzy z tej grupy odebrali paczki",
                        color = Color(0xFF1A2332),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(12.dp),
                    )
                }
                is QrDisplayState.Missing -> {
                    val reason = when (display.reason) {
                        MissingReason.NOT_CONFIGURED -> "brak konfiguracji"
                        MissingReason.DISABLED -> "wariant wyłączony"
                        MissingReason.CORRUPT -> "uszkodzony / pusty"
                    }
                    Text(
                        text = "Brak kodu: ${display.variantKey.label()}\n($reason)",
                        color = WarehouseRed,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(2.dp))
        when (counter) {
            CounterDisplay.NotConfigured -> Text(
                "Licznik: —",
                color = WarehouseMuted,
                fontSize = 10.sp,
            )
            is CounterDisplay.DemoValue -> Text(
                "DEMO: ${counter.waiting}" +
                    (counter.threshold?.let { " / $it" } ?: ""),
                color = WarehouseAmber,
                fontSize = 11.sp,
            )
            is CounterDisplay.Live -> Text(
                buildString {
                    append(if (counter.estimated) "~" else "")
                    append(counter.waiting)
                    counter.filterHint?.let { append(" · $it") }
                    counter.error?.let { append(" · $it") }
                },
                color = if (counter.error != null) WarehouseRed else WarehouseAmber,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
    val status = if (courier.isActive) "Czeka" else "Odebrano"
    val icon = if (courier.isActive) "●" else "○"
    androidx.compose.material3.Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 4.dp,
            vertical = 2.dp,
        ),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = bg),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "$icon ${courier.displayName}",
                fontSize = 12.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(status, fontSize = 10.sp, color = Color(0xFFE8E8E8))
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
        horizontalArrangement = Arrangement.spacedBy(4.dp),
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
