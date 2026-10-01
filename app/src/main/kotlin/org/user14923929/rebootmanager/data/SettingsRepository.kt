package org.user14923929.rebootmanager.data

import android.content.Context
import org.user14923929.rebootmanager.core.ExecutionMethod
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val method: ExecutionMethod = ExecutionMethod.AUTO,
    val confirmRestart: Boolean = true,
)

class SettingsRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_state.value)
        prefs.edit()
            .putString(KEY_METHOD, next.method.name)
            .putBoolean(KEY_CONFIRM_RESTART, next.confirmRestart)
            .apply()
        _state.value = next
    }

    private fun load(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            method = prefs.getString(KEY_METHOD, null)
                ?.let { runCatching { ExecutionMethod.valueOf(it) }.getOrNull() }
                ?: defaults.method,
            confirmRestart = prefs.getBoolean(KEY_CONFIRM_RESTART, defaults.confirmRestart),
        )
    }

    private companion object {
        const val KEY_METHOD = "method"
        const val KEY_CONFIRM_RESTART = "confirm_restart"
    }
}
