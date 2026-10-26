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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.pickpack.panel.data.DemoContent
import pl.pickpack.panel.domain.QrDisplayState
import pl.pickpack.panel.presentation.components.CourierBar
import pl.pickpack.panel.presentation.components.GroupQrCard
import pl.pickpack.panel.presentation.theme.WarehouseAmber
import pl.pickpack.panel.presentation.theme.WarehouseMuted
import pl.pickpack.panel.presentation.theme.WarehouseNavy
import pl.pickpack.panel.presentation.theme.WarehouseSlate
import pl.pickpack.panel.viewmodel.MainViewModel
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onOpenSettings: () -> Unit,
) {
    val panel by viewModel.panel.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val undoAvailable by viewModel.undoAvailable.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedGroupTab.collectAsStateWithLifecycle()
    var clock by remember { mutableStateOf(LocalTime.now()) }

    LaunchedEffect(Unit) {
        while (true) {
            clock = LocalTime.now()
            delay(1_000)
        }
    }

    val snapshot = panel
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(WarehouseNavy, Color(0xFF121820)))
            ),
    ) {
        if (snapshot == null) {
            Text(
                "Ładowanie…",
                color = Color.White,
                modifier = Modifier.align(Alignment.Center),
            )
            return@Box
        }

        val visibleGroups = viewModel.visibleGroups(snapshot)
        val codeCount = snapshot.settings.visibleCodeCount.coerceIn(1, 2)
        val showGroups = if (codeCount == 1) {
            listOf(visibleGroups.getOrNull(selectedTab) ?: visibleGroups.firstOrNull()).filterNotNull()
        } else {
            visibleGroups.take(2)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
        ) {
            // Top bar: clock, settings, connection
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = clock.format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                    style = MaterialTheme.typography.displayLarge,
                    color = Color.White,
                    fontSize = 32.sp,
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    "Pick / Pack — Panel magazynu",
                    color = WarehouseAmber,
                    style = MaterialTheme.typography.titleLarge,
                )
                if (snapshot.settings.demoMode) {
                    Spacer(Modifier.width(12.dp))
                    Text(DemoContent.BANNER, color = WarehouseAmber, fontSize = 13.sp)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    snapshot.connectionLabel,
                    color = WarehouseMuted,
                    fontSize = 14.sp,
                )
                if (undoAvailable) {
                    TextButton(onClick = { viewModel.undo() }) {
                        Icon(Icons.Default.Undo, contentDescription = null, tint = WarehouseAmber)
                        Spacer(Modifier.width(4.dp))
                        Text("Cofnij", color = WarehouseAmber)
                    }
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Ustawienia", tint = Color.White)
                }
            }

            if (codeCount == 1 && visibleGroups.size > 1) {
                TabRow(
                    selectedTabIndex = selectedTab.coerceIn(0, visibleGroups.lastIndex),
                    containerColor = WarehouseSlate,
                ) {
                    visibleGroups.forEachIndexed { index, g ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { viewModel.selectGroupTab(index) },
                            text = {
                                Text(
                                    g.displayName,
                                    fontSize = 18.sp,
                                    color = if (selectedTab == index) WarehouseAmber else Color.White,
                                )
                            },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                showGroups.forEach { group ->
                    val display = snapshot.displays[group.id]
                        ?: QrDisplayState.AllPickedUp(group.id)
                    val active = snapshot.couriers.filter {
                        it.groupId == group.id && it.isActive
                    }
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

            Spacer(Modifier.height(10.dp))
            CourierBar(
                couriers = snapshot.couriers,
                onToggle = viewModel::toggleCourier,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        message?.let { msg ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 88.dp, start = 24.dp, end = 24.dp),
                action = {
                    TextButton(onClick = { viewModel.consumeMessage() }) {
                        Text("OK")
                    }
                },
            ) { Text(msg.text) }
            LaunchedEffect(msg.id) {
                delay(2500)
                viewModel.consumeMessage()
            }
        }
    }
}
