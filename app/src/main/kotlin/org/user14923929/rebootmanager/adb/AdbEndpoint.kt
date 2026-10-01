package org.user14923929.rebootmanager.adb

import org.user14923929.rebootmanager.exec.ShellRunner

data class AdbEndpoint(val host: String, val port: Int) {
    override fun toString() = "$host:$port"
}

/**
 * Works out where adbd can be reached for USB ADB (the legacy `adb tcpip` transport).
 * Wireless debugging (TLS) has no equivalent here: it discovers its own host and port via
 * mDNS/NSD through libadb-android's `autoConnect`, so it needs no resolver — see
 * [org.user14923929.rebootmanager.adb.tls.WirelessDebuggingClient].
 */
class AdbEndpointResolver {

    /** Loopback; the port is whatever "adb tcpip" configured (default 5555). */
    suspend fun usb(): AdbEndpoint {
        val port = listOf("service.adb.tcp.port", "persist.adb.tcp.port")
            .firstNotNullOfOrNull { ShellRunner.getProp(it).toIntOrNull()?.takeIf { p -> p in 1..65535 } }
            ?: DEFAULT_PORT
        return AdbEndpoint("127.0.0.1", port)
    }

    companion object {
        const val DEFAULT_PORT = 5555
    }
}
