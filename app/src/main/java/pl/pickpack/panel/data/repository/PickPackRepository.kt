package pl.pickpack.panel.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import pl.pickpack.panel.data.AppDatabase
import pl.pickpack.panel.data.ConfigExportImport
import pl.pickpack.panel.data.DemoContent
import pl.pickpack.panel.data.QrBitmapEncoder
import pl.pickpack.panel.data.datastore.SettingsDataStore
import pl.pickpack.panel.data.entity.GroupMetaEntity
import pl.pickpack.panel.data.provider.DemoOrderPoolProvider
import pl.pickpack.panel.data.provider.LocalQrTemplateProvider
import pl.pickpack.panel.data.provider.OrderPoolProvider
import pl.pickpack.panel.data.provider.QrTemplateProvider
import pl.pickpack.panel.data.provider.StubOrderPoolProvider
import pl.pickpack.panel.domain.AppSettings
import pl.pickpack.panel.domain.CounterDisplay
import pl.pickpack.panel.domain.CourierState
import pl.pickpack.panel.domain.GroupConfig
import pl.pickpack.panel.domain.GroupId
import pl.pickpack.panel.domain.QrDisplayState
import pl.pickpack.panel.domain.QrSelectionLogic
import pl.pickpack.panel.domain.QrVariantConfig
import pl.pickpack.panel.domain.QrVariantKey
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.ZoneId

class PickPackRepository(
    private val context: Context,
    private val db: AppDatabase = AppDatabase.get(context),
    private val settingsStore: SettingsDataStore = SettingsDataStore(context),
) {
    val qrProvider: QrTemplateProvider = LocalQrTemplateProvider(db.qrVariantDao())
    private val stubPool: OrderPoolProvider = StubOrderPoolProvider()
    private val demoPool = DemoOrderPoolProvider()

    val settingsFlow: Flow<AppSettings> = settingsStore.settingsFlow
    val courierStatesFlow: Flow<List<CourierState>> = settingsStore.courierStatesFlow

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
    )

    val panelFlow: Flow<PanelSnapshot> = combine(
        settingsFlow,
        courierStatesFlow,
        groupsFlow,
        variantsFlow,
    ) { settings, couriers, groups, variants ->
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
        val pool: OrderPoolProvider = if (settings.demoMode) demoPool else stubPool
        val counters = GroupId.entries.associateWith { groupId ->
            when {
                settings.demoMode -> {
                    val active = couriers.filter { it.groupId == groupId && it.isActive }.map { it.id }.toSet()
                    // Suspend not available here — use sync demo formula
                    val waiting = run {
                        if (active.isEmpty()) 0
                        else {
                            val base = if (groupId == GroupId.PICK_PACK_1) 18 else 12
                            base + active.size * 3
                        }
                    }
                    CounterDisplay.DemoValue(waiting, threshold = 25)
                }
                !settings.counterConfigured -> CounterDisplay.NotConfigured
                else -> CounterDisplay.NotConfigured
            }
        }
        PanelSnapshot(
            settings = settings,
            couriers = couriers,
            groups = groups,
            variants = variants,
            displays = displays,
            counters = counters,
            connectionLabel = pool.connectionLabel(),
        )
    }

    suspend fun toggleCourier(courierId: String) {
        val current = courierStatesFlow.currentOrDefault()
        settingsStore.saveUndoSnapshot(current)
        val next = current.map {
            if (it.id == courierId) it.copy(isActive = !it.isActive) else it
        }
        settingsStore.setCourierStates(next)
    }

    suspend fun undoLastCourierChange(): Boolean {
        val snapshot = settingsStore.consumeUndoSnapshot() ?: return false
        settingsStore.setCourierStates(snapshot)
        return true
    }

    suspend fun resetAllActive() {
        val current = courierStatesFlow.currentOrDefault()
        settingsStore.saveUndoSnapshot(current)
        settingsStore.setCourierStates(
            current.map { it.copy(isActive = true) }
        )
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

    suspend fun setPin(pin: String?) = settingsStore.setPin(pin)

    suspend fun verifyPin(pin: String) = settingsStore.verifyPin(pin)

    suspend fun upsertVariant(config: QrVariantConfig) = qrProvider.upsertVariant(config)

    suspend fun setVariantEnabled(key: QrVariantKey, enabled: Boolean) =
        qrProvider.setEnabled(key, enabled)

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

        val payload = payloadOverride?.takeIf { it.isNotBlank() }
        QrVariantConfig(
            groupId = key.groupId,
            mask = key.mask,
            payload = payload,
            enabled = enabled,
            isDemo = false,
            fallbackImagePath = dest.absolutePath,
            sourceLabel = uri.lastPathSegment ?: "import",
            templateIdHint = if (key.mask == 7) key.groupId.defaultTemplateId else null,
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
                    QrVariantConfig(
                        groupId = key.groupId,
                        mask = key.mask,
                        payload = payloadOverride?.takeIf { it.isNotBlank() },
                        enabled = true,
                        isDemo = false,
                        fallbackImagePath = dest.absolutePath,
                        sourceLabel = "PDF strona ${pageIndex + 1}",
                        templateIdHint = if (key.mask == 7) key.groupId.defaultTemplateId else null,
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
        val config = QrVariantConfig(
            groupId = key.groupId,
            mask = key.mask,
            payload = payload.trim(),
            enabled = enabled,
            isDemo = false,
            fallbackImagePath = null,
            sourceLabel = "wklejona treść",
            templateIdHint = if (key.mask == 7) key.groupId.defaultTemplateId else null,
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
        val settings = settingsFlow.currentSettings()
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
            .filter { !it.payload.isNullOrBlank() }
            .groupBy { it.payload!!.trim() }
            .filter { it.value.size > 1 }
            .mapValues { (_, list) -> list.map { it.key } }
    }

    private suspend fun Flow<List<CourierState>>.currentOrDefault(): List<CourierState> =
        first().ifEmpty { SettingsDataStore.defaultCourierStates() }

    private suspend fun Flow<AppSettings>.currentSettings(): AppSettings = first()
}
