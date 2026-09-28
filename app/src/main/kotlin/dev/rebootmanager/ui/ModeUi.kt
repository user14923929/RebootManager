package dev.rebootmanager.ui

import androidx.annotation.StringRes
import dev.rebootmanager.R
import dev.rebootmanager.core.ExecutionMethod
import dev.rebootmanager.core.RebootMode

internal data class ModeUi(
    val emoji: String,
    @StringRes val title: Int,
    @StringRes val confirmTitle: Int,
    @StringRes val confirmBody: Int,
    @StringRes val confirmAction: Int,
)

internal fun RebootMode.ui(): ModeUi = when (this) {
    RebootMode.RESTART -> ModeUi("🔄", R.string.mode_restart, R.string.confirm_restart, R.string.confirm_body_reboot, R.string.confirm_action_reboot)
    RebootMode.RECOVERY -> ModeUi("🛠", R.string.mode_recovery, R.string.confirm_recovery, R.string.confirm_body_reboot, R.string.confirm_action_reboot)
    RebootMode.BOOTLOADER -> ModeUi("⚡", R.string.mode_bootloader, R.string.confirm_bootloader, R.string.confirm_body_reboot, R.string.confirm_action_reboot)
    RebootMode.FASTBOOT -> ModeUi("🚀", R.string.mode_fastboot, R.string.confirm_fastboot, R.string.confirm_body_reboot, R.string.confirm_action_reboot)
    RebootMode.FASTBOOTD -> ModeUi("🧪", R.string.mode_fastbootd, R.string.confirm_fastbootd, R.string.confirm_body_reboot, R.string.confirm_action_reboot)
    RebootMode.SHUTDOWN -> ModeUi("⏻", R.string.mode_shutdown, R.string.confirm_shutdown, R.string.confirm_body_shutdown, R.string.confirm_action_shutdown)
}

@StringRes
internal fun ExecutionMethod.titleRes(): Int = when (this) {
    ExecutionMethod.AUTO -> R.string.method_auto
    ExecutionMethod.ROOT -> R.string.method_root
    ExecutionMethod.USB_ADB -> R.string.method_usb_adb
    ExecutionMethod.WIRELESS_ADB -> R.string.method_wireless_adb
}
