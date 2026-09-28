package dev.rebootmanager.core

import android.content.Context
import android.os.Build
import android.provider.Settings
import dev.rebootmanager.adb.AdbClient
import dev.rebootmanager.adb.AdbEndpoint
import dev.rebootmanager.adb.AdbEndpointResolver
import dev.rebootmanager.exec.RootExecutor
import dev.rebootmanager.exec.ShellRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Detects what this device can do right now: Android version, root, ADB availability,
 * Wireless ADB support and connection, and which reboot modes make sense.
 * "Supported" and "connected" are always reported as separate facts.
 */
class CapabilityDetector(
    private val context: Context,
    private val root: RootExecutor,
    private val adb: AdbClient,
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
        return probe(endpoints.usb(), listOf("✓ USB debugging enabled"))
    }

    private suspend fun detectWirelessAdb(): MethodStatus {
        // Android 11+ (API 30) means Wireless ADB is SUPPORTED. It says nothing about being connected.
        if (sdkInt < Build.VERSION_CODES.R) {
            return MethodStatus(MethodState.UNSUPPORTED, listOf("✕ Not supported on Android < 11"))
        }
        val supported = "✓ Android 11+ supported"
        val endpoint = endpoints.wireless()
            ?: return MethodStatus(
                MethodState.NOT_CONNECTED,
                listOf(supported, "⚠ Not connected (no Wi-Fi network)"),
            )
        return probe(endpoint, listOf(supported))
    }

    /** Reachable? -> authorized? Never triggers an on-device authorization dialog. */
    private suspend fun probe(endpoint: AdbEndpoint, header: List<String>): MethodStatus = when {
        !adb.isReachable(endpoint) -> MethodStatus(
            MethodState.NOT_CONNECTED,
            header + listOf(
                "⚠ Not connected",
                "Nothing is listening on $endpoint. Run \"adb tcpip ${endpoint.port}\" once from a computer.",
            ),
        )
        !adb.isAuthorized(endpoint) -> MethodStatus(
            MethodState.CONNECTED,
            header + listOf("✓ Connected", "⚠ Not authorized yet — tap Authorize"),
        )
        else -> MethodStatus(MethodState.READY, header + listOf("✓ Connected", "✓ Ready"))
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
                // Recovery / bootloader / fastboot cannot be verified without trying.
                else -> ModeSupport(true)
            }
        }
}
