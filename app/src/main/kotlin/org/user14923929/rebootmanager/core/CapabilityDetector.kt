package org.user14923929.rebootmanager.core

import android.content.Context
import android.os.Build
import android.provider.Settings
import org.user14923929.rebootmanager.adb.AdbClient
import org.user14923929.rebootmanager.adb.AdbEndpointResolver
import org.user14923929.rebootmanager.adb.tls.WirelessDebuggingClient
import org.user14923929.rebootmanager.exec.RootExecutor
import org.user14923929.rebootmanager.exec.ShellRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Detects what this device can do right now: Android version, root, ADB availability,
 * Wireless debugging support and connection, and which reboot modes make sense.
 * "Supported" and "connected" are always reported as separate facts.
 */
class CapabilityDetector(
    private val context: Context,
    private val root: RootExecutor,
    private val adb: AdbClient,
    private val wireless: WirelessDebuggingClient,
    private val endpoints: AdbEndpointResolver,
    private val sdkInt: Int = Build.VERSION.SDK_INT,
) {
    suspend fun detect(): Capabilities = withContext(Dispatchers.IO) {
        coroutineScope {
            val rootJob = async { detectRoot() }
            val usbJob = async { detectUsbAdb() }
            val wifiJob = async { detectWirelessAdb() }
            val dynamicPartitions = async { ShellRunner.getProp("ro.boot.dynamic_partitions") }
            Capabilities(
                sdkInt = sdkInt,
                root = rootJob.await(),
                usbAdb = usbJob.await(),
                wirelessAdb = wifiJob.await(),
                modes = detectModes(dynamicPartitions.await()),
            )
        }
    }

    private suspend fun detectRoot(): MethodStatus {
        if (!root.isSuBinaryPresent()) {
            return MethodStatus(MethodState.UNSUPPORTED, listOf("✕ No su binary found"))
        }
        return if (root.isRootGranted()) {
            MethodStatus(MethodState.READY, listOf("✓ Root access granted"))
        } else {
            MethodStatus(MethodState.NOT_CONNECTED, listOf("⚠ su found, but access was not granted"))
        }
    }

    private suspend fun detectUsbAdb(): MethodStatus {
        val usbDebugging =
            Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
        if (!usbDebugging) {
            return MethodStatus(MethodState.NOT_CONNECTED, listOf("⚠ USB debugging is disabled"))
        }
        val endpoint = endpoints.usb()
        return when {
            !adb.isReachable(endpoint) -> MethodStatus(
                MethodState.NOT_CONNECTED,
                listOf(
                    "✓ USB debugging enabled",
                    "⚠ Not connected",
                    "Nothing is listening on $endpoint. Run \"adb tcpip ${endpoint.port}\" once from a computer.",
                ),
            )
            !adb.isAuthorized(endpoint) -> MethodStatus(
                MethodState.CONNECTED,
                listOf("✓ USB debugging enabled", "✓ Connected", "⚠ Not authorized yet — tap Authorize"),
            )
            else -> MethodStatus(
                MethodState.READY,
                listOf("✓ USB debugging enabled", "✓ Connected", "✓ Ready"),
            )
        }
    }

    /**
     * Wireless debugging (TLS + pairing), not the legacy `adb tcpip` transport used by [detectUsbAdb].
     * Android 11+ (API 30) only means SUPPORTED — it says nothing about pairing or connection.
     * TLS pairing has no separate "allow this computer?" step the way USB ADB's RSA auth does, so
     * there's no analogue of USB ADB's "Connected but not authorized" middle state here: either
     * adbd can be found and connected to (which for a paired app means it's also trusted), or not.
     */
    private suspend fun detectWirelessAdb(): MethodStatus {
        if (sdkInt < Build.VERSION_CODES.R) {
            return MethodStatus(MethodState.UNSUPPORTED, listOf("✕ Not supported on Android < 11"))
        }
        val supported = "✓ Android 11+ supported"
        return if (wireless.isReady(context)) {
            MethodStatus(MethodState.READY, listOf(supported, "✓ Connected", "✓ Ready"))
        } else {
            MethodStatus(
                MethodState.NOT_CONNECTED,
                listOf(supported, "⚠ Not connected", "Pair this app in Settings if you haven't yet."),
            )
        }
    }

    private fun detectModes(dynamicPartitionsProp: String): Map<RebootMode, ModeSupport> =
        RebootMode.entries.associateWith { mode ->
            when (mode) {
                RebootMode.FASTBOOTD -> when {
                    sdkInt < Build.VERSION_CODES.Q ->
                        ModeSupport(false, "FastbootD requires Android 10+")
                    dynamicPartitionsProp == "false" ->
                        ModeSupport(false, "Device has no dynamic partitions (no FastbootD)")
                    dynamicPartitionsProp == "true" -> ModeSupport(true)
                    else -> ModeSupport(true, "Could not verify FastbootD support on this device")
                }
                // Recovery / bootloader cannot be verified without trying.
                else -> ModeSupport(true)
            }
        }
}
