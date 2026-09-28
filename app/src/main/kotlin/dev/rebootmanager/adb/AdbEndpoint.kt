package dev.rebootmanager.adb

import dev.rebootmanager.data.SettingsRepository
import dev.rebootmanager.exec.ShellRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.SocketException

data class AdbEndpoint(val host: String, val port: Int) {
    override fun toString() = "$host:$port"
}

/** Works out where adbd can be reached for each ADB flavour. */
class AdbEndpointResolver(private val settings: SettingsRepository) {

    /** Loopback; the port is whatever "adb tcpip" configured (default 5555). */
    suspend fun usb(): AdbEndpoint {
        val port = listOf("service.adb.tcp.port", "persist.adb.tcp.port")
            .firstNotNullOfOrNull { ShellRunner.getProp(it).toIntOrNull()?.takeIf { p -> p in 1..65535 } }
            ?: DEFAULT_PORT
        return AdbEndpoint("127.0.0.1", port)
    }

    /** This device's Wi-Fi IPv4 address plus the configured port, or null without Wi-Fi. */
    suspend fun wireless(): AdbEndpoint? = withContext(Dispatchers.IO) {
        val ip = try {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                .filter { it.isUp && !it.isLoopback && it.name.startsWith("wlan") }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress }
                ?.hostAddress
        } catch (_: SocketException) {
            null
        }
        ip?.let { AdbEndpoint(it, settings.state.value.wirelessPort) }
    }

    companion object {
        const val DEFAULT_PORT = 5555
    }
}
