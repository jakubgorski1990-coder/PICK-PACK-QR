package pl.pickpack.panel.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.pickpack.panel.data.AppDatabase
import pl.pickpack.panel.data.ConfigExportImport
import pl.pickpack.panel.data.DemoContent
import pl.pickpack.panel.data.QrBitmapEncoder
import pl.pickpack.panel.data.alert.AlertSoundPlayer
import pl.pickpack.panel.data.baselinker.BaseLinkerClient
import pl.pickpack.panel.data.datastore.SettingsDataStore
import pl.pickpack.panel.data.entity.GroupMetaEntity
import pl.pickpack.panel.data.entity.NamedQrEntity
import pl.pickpack.panel.data.entity.QrVariantEntity
import pl.pickpack.panel.data.provider.BaselinkerOrderPoolProvider
import pl.pickpack.panel.data.provider.DemoOrderPoolProvider
import pl.pickpack.panel.data.provider.LocalQrTemplateProvider
import pl.pickpack.panel.data.provider.QrTemplateProvider
import pl.pickpack.panel.data.security.SecureTokenStore
import pl.pickpack.panel.domain.AppSettings
import pl.pickpack.panel.domain.BlStatus
import pl.pickpack.panel.domain.BlpptPayload
import pl.pickpack.panel.domain.CounterDisplay
import pl.pickpack.panel.domain.CourierState
import pl.pickpack.panel.domain.GroupConfig
import pl.pickpack.panel.domain.GroupId
import pl.pickpack.panel.domain.NamedAlertState
import pl.pickpack.panel.domain.NamedQrEntry
import pl.pickpack.panel.domain.CourierDeadlineConfig
import pl.pickpack.panel.domain.CourierDeadlineLogic
import pl.pickpack.panel.domain.NamedStatusAlertConfig
import pl.pickpack.panel.domain.NamedStatusAlertLogic
import pl.pickpack.panel.domain.NamedStatusCountLogic
import pl.pickpack.panel.domain.NamedStatusRef
import pl.pickpack.panel.domain.PoolFilterConfig
import pl.pickpack.panel.domain.PoolOrder
import pl.pickpack.panel.domain.DefaultCouriers
import pl.pickpack.panel.domain.QrDisplayState
import pl.pickpack.panel.domain.QrSelectionLogic
import pl.pickpack.panel.domain.QrVariantConfig
import pl.pickpack.panel.domain.QrVariantKey
import pl.pickpack.panel.domain.SyncState
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

