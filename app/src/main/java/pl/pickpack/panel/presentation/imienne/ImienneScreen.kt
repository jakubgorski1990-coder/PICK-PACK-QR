package pl.pickpack.panel.presentation.imienne

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import pl.pickpack.panel.domain.BlpptPayload
import pl.pickpack.panel.domain.ImienneGridLayout
import pl.pickpack.panel.domain.NamedQrEntry
import pl.pickpack.panel.domain.NamedStatusCountLogic
import pl.pickpack.panel.domain.NamedStatusRef
import pl.pickpack.panel.presentation.theme.WarehouseAmber
import pl.pickpack.panel.presentation.theme.WarehouseMuted
import pl.pickpack.panel.presentation.theme.WarehousePaper
import pl.pickpack.panel.presentation.theme.WarehouseSlate
import kotlin.math.min

/**
 * IMIENNE: dense name-button grid (≥20 on landscape). Tap → one large QR centered.
 * Timeout / swipe back to Pick/Pack are handled by [MainScreen].
 */
@Composable
fun ImienneScreen(
    entries: List<NamedQrEntry>,
    demoBanner: Boolean,
    encodeQr: (String) -> Bitmap?,
    modifier: Modifier = Modifier,
    namedStatuses: List<NamedStatusRef> = emptyList(),
    countsByStatusId: Map<Int, Int> = emptyMap(),
    countsReliable: Boolean = false,
) {
    var selectedId by remember { mutableStateOf<String?>(null) }
    val selected = entries.firstOrNull { it.id == selectedId }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF121820)),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 2.dp)) {
            if (namedStatuses.isNotEmpty()) {
                NamedStatusCountStrip(
                    statuses = namedStatuses,
                    countsByStatusId = countsByStatusId,
                    countsReliable = countsReliable,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
                )
            }
            if (demoBanner) {
                Text(
                    DemoContent.BANNER,
                    color = WarehouseAmber,
                    fontSize = 11.sp,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
                    textAlign = TextAlign.Center,
                )
            }
            if (entries.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Brak osób imiennych — dodaj w Ustawieniach (nazwa + template ID).",
                        color = WarehouseMuted,
                        modifier = Modifier.padding(24.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                NameButtonGrid(
                    entries = entries,
                    namedStatuses = namedStatuses,
                    countsByStatusId = countsByStatusId,
                    countsReliable = countsReliable,
                    onSelect = { selectedId = it.id },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
        }

        if (selected != null) {
            LargeQrOverlay(
                entry = selected,
                encodeQr = encodeQr,
                onDismiss = { selectedId = null },
            )
        }
    }
}

@Composable
private fun NameButtonGrid(
    entries: List<NamedQrEntry>,
    namedStatuses: List<NamedStatusRef>,
    countsByStatusId: Map<Int, Int>,
    countsReliable: Boolean,
    onSelect: (NamedQrEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val aspect = (maxWidth / maxHeight).takeIf { it.isFinite() && it > 0f } ?: (16f / 9f)
        val (cols, rows) = remember(entries.size, aspect) {
            ImienneGridLayout.buttonDimensions(entries.size, aspect)
        }
        Column(modifier = Modifier.fillMaxSize()) {
            for (r in 0 until rows) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    for (c in 0 until cols) {
                        val index = r * cols + c
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .padding(vertical = 2.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (index < entries.size) {
                                val entry = entries[index]
                                val count = if (countsReliable) {
                                    NamedStatusCountLogic.countForDisplayName(
                                        entry.displayName,
                                        namedStatuses,
                                        countsByStatusId,
                                    )
                                } else null
                                NamePersonButton(
                                    entry = entry,
                                    statusCount = count,
                                    onClick = { onSelect(entry) },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NamePersonButton(
    entry: NamedQrEntry,
    statusCount: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = buildString {
        append(entry.displayName)
        if (statusCount != null) append(" · $statusCount")
    }
    Box(
        modifier = modifier
            .background(WarehouseSlate, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LargeQrOverlay(
    entry: NamedQrEntry,
    encodeQr: (String) -> Bitmap?,
    onDismiss: () -> Unit,
) {
    val payload = remember(entry) { BlpptPayload.resolveNamed(entry) }
    val bmp = remember(payload) { payload?.let { encodeQr(it) } }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE6121820))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { /* consume — keep open */ },
                )
                .padding(16.dp),
        ) {
            Text(
                entry.displayName,
                color = WarehouseAmber,
                fontSize = 20.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            BoxWithConstraints {
                val side = min(maxWidth.value, maxHeight.value * 0.85f).coerceAtMost(520f).dp
                Box(
                    modifier = Modifier
                        .size(side)
                        .background(WarehousePaper)
                        .border(2.dp, Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = entry.displayName,
                            modifier = Modifier.fillMaxSize().padding(8.dp),
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        Text(
                            "Brak QR — ustaw template ID",
                            color = Color.DarkGray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Dotknij tła, aby zamknąć",
                color = WarehouseMuted,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun NamedStatusCountStrip(
    statuses: List<NamedStatusRef>,
    countsByStatusId: Map<Int, Int>,
    countsReliable: Boolean,
    modifier: Modifier = Modifier,
) {
    Text(
        statuses.joinToString("  ·  ") { ref ->
            val n = if (countsReliable) (countsByStatusId[ref.statusId] ?: 0).toString() else "—"
            "${ref.displayName} $n"
        },
        color = WarehouseMuted,
        fontSize = 10.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}
