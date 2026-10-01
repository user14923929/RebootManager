package org.user14923929.rebootmanager.exec

import android.content.Context
import android.os.Build
import org.user14923929.rebootmanager.adb.tls.WirelessDebuggingClient
import org.user14923929.rebootmanager.core.ExecResult
import org.user14923929.rebootmanager.core.ExecutionMethod

/**
 * "Wireless debugging": TLS + a one-time pairing code, Android 11+ (API 30) only.
 * Being on Android 11+ means "supported", not "paired" or "connected".
 */
class WirelessAdbExecutor(
    private val context: Context,
    private val client: WirelessDebuggingClient,
) : CommandExecutor {
    override val method = ExecutionMethod.WIRELESS_ADB

    override suspend fun execute(command: String): ExecResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return ExecResult.Failure("Wireless debugging is not supported on Android < 11.")
        }
        return client.shell(context, command)
    }

    /** Pairing (separate from [execute]'s connection) happens once, from Settings. */
    suspend fun pair(pairingPort: Int, pairingCode: String): ExecResult =
        client.pair(context, pairingPort, pairingCode)
}
