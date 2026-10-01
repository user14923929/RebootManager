package org.user14923929.rebootmanager.exec

import org.user14923929.rebootmanager.adb.AdbClient
import org.user14923929.rebootmanager.adb.AdbEndpointResolver
import org.user14923929.rebootmanager.core.ExecResult
import org.user14923929.rebootmanager.core.ExecutionMethod

/**
 * "USB ADB": talks to the device's own adbd through the loopback interface.
 * Requires USB debugging and adbd in TCP mode (run "adb tcpip 5555" once from a computer over USB).
 */
class AdbExecutor(
    private val client: AdbClient,
    private val endpoints: AdbEndpointResolver,
) : CommandExecutor, Authorizable {
    override val method = ExecutionMethod.USB_ADB

    override suspend fun execute(command: String): ExecResult =
        client.shell(endpoints.usb(), command)

    override suspend fun authorize(): ExecResult =
        client.authorize(endpoints.usb())
}