class PickPackRepository(
    private val context: Context,
    private val db: AppDatabase = AppDatabase.get(context),
    private val settingsStore: SettingsDataStore = SettingsDataStore(context),
    private val tokenStore: SecureTokenStore = SecureTokenStore(context),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val qrProvider: QrTemplateProvider = LocalQrTemplateProvider(db.qrVariantDao())
    private val demoPool = DemoOrderPoolProvider()

    private val blClient = BaseLinkerClient(tokenProvider = { tokenStore.getToken() })
    @Volatile private var latestPoolFilter: PoolFilterConfig = PoolFilterConfig()
    val blPool = BaselinkerOrderPoolProvider(
        client = blClient,
        configProvider = { latestPoolFilter },
        tokenPresent = { tokenStore.hasToken() },
    )
    private val alertPlayer = AlertSoundPlayer(context)
    private val _namedAlertState = MutableStateFlow(NamedAlertState())
    val namedAlertState: StateFlow<NamedAlertState> = _namedAlertState.asStateFlow()

    val settingsFlow: Flow<AppSettings> = settingsStore.settingsFlow.map { s ->
        s.copy(
            baselinkerTokenPresent = tokenStore.hasToken(),
            counterConfigured = tokenStore.hasToken() && s.poolFilter.isPoolConfigured(),
        )
    }
    val courierStatesFlow: Flow<List<CourierState>> = settingsStore.courierStatesFlow
    val syncStateFlow: StateFlow<SyncState> = blPool.syncState

    val variantsFlow: Flow<List<QrVariantConfig>> =
        db.qrVariantDao().observeAll().map { list -> list.map { it.toDomain() } }

    val groupsFlow: Flow<List<GroupConfig>> =
        db.groupMetaDao().observeAll().map { list ->
            if (list.isEmpty()) {
                GroupId.entries.map {
                    GroupConfig(it, it.defaultName, true, it.ordinal)
                }
            } else {
                list.map {
                    GroupConfig(
                        id = GroupId.fromStorageKey(it.groupKey) ?: GroupId.PICK_PACK_1,
                        displayName = it.displayName,
                        visible = it.visible,
                        orderIndex = it.orderIndex,
                    )
                }
            }
        }

    data class PanelSnapshot(
        val settings: AppSettings,
        val couriers: List<CourierState>,
        val groups: List<GroupConfig>,
        val variants: List<QrVariantConfig>,
        val displays: Map<GroupId, QrDisplayState>,
        val counters: Map<GroupId, CounterDisplay>,
        val connectionLabel: String,
        val syncState: SyncState,
        val namedEntries: List<NamedQrEntry> = emptyList(),
        val namedAlert: NamedAlertState = NamedAlertState(),
    )

    private val namedProductionFlow: Flow<List<NamedQrEntry>> =
        db.namedQrDao().observeProduction().map { list -> list.map { it.toDomain() } }

    private val panelSource = combine(
        settingsFlow,
        courierStatesFlow,
        groupsFlow,
        variantsFlow,
        syncStateFlow,
    ) { settings, couriers, groups, variants, sync ->
        val demoMap = if (settings.demoMode) {
            DemoContent.allVariants().associateBy { it.groupId to it.mask }
        } else emptyMap()
        val prodByGroup = variants.filter { !it.isDemo }.groupBy { it.groupId }
        val displays = GroupId.entries.associateWith { groupId ->
            val active = couriers.filter { it.groupId == groupId && it.isActive }.map { it.id }.toSet()
            val map = (prodByGroup[groupId].orEmpty()).associateBy { it.mask }
            val demoForGroup = demoMap.filterKeys { it.first == groupId }
                .mapKeys { it.key.second }
            QrSelectionLogic.select(
                groupId = groupId,
                activeCourierIds = active,
                variants = map,
                demoMode = settings.demoMode,
                demoVariants = demoForGroup,
            )
        }
        val counters = GroupId.entries.associateWith { groupId ->
            buildCounter(settings, couriers, groupId, sync)
        }
        val label = when {
            settings.demoMode -> demoPool.connectionLabel()
            else -> blPool.connectionLabel()
        }
        PanelSnapshot(
            settings = settings,
            couriers = couriers,
            groups = groups,
            variants = variants,
            displays = displays,
            counters = counters,
            connectionLabel = label,
            syncState = sync,
        )
    }

    val panelFlow: StateFlow<PanelSnapshot?> =
        combine(panelSource, namedProductionFlow, namedAlertState) { snap, named, alert ->
            val displayNamed = when {
                // DEMO only when mode on and no production named entries — never silent substitute.
                snap.settings.demoMode && named.isEmpty() -> DemoContent.namedEntries()
                else -> named
            }
            snap.copy(namedEntries = displayNamed, namedAlert = alert)
        }.stateIn(scope, SharingStarted.Eagerly, null)

    init {
        scope.launch {
            settingsStore.poolFilterFlow.collect { latestPoolFilter = it }
        }
        scope.launch {
            settingsStore.seedNamedStatusesIfNeeded()
            seedTemplateIdsIfNeeded()
            maybeDailyReset()
            launch { runSyncLoop() }
            launch { runCourierDeadlineLoop() }
        }
    }

    private fun buildCounter(
        settings: AppSettings,
        couriers: List<CourierState>,
        groupId: GroupId,
        sync: SyncState,
    ): CounterDisplay {
        if (settings.demoMode) {
            val active = couriers.filter { it.groupId == groupId && it.isActive }.map { it.id }.toSet()
            val waiting = if (active.isEmpty()) 0 else {
                val base = if (groupId == GroupId.PICK_PACK_1) 18 else 12
                base + active.size * 3
            }
            return CounterDisplay.DemoValue(waiting, threshold = 25)
        }
        val pool = settings.poolFilter
        if (!settings.baselinkerTokenPresent) return CounterDisplay.NotConfigured
        val groupStatusId = pool.statusIdFor(groupId)
        if (groupStatusId == null) return CounterDisplay.NotConfigured
        val active = couriers.filter { it.groupId == groupId && it.isActive }.map { it.id }.toSet()
        val groupCourierIds = DefaultCouriers.forGroup(groupId).map { it.id }.toSet()
        val allActive = active == groupCourierIds && active.isNotEmpty()
        val variantKey = QrVariantKey.fromActiveCouriers(groupId, active)
        val orders = blPool.cachedOrders()
        val onlyConfirmed = pool.onlyConfirmed
        // Start variant: count = getOrders(status_id tej grupy).
        // Partial: te zamówienia ∩ delivery aktywnych kurierów.
        val (waiting, hint, estimated) = when {
            active.isEmpty() -> Triple(0, "brak aktywnych kurierów", true)
            sync.inProgress && orders.none { it.statusId == groupStatusId } ->
                Triple(0, "synchronizacja…", true)
            allActive || variantKey?.mask == 7 -> Triple(
                BaselinkerOrderPoolProvider.countInPoolStatuses(orders, onlyConfirmed, groupStatusId),
                "PP start: status_id $groupStatusId",
                false,
            )
            !pool.hasDeliveryMappings() -> Triple(
                0,
                "wariant filtrów: brak mapowania delivery",
                true,
            )
            else -> Triple(
                BaselinkerOrderPoolProvider.countMatching(
                    orders = orders,
                    mappings = pool.deliveryMappings,
                    activeCourierIds = active,
                    onlyConfirmed = onlyConfirmed,
                    statusId = groupStatusId,
                ),
                "wariant filtrów: delivery · status $groupStatusId",
                true,
            )
        }
        return CounterDisplay.Live(
            waiting = waiting,
            estimated = estimated,
            lastSyncEpochMs = sync.lastSuccessEpochMs,
            error = sync.lastError,
            filterHint = hint,
        )
    }

    private suspend fun runSyncLoop() {
        while (scope.isActive) {
            val settings = settingsFlow.first()
            val intervalMs = settings.poolFilter.syncIntervalSec.coerceIn(15, 600) * 1000L
            if (!settings.demoMode && settings.baselinkerTokenPresent) {
                val cfg = settings.poolFilter
                latestPoolFilter = cfg
                if (cfg.isPoolConfigured()) {
                    blPool.syncNow(force = false, config = cfg)
                }
                checkNamedStatusAlert(settings.namedStatusAlert)
            }
            delay(intervalMs)
        }
    }

    /**
     * Fetches orders for configured imienny statuses (getOrders + status_id):
     * - updates per-status_id counts (UI counters)
     * - if alert enabled: plays MP3 when any order exceeds threshold (date_in_status)
     * Errors → no sound; counts marked unreliable (never fake zeros on failure).
     */
    suspend fun checkNamedStatusAlert(cfg: NamedStatusAlertConfig? = null) {
        val effective = cfg ?: settingsFlow.first().namedStatusAlert
        if (effective.statuses.isEmpty()) {
            _namedAlertState.value = NamedAlertState(active = false)
            return
        }
        if (!tokenStore.hasToken()) {
            _namedAlertState.value = _namedAlertState.value.copy(
                lastError = "Brak tokenu",
                lastCheckEpochMs = System.currentTimeMillis(),
                countsReliable = false,
            )
            return
        }
        try {
            val collected = linkedMapOf<Long, PoolOrder>()
            for (ref in effective.statuses) {
                var idFrom: Long? = null
                var guard = 0
                while (guard++ < 30) {
                    val page = blClient.getOrders(
                        statusId = ref.statusId,
                        idFrom = idFrom,
                        getUnconfirmed = false,
                    )
                    if (page.isEmpty()) break
                    page.forEach { collected[it.orderId] = it }
                    if (page.size < 100) break
                    idFrom = page.maxOf { it.orderId }
                }
            }
            val orders = collected.values.toList()
            val counts = NamedStatusCountLogic.countsByStatusId(
                orders = orders,
                statusIds = effective.statuses.map { it.statusId },
            )
            val nowMs = System.currentTimeMillis()
            val prev = _namedAlertState.value
            if (!effective.enabled) {
                _namedAlertState.value = NamedAlertState(
                    active = false,
                    overdueCount = 0,
                    lastPlayedEpochMs = prev.lastPlayedEpochMs,
                    lastCheckEpochMs = nowMs,
                    lastError = null,
                    countsByStatusId = counts,
                    countsReliable = true,
                )
                return
            }
            val nowSec = nowMs / 1000L
            val overdue = NamedStatusAlertLogic.overdueOrders(
                orders = orders,
                namedStatusIds = effective.statuses.map { it.statusId }.toSet(),
                nowEpochSec = nowSec,
                thresholdMinutes = effective.thresholdMinutes,
            )
            val play = NamedStatusAlertLogic.shouldPlay(
                overdueCount = overdue.size,
                wasActive = prev.active,
                lastPlayedEpochMs = prev.lastPlayedEpochMs,
                nowEpochMs = nowMs,
                cooldownMinutes = effective.cooldownMinutes,
            )
            var lastPlayed = prev.lastPlayedEpochMs
            if (play) {
                withContext(Dispatchers.Main) {
                    if (alertPlayer.playUwagaImienny()) {
                        lastPlayed = nowMs
                    }
                }
            }
            _namedAlertState.value = NamedAlertState(
                active = overdue.isNotEmpty(),
                overdueCount = overdue.size,
                lastPlayedEpochMs = lastPlayed,
                lastCheckEpochMs = nowMs,
                lastError = null,
                countsByStatusId = counts,
                countsReliable = true,
            )
        } catch (e: Exception) {
            _namedAlertState.value = _namedAlertState.value.copy(
                lastCheckEpochMs = System.currentTimeMillis(),
                lastError = e.message ?: "Błąd alertu imiennego",
                countsReliable = false,
            )
        }
    }

    fun playNamedAlertTest(): Boolean = alertPlayer.playUwagaImienny()

    suspend fun updateNamedStatusAlert(transform: (NamedStatusAlertConfig) -> NamedStatusAlertConfig) {
        settingsStore.updateNamedStatusAlert(transform)
    }

    suspend fun upsertNamedStatus(ref: NamedStatusRef) {
        settingsStore.updateNamedStatusAlert { cfg ->
            val others = cfg.statuses.filterNot { it.statusId == ref.statusId }
            cfg.copy(statuses = others + ref)
        }
    }

    suspend fun deleteNamedStatus(statusId: Int) {
        settingsStore.updateNamedStatusAlert { cfg ->
            cfg.copy(statuses = cfg.statuses.filterNot { it.statusId == statusId })
        }
    }

    private suspend fun seedTemplateIdsIfNeeded() {
        val existing = db.qrVariantDao().getAll().filter { !it.isDemo }
        if (existing.any { it.templateIdHint != null }) return
        // Prefill full-set variants (mask 7) with known BL template IDs → BLPPT payload.
        GroupId.entries.forEach { group ->
            val key = QrVariantKey(group, 7)
            val config = QrVariantConfig(
                groupId = group,
                mask = 7,
                payload = null,
                enabled = true,
                isDemo = false,
                fallbackImagePath = null,
                sourceLabel = "BLPPT auto",
                templateIdHint = group.defaultTemplateId,
            )
            db.qrVariantDao().upsert(QrVariantEntity.fromDomain(config))
            // Ensure BLPPT string is what scanners expect (also stored as optional mirror).
            // Keep payload null so generator always uses template ID (admin can override).
            @Suppress("UNUSED_VARIABLE")
            val expected = BlpptPayload.fromTemplateId(group.defaultTemplateId)
        }
    }

    suspend fun toggleCourier(courierId: String) {
        val current = courierStatesFlow.currentOrDefault()
        val target = current.find { it.id == courierId } ?: return
        val nowActive = !target.isActive
        settingsStore.saveUndoSnapshot(current)
        settingsStore.setCourierStates(
            current.map { if (it.id == courierId) it.copy(isActive = nowActive) else it }
        )
        if (settingsFlow.first().courierToggleSoundEnabled) {
            alertPlayer.enqueueCourierToggle(nowActive)
        }
    }

    suspend fun undoLastCourierChange(): Boolean {
        val snapshot = settingsStore.consumeUndoSnapshot() ?: return false
        settingsStore.setCourierStates(snapshot)
        return true
    }

    suspend fun resetAllActive() {
        val current = courierStatesFlow.currentOrDefault()
        settingsStore.saveUndoSnapshot(current)
        settingsStore.setCourierStates(current.map { it.copy(isActive = true) })
        settingsStore.clearCourierDeadlinePlayed()
    }

    suspend fun updateCourierDeadlines(transform: (CourierDeadlineConfig) -> CourierDeadlineConfig) {
        settingsStore.updateCourierDeadlines(transform)
    }

    fun testKurierSound(courierId: String): Boolean =
        alertPlayer.enqueueKurier(courierId)

    /**
     * Foreground tick (~1 min): play courier MP3 at deadline−30 and deadline−25 (Warsaw).
     */
    private suspend fun runCourierDeadlineLoop() {
        while (scope.isActive) {
            try {
                tickCourierDeadlines()
            } catch (_: Exception) {
            }
            delay(30_000L) // half-minute so we don't miss the clock minute
        }
    }

    suspend fun tickCourierDeadlines() {
        val settings = settingsFlow.first()
        val cfg = settings.courierDeadlines
        if (!cfg.enabled) return
        val now = ZonedDateTime.now(CourierDeadlineLogic.WARSAW)
        val played = settingsStore.courierDeadlinePlayedFlow.first()
        val due = CourierDeadlineLogic.dueEvents(now, cfg, played)
        if (due.isEmpty()) return
        val day = now.toLocalDate()
        val keys = mutableListOf<String>()
        for ((courierId, slot) in due) {
            if (alertPlayer.enqueueKurier(courierId)) {
                keys += CourierDeadlineLogic.playKey(day, courierId, slot)
            }
        }
        settingsStore.markCourierDeadlinePlayed(keys)
    }

    suspend fun renameCourier(courierId: String, name: String) {
        val current = courierStatesFlow.currentOrDefault()
        settingsStore.setCourierStates(
            current.map { if (it.id == courierId) it.copy(displayName = name) else it }
        )
    }

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        settingsStore.updateSettings(transform)
    }

    suspend fun updatePoolFilter(transform: (PoolFilterConfig) -> PoolFilterConfig) {
        settingsStore.updatePoolFilter(transform)
        val cfg = settingsFlow.first().poolFilter
        latestPoolFilter = cfg
        // Persist status_id → immediate pool sync so PP1/PP2 counters update.
        if (tokenStore.hasToken() && cfg.isPoolConfigured()) {
            blPool.syncNow(force = true, config = cfg)
        }
    }

    suspend fun setPin(pin: String?) = settingsStore.setPin(pin)
    suspend fun verifyPin(pin: String) = settingsStore.verifyPin(pin)

    fun hasToken(): Boolean = tokenStore.hasToken()
    fun setToken(token: String?) = tokenStore.setToken(token)

    suspend fun fetchStatuses(): Result<List<BlStatus>> = runCatching {
        blClient.getOrderStatusList()
    }

    suspend fun syncNow(): Result<Int> {
        val cfg = settingsFlow.first().poolFilter
        latestPoolFilter = cfg
        return blPool.syncNow(force = true, config = cfg)
    }

    suspend fun upsertVariant(config: QrVariantConfig) = qrProvider.upsertVariant(config)

    suspend fun setVariantEnabled(key: QrVariantKey, enabled: Boolean) =
        qrProvider.setEnabled(key, enabled)

    suspend fun setTemplateId(key: QrVariantKey, templateId: Int?) {
        val existing = qrProvider.getVariant(key)
        val config = (existing ?: QrVariantConfig(
            groupId = key.groupId,
            mask = key.mask,
            payload = null,
            enabled = true,
            isDemo = false,
            fallbackImagePath = null,
            sourceLabel = "template ID",
            templateIdHint = null,
        )).copy(
            templateIdHint = templateId,
            sourceLabel = existing?.sourceLabel ?: "template ID",
            isDemo = false,
        )
        qrProvider.upsertVariant(config)
    }

    suspend fun deleteVariant(key: QrVariantKey) = qrProvider.deleteVariant(key)

    suspend fun updateGroup(meta: GroupConfig) {
        db.groupMetaDao().upsert(
            GroupMetaEntity(
                groupKey = meta.id.storageKey,
                displayName = meta.displayName,
                visible = meta.visible,
                orderIndex = meta.orderIndex,
            )
        )
    }

    suspend fun exportConfig(): String {
        val variants = qrProvider.getAllVariants()
        val groups = db.groupMetaDao().getAll().associate {
            (GroupId.fromStorageKey(it.groupKey) ?: GroupId.PICK_PACK_1) to it.displayName
        }
        val named = db.namedQrDao().getProduction().map { it.toDomain() }
        return ConfigExportImport.exportJson(variants, groups, named)
    }

    suspend fun importConfig(json: String): Int {
        val bundle = ConfigExportImport.importJson(json)
        bundle.variants.forEach { qrProvider.upsertVariant(it) }
        bundle.namedEntries.forEach { upsertNamedEntry(it) }
        return bundle.variants.size + bundle.namedEntries.size
    }

    suspend fun upsertNamedEntry(entry: NamedQrEntry) {
        val clean = entry.copy(isDemo = false)
        db.namedQrDao().upsert(NamedQrEntity.fromDomain(clean))
    }

    suspend fun deleteNamedEntry(id: String) {
        db.namedQrDao().delete(id)
    }

    suspend fun addNamedEntry(displayName: String, templateIdRaw: String, payloadOverride: String? = null) {
        val templateId = BlpptPayload.parseFlexibleTemplateId(templateIdRaw)
        val existing = db.namedQrDao().getProduction()
        val nextOrder = (existing.maxOfOrNull { it.orderIndex } ?: -1) + 1
        upsertNamedEntry(
            NamedQrEntry(
                id = UUID.randomUUID().toString(),
                displayName = displayName.trim(),
                templateId = templateId,
                payloadOverride = payloadOverride?.trim()?.takeIf { it.isNotEmpty() },
                orderIndex = nextOrder,
                isDemo = false,
            )
        )
    }

    suspend fun moveNamedEntry(id: String, delta: Int) {
        val list = db.namedQrDao().getProduction().map { it.toDomain() }.toMutableList()
        val idx = list.indexOfFirst { it.id == id }
        if (idx < 0) return
        val target = (idx + delta).coerceIn(0, list.lastIndex)
        if (target == idx) return
        val item = list.removeAt(idx)
        list.add(target, item)
        list.forEachIndexed { i, e ->
            db.namedQrDao().upsert(NamedQrEntity.fromDomain(e.copy(orderIndex = i)))
        }
    }

    suspend fun importImageAsVariant(
        uri: Uri,
        key: QrVariantKey,
        payloadOverride: String?,
        enabled: Boolean = true,
    ): QrVariantConfig = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "qr_images").also { it.mkdirs() }
        val dest = File(dir, "${key.groupId.storageKey}_${key.mask}.png")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(dest).use { output -> input.copyTo(output) }
        } ?: error("Nie można odczytać pliku")
        val existing = qrProvider.getVariant(key)
        QrVariantConfig(
            groupId = key.groupId,
            mask = key.mask,
            payload = payloadOverride?.takeIf { it.isNotBlank() },
            enabled = enabled,
            isDemo = false,
            fallbackImagePath = dest.absolutePath,
            sourceLabel = uri.lastPathSegment ?: "import",
            templateIdHint = existing?.templateIdHint
                ?: if (key.mask == 7) key.groupId.defaultTemplateId else null,
        ).also { qrProvider.upsertVariant(it) }
    }

    suspend fun importPdfPageAsVariant(
        uri: Uri,
        pageIndex: Int,
        key: QrVariantKey,
        payloadOverride: String?,
    ): QrVariantConfig = withContext(Dispatchers.IO) {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: error("Nie można otworzyć PDF")
        pfd.use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                require(pageIndex in 0 until renderer.pageCount) { "Strona poza zakresem" }
                renderer.openPage(pageIndex).use { page ->
                    val bitmap = Bitmap.createBitmap(
                        page.width * 2,
                        page.height * 2,
                        Bitmap.Config.ARGB_8888,
                    )
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    val dir = File(context.filesDir, "qr_images").also { it.mkdirs() }
                    val dest = File(dir, "${key.groupId.storageKey}_${key.mask}_pdf.png")
                    FileOutputStream(dest).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    val existing = qrProvider.getVariant(key)
                    QrVariantConfig(
                        groupId = key.groupId,
                        mask = key.mask,
                        payload = payloadOverride?.takeIf { it.isNotBlank() },
                        enabled = true,
                        isDemo = false,
                        fallbackImagePath = dest.absolutePath,
                        sourceLabel = "PDF strona ${pageIndex + 1}",
                        templateIdHint = existing?.templateIdHint
                            ?: if (key.mask == 7) key.groupId.defaultTemplateId else null,
                    ).also { qrProvider.upsertVariant(it) }
                }
            }
        }
    }

    suspend fun importPayloadText(
        key: QrVariantKey,
        payload: String,
        enabled: Boolean = true,
    ): QrVariantConfig {
        val existing = qrProvider.getVariant(key)
        val config = QrVariantConfig(
            groupId = key.groupId,
            mask = key.mask,
            payload = payload.trim(),
            enabled = enabled,
            isDemo = false,
            fallbackImagePath = existing?.fallbackImagePath,
            sourceLabel = "wklejona treść",
            templateIdHint = existing?.templateIdHint
                ?: BlpptPayload.tryParseTemplateId(payload),
        )
        qrProvider.upsertVariant(config)
        return config
    }

    fun encodeQr(payload: String, size: Int = 1024): Bitmap? = QrBitmapEncoder.encode(payload, size)

    fun loadFallbackBitmap(path: String?): Bitmap? {
        if (path.isNullOrBlank()) return null
        return BitmapFactory.decodeFile(path)
    }

    suspend fun maybeDailyReset() {
        val settings = settingsFlow.first()
        if (!settings.dailyResetEnabled) return
        val zone = ZoneId.of(settings.timezoneId)
        val now = java.time.ZonedDateTime.now(zone)
        val dayKey = LocalDate.now(zone).toString()
        if (settingsStore.lastResetDay() == dayKey) return
        if (now.hour > settings.dailyResetHour ||
            (now.hour == settings.dailyResetHour && now.minute >= settings.dailyResetMinute)
        ) {
            resetAllActive()
            settingsStore.markResetDay(dayKey)
        }
    }

    fun findDuplicatePayloads(variants: List<QrVariantConfig>): Map<String, List<QrVariantKey>> {
        return variants
            .map { it to (BlpptPayload.resolve(it) ?: it.payload?.trim()) }
            .filter { !it.second.isNullOrBlank() }
            .groupBy { it.second!! }
            .filter { it.value.size > 1 }
            .mapValues { (_, list) -> list.map { it.first.key } }
    }

    private suspend fun Flow<List<CourierState>>.currentOrDefault(): List<CourierState> =
        first().ifEmpty { SettingsDataStore.defaultCourierStates() }
}
