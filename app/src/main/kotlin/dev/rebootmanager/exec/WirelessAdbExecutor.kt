package dev.rebootmanager.exec

import android.os.Build
import dev.rebootmanager.adb.AdbClient
import dev.rebootmanager.adb.AdbEndpointResolver
import dev.rebootmanager.core.ExecResult
import dev.rebootmanager.core.ExecutionMethod

/**
 * "Wireless ADB": talks to adbd over the Wi-Fi address of this device.
 * Android 11+ only. Being on Android 11+ means "supported", not "connected".
 */
class WirelessAdbExecutor(
    private val client: AdbClient,
    private val endpoints: AdbEndpointResolver,
) : CommandExecutor, Authorizable {
    override val method = ExecutionMethod.WIRELESS_ADB

    override suspend fun execute(command: String): ExecResult {
        val endpoint = resolveOrFail() ?: return unsupportedOrDisconnected()
        return client.shell(endpoint, command)
    }

    override suspend fun authorize(): ExecResult {
        val endpoint = resolveOrFail() ?: return unsupportedOrDisconnected()
        return client.authorize(endpoint)
    }

    private suspend fun resolveOrFail() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+: Wireless ADB supported
            endpoints.wireless()
        } else {
            // Android 10 and older: Wireless ADB unsupported
            null
        }

    private fun unsupportedOrDisconnected(): ExecResult =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            ExecResult.Failure("Wireless ADB is not supported on Android < 11.")
        } else {
            ExecResult.Failure("ADB is not connected.\nConnect this device to Wi-Fi and enable ADB over the network.")
        }
}
