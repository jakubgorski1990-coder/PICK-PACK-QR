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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
import pl.pickpack.panel.data.baselinker.BaseLinkerClient
import pl.pickpack.panel.data.datastore.SettingsDataStore
import pl.pickpack.panel.data.entity.GroupMetaEntity
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
import pl.pickpack.panel.domain.PoolFilterConfig
import pl.pickpack.panel.domain.QrDisplayState
import pl.pickpack.panel.domain.QrSelectionLogic
import pl.pickpack.panel.domain.QrVariantConfig
import pl.pickpack.panel.domain.QrVariantKey
import pl.pickpack.panel.domain.SyncState
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.ZoneId

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

    val settingsFlow: Flow<AppSettings> = settingsStore.settingsFlow.map { s ->
        s.copy(
            baselinkerTokenPresent = tokenStore.hasToken(),
            counterConfigured = tokenStore.hasToken() && s.poolFilter.isCounterRulesReady(),
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
    )

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
        panelSource.stateIn(scope, SharingStarted.Eagerly, null)

    init {
        scope.launch {
            settingsStore.poolFilterFlow.collect { latestPoolFilter = it }
        }
        scope.launch {
            seedTemplateIdsIfNeeded()
            maybeDailyReset()
            runSyncLoop()
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
        val configured = settings.baselinkerTokenPresent && settings.poolFilter.isCounterRulesReady()
        if (!configured) return CounterDisplay.NotConfigured
        val active = couriers.filter { it.groupId == groupId && it.isActive }.map { it.id }.toSet()
        val waiting = BaselinkerOrderPoolProvider.countMatching(
            orders = blPool.cachedOrders(),
            mappings = settings.poolFilter.deliveryMappings,
            activeCourierIds = active,
            onlyConfirmed = settings.poolFilter.onlyConfirmed,
        )
        return CounterDisplay.Live(
            waiting = waiting,
            estimated = true,
            lastSyncEpochMs = sync.lastSuccessEpochMs,
            error = sync.lastError,
        )
    }

    private suspend fun runSyncLoop() {
        while (scope.isActive) {
            val settings = settingsFlow.first()
            val intervalMs = settings.poolFilter.syncIntervalSec.coerceIn(15, 600) * 1000L
            if (!settings.demoMode &&
                settings.baselinkerTokenPresent &&
                settings.poolFilter.isCounterRulesReady()
            ) {
                blPool.syncNow(force = false)
            }
            delay(intervalMs)
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
        settingsStore.saveUndoSnapshot(current)
        settingsStore.setCourierStates(
            current.map { if (it.id == courierId) it.copy(isActive = !it.isActive) else it }
        )
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
    }

    suspend fun setPin(pin: String?) = settingsStore.setPin(pin)
    suspend fun verifyPin(pin: String) = settingsStore.verifyPin(pin)

    fun hasToken(): Boolean = tokenStore.hasToken()
    fun setToken(token: String?) = tokenStore.setToken(token)

    suspend fun fetchStatuses(): Result<List<BlStatus>> = runCatching {
        blClient.getOrderStatusList()
    }

    suspend fun syncNow(): Result<Int> = blPool.syncNow(force = true)

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
        // Secrets never included — ConfigExportImport already skips tokens.
        return ConfigExportImport.exportJson(variants, groups)
    }

    suspend fun importConfig(json: String): Int {
        val imported = ConfigExportImport.importJson(json)
        imported.forEach { qrProvider.upsertVariant(it) }
        return imported.size
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
