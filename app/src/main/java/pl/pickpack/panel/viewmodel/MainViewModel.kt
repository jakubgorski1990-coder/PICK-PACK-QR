package pl.pickpack.panel.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pl.pickpack.panel.PickPackApplication
import pl.pickpack.panel.data.repository.PickPackRepository
import pl.pickpack.panel.domain.AppSettings
import pl.pickpack.panel.domain.BlStatus
import pl.pickpack.panel.domain.GroupConfig
import pl.pickpack.panel.domain.PoolFilterConfig
import pl.pickpack.panel.domain.QrVariantConfig
import pl.pickpack.panel.domain.QrVariantKey

data class UiMessage(val text: String, val id: Long = System.currentTimeMillis())

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repo: PickPackRepository =
        (application as PickPackApplication).repository

    val panel: StateFlow<PickPackRepository.PanelSnapshot?> = repo.panelFlow

    private val _message = MutableStateFlow<UiMessage?>(null)
    val message: StateFlow<UiMessage?> = _message.asStateFlow()

    private val _undoAvailable = MutableStateFlow(false)
    val undoAvailable: StateFlow<Boolean> = _undoAvailable.asStateFlow()

    private val _pinUnlocked = MutableStateFlow(false)
    val pinUnlocked: StateFlow<Boolean> = _pinUnlocked.asStateFlow()

    private val _selectedGroupTab = MutableStateFlow(0)
    val selectedGroupTab: StateFlow<Int> = _selectedGroupTab.asStateFlow()

    init {
        viewModelScope.launch {
            repo.maybeDailyReset()
        }
    }

    fun selectGroupTab(index: Int) {
        _selectedGroupTab.value = index
    }

    fun toggleCourier(id: String) {
        viewModelScope.launch {
            repo.toggleCourier(id)
            _undoAvailable.value = true
            _message.value = UiMessage("Zaktualizowano kuriera — QR przełączony")
        }
    }

    fun undo() {
        viewModelScope.launch {
            if (repo.undoLastCourierChange()) {
                _undoAvailable.value = false
                _message.value = UiMessage("Cofnięto ostatnią zmianę")
            }
        }
    }

    fun confirmNewDay() {
        viewModelScope.launch {
            repo.resetAllActive()
            _message.value = UiMessage("Nowy dzień — wszyscy kurierzy aktywni")
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { repo.updateSettings(transform) }
    }

    fun renameCourier(id: String, name: String) {
        viewModelScope.launch { repo.renameCourier(id, name) }
    }

    fun updateGroup(config: GroupConfig) {
        viewModelScope.launch { repo.updateGroup(config) }
    }

    fun setPin(pin: String?) {
        viewModelScope.launch {
            repo.setPin(pin)
            if (pin.isNullOrBlank()) _pinUnlocked.value = true
        }
    }

    fun unlockWithPin(pin: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = repo.verifyPin(pin)
            if (ok) _pinUnlocked.value = true
            onResult(ok)
        }
    }

    fun lockPin() {
        _pinUnlocked.value = false
    }

    fun ensurePinGate(settings: AppSettings): Boolean {
        if (!settings.pinEnabled) {
            _pinUnlocked.value = true
            return true
        }
        return _pinUnlocked.value
    }

    fun upsertVariant(config: QrVariantConfig) {
        viewModelScope.launch {
            repo.upsertVariant(config)
            _message.value = UiMessage("Zapisano wariant QR")
        }
    }

    fun setVariantEnabled(key: QrVariantKey, enabled: Boolean) {
        viewModelScope.launch { repo.setVariantEnabled(key, enabled) }
    }

    fun deleteVariant(key: QrVariantKey) {
        viewModelScope.launch {
            repo.deleteVariant(key)
            _message.value = UiMessage("Usunięto wariant — QR natychmiast wyczyszczony")
        }
    }

    fun importPayload(key: QrVariantKey, payload: String) {
        viewModelScope.launch {
            repo.importPayloadText(key, payload)
            _message.value = UiMessage("Zaimportowano treść QR")
        }
    }

    fun importImage(uri: Uri, key: QrVariantKey, payload: String?) {
        viewModelScope.launch {
            try {
                repo.importImageAsVariant(uri, key, payload)
                _message.value = UiMessage("Zaimportowano obraz QR")
            } catch (e: Exception) {
                _message.value = UiMessage("Błąd importu: ${e.message}")
            }
        }
    }

    fun importPdf(uri: Uri, page: Int, key: QrVariantKey, payload: String?) {
        viewModelScope.launch {
            try {
                repo.importPdfPageAsVariant(uri, page, key, payload)
                _message.value = UiMessage("Zaimportowano stronę PDF")
            } catch (e: Exception) {
                _message.value = UiMessage("Błąd PDF: ${e.message}")
            }
        }
    }

    fun exportConfig(onDone: (String) -> Unit) {
        viewModelScope.launch {
            onDone(repo.exportConfig())
        }
    }

    fun importConfig(json: String) {
        viewModelScope.launch {
            try {
                val n = repo.importConfig(json)
                _message.value = UiMessage("Zaimportowano $n wariantów (bez sekretów API)")
            } catch (e: Exception) {
                _message.value = UiMessage("Błąd importu konfiguracji: ${e.message}")
            }
        }
    }

    fun encodeQr(payload: String) = repo.encodeQr(payload)
    fun loadFallback(path: String?) = repo.loadFallbackBitmap(path)

    fun duplicatePayloads(variants: List<QrVariantConfig>) = repo.findDuplicatePayloads(variants)

    fun visibleGroups(snapshot: PickPackRepository.PanelSnapshot): List<GroupConfig> {
        val order = snapshot.settings.groupOrder
        return order.mapNotNull { id ->
            snapshot.groups.find { it.id == id && it.visible }
        }.ifEmpty {
            snapshot.groups.filter { it.visible }.sortedBy { it.orderIndex }
        }
    }

    fun hasToken(): Boolean = repo.hasToken()

    fun saveToken(token: String?) {
        repo.setToken(token)
        _message.value = UiMessage(
            if (token.isNullOrBlank()) "Token usunięty" else "Token zapisany (Keystore)"
        )
        // Nudge settings so UI refreshes baselinkerTokenPresent
        viewModelScope.launch {
            repo.updateSettings { it }
            repo.syncNow()
        }
    }

    fun updatePoolFilter(transform: (PoolFilterConfig) -> PoolFilterConfig) {
        viewModelScope.launch { repo.updatePoolFilter(transform) }
    }

    fun setTemplateId(key: QrVariantKey, templateId: Int?) {
        viewModelScope.launch {
            repo.setTemplateId(key, templateId)
            _message.value = UiMessage(
                if (templateId == null) "Usunięto template ID"
                else "Zapisano template ID $templateId"
            )
        }
    }

    fun loadStatuses(onResult: (Result<List<BlStatus>>) -> Unit) {
        viewModelScope.launch { onResult(repo.fetchStatuses()) }
    }

    fun syncNow() {
        viewModelScope.launch {
            val result = repo.syncNow()
            _message.value = UiMessage(
                result.fold(
                    onSuccess = { "Sync OK — $it zamówień (bez PII)" },
                    onFailure = { "Sync: ${it.message}" },
                )
            )
        }
    }
}
