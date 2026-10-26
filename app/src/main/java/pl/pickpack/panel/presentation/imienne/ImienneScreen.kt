package pl.pickpack.panel.presentation.imienne

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import pl.pickpack.panel.domain.BlpptPayload
import pl.pickpack.panel.domain.ImienneGridLayout
import pl.pickpack.panel.domain.NamedQrEntry
import pl.pickpack.panel.domain.NamedStatusCountLogic
import pl.pickpack.panel.domain.NamedStatusRef
import pl.pickpack.panel.presentation.theme.WarehouseAmber
import pl.pickpack.panel.presentation.theme.WarehouseMuted
import pl.pickpack.panel.presentation.theme.WarehousePaper
import kotlin.math.min

/**
 * IMIENNE grid — QR cells maximize size; names are tiny secondary labels.
 * Fits all entries on one landscape screen (auto cols/rows).
 * Optional strip: small status_id counts for named statuses.
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
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF121820))
            .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (namedStatuses.isNotEmpty()) {
                NamedStatusCountStrip(
                    statuses = namedStatuses,
                    countsByStatusId = countsByStatusId,
                    countsReliable = countsReliable,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
                )
            }
            if (entries.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Brak kodów imiennych — dodaj osobę w Ustawieniach (nazwa + template ID).",
                        color = WarehouseMuted,
                        modifier = Modifier.padding(24.dp),
                        textAlign = TextAlign.Center,
                    )
                }
                return@Column
            }

            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val aspect = (maxWidth / maxHeight).takeIf { it.isFinite() && it > 0f } ?: (16f / 9f)
                val (cols, rows) = remember(entries.size, aspect) {
                    ImienneGridLayout.dimensions(entries.size, aspect)
                }
                val cellW = maxWidth / cols
                val cellH = maxHeight / rows
                // QR gets nearly all of the cell; leave ~14% for tiny name label
                val qrSide = min(cellW.value, cellH.value * 0.86f).dp

                Column(modifier = Modifier.fillMaxSize()) {
                    if (demoBanner) {
                        Text(
                            DemoContent.BANNER,
                            color = WarehouseAmber,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 2.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                    for (r in 0 until rows) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                        ) {
                            for (c in 0 until cols) {
                                val index = r * cols + c
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxSize()
                                        .padding(1.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (index < entries.size) {
                                        val entry = entries[index]
                                        val matched = NamedStatusCountLogic.countForDisplayName(
                                            entry.displayName,
                                            namedStatuses,
                                            countsByStatusId,
                                        )
                                        NamedQrCell(
                                            entry = entry,
                                            qrSide = qrSide,
                                            encodeQr = encodeQr,
                                            statusCount = if (countsReliable) matched else null,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
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

@Composable
private fun NamedQrCell(
    entry: NamedQrEntry,
    qrSide: androidx.compose.ui.unit.Dp,
    encodeQr: (String) -> Bitmap?,
    statusCount: Int? = null,
) {
    val payload = remember(entry) { BlpptPayload.resolveNamed(entry) }
    val bmp = remember(payload) { payload?.let { encodeQr(it) } }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(qrSide)
                .background(WarehousePaper)
                .border(1.dp, Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = entry.displayName,
                    modifier = Modifier.fillMaxSize().padding(2.dp),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Text("?", color = Color.DarkGray, fontSize = 18.sp)
            }
        }
        Text(
            buildString {
                append(entry.displayName)
                if (statusCount != null) append(" · $statusCount")
            },
            color = Color.White,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 1.dp),
        )
    }
}
