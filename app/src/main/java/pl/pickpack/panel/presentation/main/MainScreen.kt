package pl.pickpack.panel.presentation.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import pl.pickpack.panel.data.DemoContent
import pl.pickpack.panel.data.repository.PickPackRepository
import pl.pickpack.panel.domain.QrDisplayState
import pl.pickpack.panel.presentation.components.CourierBar
import pl.pickpack.panel.presentation.components.GroupQrCard
import pl.pickpack.panel.presentation.imienne.ImienneScreen
import pl.pickpack.panel.presentation.theme.WarehouseAmber
import pl.pickpack.panel.presentation.theme.WarehouseMuted
import pl.pickpack.panel.presentation.theme.WarehouseNavy
import pl.pickpack.panel.viewmodel.MainViewModel
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private const val PAGE_PICK_PACK = 0
private const val PAGE_IMIENNE = 1

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onOpenSettings: () -> Unit,
) {
    val panel by viewModel.panel.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val undoAvailable by viewModel.undoAvailable.collectAsStateWithLifecycle()
    val selectedGroupTab by viewModel.selectedGroupTab.collectAsStateWithLifecycle()
    var clock by remember { mutableStateOf(LocalTime.now()) }
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 2 })
    var lastImienneInteraction by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            clock = LocalTime.now()
            delay(1_000)
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.distinctUntilChanged().collect { page ->
            if (page == PAGE_IMIENNE) {
                lastImienneInteraction = System.currentTimeMillis()
            }
        }
    }

    val timeoutSec = (panel?.settings?.imienneTimeoutSec ?: 15).coerceIn(5, 300)
    LaunchedEffect(lastImienneInteraction, timeoutSec, pagerState.currentPage) {
        if (pagerState.currentPage != PAGE_IMIENNE) return@LaunchedEffect
        delay(timeoutSec * 1000L)
        if (pagerState.currentPage == PAGE_IMIENNE) {
            pagerState.animateScrollToPage(PAGE_PICK_PACK)
        }
    }

    val snapshot = panel
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(WarehouseNavy, Color(0xFF121820)))),
    ) {
        if (snapshot == null) {
            Text("Ładowanie…", color = Color.White, modifier = Modifier.align(Alignment.Center))
            return@Box
        }

        Column(modifier = Modifier.fillMaxSize()) {
            CompactTopBar(
                clock = clock,
                snapshot = snapshot,
                pageIndex = pagerState.currentPage,
                undoAvailable = undoAvailable,
                onUndo = { viewModel.undo() },
                onOpenSettings = onOpenSettings,
                onSelectPage = { page -> scope.launch { pagerState.animateScrollToPage(page) } },
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { page ->
                when (page) {
                    PAGE_PICK_PACK -> PickPackPage(
                        viewModel = viewModel,
                        snapshot = snapshot,
                        selectedGroupTab = selectedGroupTab,
                    )
                    PAGE_IMIENNE -> Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        awaitPointerEvent(PointerEventPass.Initial)
                                        lastImienneInteraction = System.currentTimeMillis()
                                    }
                                }
                            },
                    ) {
                        ImienneScreen(
                            entries = snapshot.namedEntries,
                            demoBanner = snapshot.namedEntries.any { it.isDemo },
                            encodeQr = viewModel::encodeQr,
                            modifier = Modifier.fillMaxSize(),
                            namedStatuses = snapshot.settings.namedStatusAlert.statuses,
                            countsByStatusId = snapshot.namedAlert.countsByStatusId,
                            countsReliable = snapshot.namedAlert.countsReliable,
                        )
                    }
                }
            }
        }

        message?.let { msg ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp, start = 16.dp, end = 16.dp),
                action = {
                    TextButton(onClick = { viewModel.consumeMessage() }) { Text("OK") }
                },
            ) { Text(msg.text) }
            LaunchedEffect(msg.id) {
                delay(2500)
                viewModel.consumeMessage()
            }
        }
    }
}

