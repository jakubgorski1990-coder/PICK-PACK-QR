package pl.pickpack.panel.presentation.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
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
import pl.pickpack.panel.presentation.theme.WarehouseSlate
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

    // Reset idle clock whenever user lands on / stays interacting with IMIENNE
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
            TopBar(
                clock = clock,
                snapshot = snapshot,
                undoAvailable = undoAvailable,
                onUndo = { viewModel.undo() },
                onOpenSettings = onOpenSettings,
            )

            // Main section tabs: Pick/Pack | IMIENNE (also swipeable via pager)
            TabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = WarehouseSlate,
            ) {
                Tab(
                    selected = pagerState.currentPage == PAGE_PICK_PACK,
                    onClick = { scope.launch { pagerState.animateScrollToPage(PAGE_PICK_PACK) } },
                    text = {
                        Text(
                            "Pick / Pack",
                            color = if (pagerState.currentPage == PAGE_PICK_PACK) WarehouseAmber else Color.White,
                            fontSize = 16.sp,
                        )
                    },
                )
                Tab(
                    selected = pagerState.currentPage == PAGE_IMIENNE,
                    onClick = { scope.launch { pagerState.animateScrollToPage(PAGE_IMIENNE) } },
                    text = {
                        Text(
                            "IMIENNE",
                            color = if (pagerState.currentPage == PAGE_IMIENNE) WarehouseAmber else Color.White,
                            fontSize = 16.sp,
                        )
                    },
                )
            }

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
                        )
                    }
                }
            }
        }

        message?.let { msg ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp, start = 24.dp, end = 24.dp),
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

@Composable
private fun TopBar(
    clock: LocalTime,
    snapshot: PickPackRepository.PanelSnapshot,
    undoAvailable: Boolean,
    onUndo: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = clock.format(DateTimeFormatter.ofPattern("HH:mm:ss")),
            style = MaterialTheme.typography.displayLarge,
            color = Color.White,
            fontSize = 28.sp,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            "Pick / Pack — Panel magazynu",
            color = WarehouseAmber,
            style = MaterialTheme.typography.titleLarge,
            fontSize = 18.sp,
        )
        if (snapshot.settings.demoMode) {
            Spacer(Modifier.width(8.dp))
            Text(DemoContent.BANNER, color = WarehouseAmber, fontSize = 12.sp)
        }
        Spacer(Modifier.weight(1f))
        if (snapshot.namedAlert.active) {
            Text(
                "⚠ Imienny (${snapshot.namedAlert.overdueCount})",
                color = WarehouseAmber,
                fontSize = 13.sp,
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(snapshot.connectionLabel, color = WarehouseMuted, fontSize = 13.sp)
        if (undoAvailable) {
            TextButton(onClick = onUndo) {
                Icon(Icons.Default.Undo, contentDescription = null, tint = WarehouseAmber)
                Spacer(Modifier.width(4.dp))
                Text("Cofnij", color = WarehouseAmber)
            }
        }
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Default.Settings, contentDescription = "Ustawienia", tint = Color.White)
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
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        if (codeCount == 1 && visibleGroups.size > 1) {
            TabRow(
                selectedTabIndex = selectedGroupTab.coerceIn(0, visibleGroups.lastIndex.coerceAtLeast(0)),
                containerColor = WarehouseSlate,
            ) {
                visibleGroups.forEachIndexed { index, g ->
                    Tab(
                        selected = selectedGroupTab == index,
                        onClick = { viewModel.selectGroupTab(index) },
                        text = {
                            Text(
                                g.displayName,
                                fontSize = 16.sp,
                                color = if (selectedGroupTab == index) WarehouseAmber else Color.White,
                            )
                        },
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
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

        Spacer(Modifier.height(8.dp))
        CourierBar(
            couriers = snapshot.couriers,
            onToggle = viewModel::toggleCourier,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
