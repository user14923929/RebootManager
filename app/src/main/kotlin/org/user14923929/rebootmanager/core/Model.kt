package org.user14923929.rebootmanager.core

enum class RebootMode { RESTART, RECOVERY, BOOTLOADER, FASTBOOTD, SHUTDOWN }

enum class ExecutionMethod { AUTO, ROOT, USB_ADB, WIRELESS_ADB }

/**
 * Deliberately separates "the platform supports it" from "it is connected" from "it works":
 *
 * - UNSUPPORTED:   not possible on this device / Android version
 * - NOT_CONNECTED: possible, but no link is established right now
 * - CONNECTED:     a link exists, but this app is not authorized to use it yet
 * - READY:         a command can be executed right now
 */
enum class MethodState { UNSUPPORTED, NOT_CONNECTED, CONNECTED, READY }

/** [lines] are ready-to-display diagnostics, e.g. "✓ Android 11+ supported", "⚠ Not connected". */
data class MethodStatus(val state: MethodState, val lines: List<String>) {
    val isReady: Boolean get() = state == MethodState.READY
}

data class ModeSupport(val supported: Boolean, val note: String? = null)

data class Capabilities(
    val sdkInt: Int,
    val root: MethodStatus,
    val usbAdb: MethodStatus,
    val wirelessAdb: MethodStatus,
    val modes: Map<RebootMode, ModeSupport>,
) {
    /** AUTO is not a real method and has no status of its own. */
    fun statusOf(method: ExecutionMethod): MethodStatus? = when (method) {
        ExecutionMethod.ROOT -> root
        ExecutionMethod.USB_ADB -> usbAdb
        ExecutionMethod.WIRELESS_ADB -> wirelessAdb
        ExecutionMethod.AUTO -> null
    }

    /**
     * Picks the method that would be used right now.
     * AUTO order: Root -> USB ADB -> Wireless ADB -> null (unavailable).
     */
    fun resolve(preferred: ExecutionMethod): ExecutionMethod? =
        if (preferred == ExecutionMethod.AUTO) {
            AUTO_ORDER.firstOrNull { statusOf(it)?.isReady == true }
        } else {
            preferred.takeIf { statusOf(it)?.isReady == true }
        }

    companion object {
        val AUTO_ORDER = listOf(ExecutionMethod.ROOT, ExecutionMethod.USB_ADB, ExecutionMethod.WIRELESS_ADB)
    }
}

sealed interface ExecResult {
    data class Success(val output: String = "") : ExecResult
    data class Failure(val message: String) : ExecResult
}

sealed interface RebootOutcome {
    data class Sent(val method: ExecutionMethod) : RebootOutcome
    data class Failed(val message: String) : RebootOutcome
}
