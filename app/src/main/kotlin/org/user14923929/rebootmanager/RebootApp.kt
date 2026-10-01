package org.user14923929.rebootmanager

import android.app.Application
import android.content.Context
import org.user14923929.rebootmanager.adb.AdbClient
import org.user14923929.rebootmanager.adb.AdbEndpointResolver
import org.user14923929.rebootmanager.adb.AdbKeyStore
import org.user14923929.rebootmanager.adb.tls.AppAdbConnectionManager
import org.user14923929.rebootmanager.adb.tls.TlsIdentity
import org.user14923929.rebootmanager.adb.tls.WirelessDebuggingClient
import org.user14923929.rebootmanager.core.CapabilityDetector
import org.user14923929.rebootmanager.core.DefaultRebootCommandProvider
import org.user14923929.rebootmanager.core.ExecutionMethod
import org.user14923929.rebootmanager.core.RebootManager
import org.user14923929.rebootmanager.data.SettingsRepository
import org.user14923929.rebootmanager.exec.AdbExecutor
import org.user14923929.rebootmanager.exec.RootExecutor
import org.user14923929.rebootmanager.exec.WirelessAdbExecutor
import java.io.File

/** Manual dependency wiring: small enough that a DI framework would only add weight. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val settings = SettingsRepository(appContext)

    // USB ADB: the legacy `adb tcpip` transport, RSA signature auth, hand-rolled (no library).
    private val adbClient = AdbClient(AdbKeyStore(File(appContext.filesDir, "adb")))
    // Wireless debugging: TLS + pairing code, via libadb-android. Separate identity from USB
    // ADB's, since adbd tracks each one's trust independently.
    private val wirelessClient = WirelessDebuggingClient(
        AppAdbConnectionManager(TlsIdentity(File(appContext.filesDir, "wireless_adb"))),
    )
    private val endpoints = AdbEndpointResolver()
    private val root = RootExecutor()

    val rebootManager = RebootManager(
        detector = CapabilityDetector(appContext, root, adbClient, wirelessClient, endpoints),
        executors = mapOf(
            ExecutionMethod.ROOT to root,
            ExecutionMethod.USB_ADB to AdbExecutor(adbClient, endpoints),
            ExecutionMethod.WIRELESS_ADB to WirelessAdbExecutor(appContext, wirelessClient),
        ),
        commands = DefaultRebootCommandProvider(),
    )
}

class RebootApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
