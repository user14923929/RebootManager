package dev.rebootmanager.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rebootmanager.core.Capabilities
import dev.rebootmanager.core.ExecResult
import dev.rebootmanager.core.ExecutionMethod
import dev.rebootmanager.core.RebootManager
import dev.rebootmanager.core.RebootMode
import dev.rebootmanager.core.RebootOutcome
import dev.rebootmanager.data.AppSettings
import dev.rebootmanager.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val capabilities: Capabilities? = null,
    val refreshing: Boolean = false,
    val busy: Boolean = false,
    val pendingConfirm: RebootMode? = null,
    /** One-shot diagnostic text (English) shown in a snackbar. */
    val message: String? = null,
)

class MainViewModel(
    private val manager: RebootManager,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(MainUiState())
    val ui: StateFlow<MainUiState> = _ui.asStateFlow()
    val settings: StateFlow<AppSettings> = settingsRepository.state

    fun refresh() {
        if (_ui.value.refreshing) return
        viewModelScope.launch {
            _ui.update { it.copy(refreshing = true) }
            val caps = manager.detect()
            _ui.update { it.copy(capabilities = caps, refreshing = false) }
        }
    }

    /** Critical modes always ask; a plain restart asks unless the user turned that off. */
    fun onModeClicked(mode: RebootMode) {
        if (_ui.value.busy) return
        val needsConfirmation = mode != RebootMode.RESTART || settings.value.confirmRestart
        if (needsConfirmation) {
            _ui.update { it.copy(pendingConfirm = mode) }
        } else {
            execute(mode)
        }
    }

    fun confirmPending() {
        val mode = _ui.value.pendingConfirm ?: return
        _ui.update { it.copy(pendingConfirm = null) }
        execute(mode)
    }

    fun dismissConfirm() = _ui.update { it.copy(pendingConfirm = null) }

    fun consumeMessage() = _ui.update { it.copy(message = null) }

    fun authorize(method: ExecutionMethod) {
        if (_ui.value.busy) return
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, message = "Confirm the prompt on the device if it appears…") }
            val text = when (val result = manager.authorize(method)) {
                is ExecResult.Success -> "Authorized."
                is ExecResult.Failure -> result.message
            }
            _ui.update { it.copy(busy = false, message = text) }
            refresh()
        }
    }

    fun setMethod(method: ExecutionMethod) = settingsRepository.update { it.copy(method = method) }

    fun setConfirmRestart(value: Boolean) = settingsRepository.update { it.copy(confirmRestart = value) }

    fun setWirelessPort(port: Int) {
        settingsRepository.update { it.copy(wirelessPort = port) }
        refresh()
    }

    private fun execute(mode: RebootMode) {
        viewModelScope.launch {
            _ui.update { it.copy(busy = true) }
            val text = when (val outcome = manager.reboot(mode, settings.value.method)) {
                is RebootOutcome.Sent -> "Command sent (${outcome.method})."
                is RebootOutcome.Failed -> outcome.message
            }
            _ui.update { it.copy(busy = false, message = text) }
        }
    }
}
