package dev.rebootmanager.core

import android.os.Build

/** One concrete shell command that may put the device into [mode]. */
data class RebootCommand(val mode: RebootMode, val shell: String)

/**
 * The single place that knows which shell commands map to which reboot mode.
 * Candidates are tried in order until one succeeds, so device-specific variants
 * (e.g. an OEM "reboot download") can be added here without touching the UI or executors.
 */
interface RebootCommandProvider {
    fun candidates(mode: RebootMode, sdkInt: Int): List<RebootCommand>
}

class DefaultRebootCommandProvider : RebootCommandProvider {
    override fun candidates(mode: RebootMode, sdkInt: Int): List<RebootCommand> {
        val commands: List<String> = when (mode) {
            RebootMode.RESTART -> listOf("reboot", "svc power reboot")
            RebootMode.RECOVERY -> listOf("reboot recovery")
            // On most devices the bootloader IS the fastboot (bootloader-level) interface,
            // so both entries share the same base command. Override per device if needed.
            RebootMode.BOOTLOADER -> listOf("reboot bootloader")
            RebootMode.FASTBOOT -> listOf("reboot bootloader", "setprop sys.powerctl reboot,bootloader")
            // Userspace fastboot (fastbootd) exists only with dynamic partitions on Android 10+.
            RebootMode.FASTBOOTD ->
                if (sdkInt >= Build.VERSION_CODES.Q) listOf("reboot fastboot") else emptyList()
            RebootMode.SHUTDOWN -> listOf("reboot -p", "svc power shutdown")
        }
        return commands.map { RebootCommand(mode, it) }
    }
}
