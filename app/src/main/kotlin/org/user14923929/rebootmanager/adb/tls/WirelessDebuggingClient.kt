package org.user14923929.rebootmanager.adb.tls

import android.content.Context
import org.user14923929.rebootmanager.core.ExecResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

/**
 * Thin wrapper around libadb-android for Android 11+ "Wireless debugging" (TLS, pairing code) —
 * as opposed to the legacy `adb tcpip` transport in [org.user14923929.rebootmanager.adb.AdbClient].
 *
 * [AbsAdbConnectionManager.autoConnect] (used below) discovers the host and the current
 * connection port itself via mDNS/NSD, since adbd's wireless-debugging port changes on every
 * reconnect — no port to track or enter in Settings. Pairing is a separate one-time step with
 * its own port (shown under Developer options > Wireless debugging > Pair device with pairing
 * code), which this class is given rather than discovering, to keep this first implementation
 * simple.
 */
class WirelessDebuggingClient(private val manager: AppAdbConnectionManager) {

    /** adbd's wireless-debugging services are only reachable on the device's own loopback. */
    private val pairingHost = "127.0.0.1"

    /** One-time pairing with the code and port shown under "Pair device with pairing code". */
    suspend fun pair(context: Context, pairingPort: Int, pairingCode: String): ExecResult =
        withContext(Dispatchers.IO) {
            try {
                if (manager.pair(pairingHost, pairingPort, pairingCode)) {
                    ExecResult.Success()
                } else {
                    ExecResult.Failure("The device rejected that pairing code or port.")
                }
            } catch (e: Exception) {
                ExecResult.Failure(describe(e, "Pairing failed"))
            }
        }

    /**
     * True once this device has both discovered adbd's current wireless-debugging port and
     * connected to it, which for TLS pairing also means authorized — there's no separate
     * "allow this computer?" prompt the way there is for USB ADB.
     */
    suspend fun isReady(context: Context, timeoutMs: Long = CONNECT_TIMEOUT_MS): Boolean =
        withContext(Dispatchers.IO) {
            try {
                manager.autoConnect(context, timeoutMs)
            } catch (_: Exception) {
                false
            }
        }

    suspend fun shell(context: Context, command: String): ExecResult = withContext(Dispatchers.IO) {
        try {
            if (!manager.autoConnect(context, CONNECT_TIMEOUT_MS)) {
                return@withContext ExecResult.Failure(
                    "ADB is not connected.\nConnect this device to Wi-Fi and pair it in Settings.",
                )
            }
            val stream = manager.openStream("shell:$command")
            val output = StringBuilder()
            val input = stream.openInputStream()
            val buffer = ByteArray(1024)
            // A blocking read loop, not stream.use { it.readText() }: a reboot command drops the
            // connection mid-read with no clean EOF, and n <= 0 (not an exception) is how that
            // shows up here, same as a command that simply finished.
            withTimeoutOrNull(SHELL_TIMEOUT_MS) {
                runInterruptible {
                    while (true) {
                        val n = try {
                            input.read(buffer)
                        } catch (_: IOException) {
                            -1
                        }
                        if (n <= 0) break
                        output.append(String(buffer, 0, n))
                    }
                }
            }
            runCatching { stream.close() }
            ExecResult.Success(output.toString().trim())
        } catch (e: Exception) {
            ExecResult.Failure(describe(e, "ADB error"))
        }
    }

    private fun describe(e: Exception, prefix: String): String = "$prefix: ${e.message ?: e.javaClass.simpleName}"

    private companion object {
        const val CONNECT_TIMEOUT_MS = 3_000L
        const val SHELL_TIMEOUT_MS = 15_000L
    }
}