/**
 * Thin chrome: clock + connection + actions + page dots (Pick/Pack · IMIENNE).
 * No large section titles — QR gets the vertical budget.
 */
@Composable
private fun CompactTopBar(
    clock: LocalTime,
    snapshot: PickPackRepository.PanelSnapshot,
    pageIndex: Int,
    undoAvailable: Boolean,
    onUndo: () -> Unit,
    onOpenSettings: () -> Unit,
    onSelectPage: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = clock.format(DateTimeFormatter.ofPattern("HH:mm")),
            color = Color.White,
            fontSize = 14.sp,
        )
        if (snapshot.settings.demoMode) {
            Spacer(Modifier.width(6.dp))
            Text("DEMO", color = WarehouseAmber, fontSize = 11.sp)
        }
        Spacer(Modifier.width(8.dp))
        PageDots(
            pageCount = 2,
            selected = pageIndex,
            onSelect = onSelectPage,
            labels = listOf("Pick/Pack", "IMIENNE"),
        )
        Spacer(Modifier.weight(1f))
        if (snapshot.namedAlert.active) {
            Text(
                "⚠${snapshot.namedAlert.overdueCount}",
                color = WarehouseAmber,
                fontSize = 11.sp,
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(snapshot.connectionLabel, color = WarehouseMuted, fontSize = 11.sp)
        if (undoAvailable) {
            IconButton(onClick = onUndo, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Undo,
                    contentDescription = "Cofnij",
                    tint = WarehouseAmber,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        IconButton(onClick = onOpenSettings, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.Settings,
                contentDescription = "Ustawienia",
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun PageDots(
    pageCount: Int,
    selected: Int,
    onSelect: (Int) -> Unit,
    labels: List<String>,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.semantics { contentDescription = "Strona ${selected + 1} z $pageCount" },
    ) {
        repeat(pageCount) { i ->
            val active = i == selected
            Box(
                modifier = Modifier
                    .size(if (active) 8.dp else 6.dp)
                    .background(
                        if (active) WarehouseAmber else WarehouseMuted,
                        CircleShape,
                    )
                    .clickable { onSelect(i) }
                    .semantics {
                        contentDescription = labels.getOrElse(i) { "Strona ${i + 1}" }
                    },
            )
        }
    }
}

@Composable
private fun PickPackPage(
    viewModel: MainViewModel,
    snapshot: PickPackRepository.PanelSnapshot,
    selectedGroupTab: Int,
) {
    val visibleGroups = viewModel.visibleGroups(snapshot)
    val codeCount = snapshot.settings.visibleCodeCount.coerceIn(1, 2)
    val showGroups = if (codeCount == 1) {
        listOf(visibleGroups.getOrNull(selectedGroupTab) ?: visibleGroups.firstOrNull()).filterNotNull()
    } else {
        visibleGroups.take(2)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        if (codeCount == 1 && visibleGroups.size > 1) {
            // Minimal group switcher — dots + tiny labels, not a full TabRow
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 2.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                visibleGroups.forEachIndexed { index, g ->
                    val selected = selectedGroupTab == index
                    Text(
                        text = g.displayName,
                        color = if (selected) WarehouseAmber else WarehouseMuted,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .clickable { viewModel.selectGroupTab(index) }
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                    if (index < visibleGroups.lastIndex) {
                        Text("·", color = WarehouseMuted, fontSize = 11.sp)
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            showGroups.forEach { group ->
                val display = snapshot.displays[group.id] ?: QrDisplayState.AllPickedUp(group.id)
                val active = snapshot.couriers.filter { it.groupId == group.id && it.isActive }
                GroupQrCard(
                    title = group.displayName,
                    activeCouriers = active,
                    display = display,
                    counter = snapshot.counters[group.id]
                        ?: pl.pickpack.panel.domain.CounterDisplay.NotConfigured,
                    encodeQr = viewModel::encodeQr,
                    loadFallback = viewModel::loadFallback,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        CourierBar(
            couriers = snapshot.couriers,
            onToggle = viewModel::toggleCourier,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
