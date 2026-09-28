package dev.rebootmanager

import android.app.Application
import android.content.Context
import dev.rebootmanager.adb.AdbClient
import dev.rebootmanager.adb.AdbEndpointResolver
import dev.rebootmanager.adb.AdbKeyStore
import dev.rebootmanager.core.CapabilityDetector
import dev.rebootmanager.core.DefaultRebootCommandProvider
import dev.rebootmanager.core.ExecutionMethod
import dev.rebootmanager.core.RebootManager
import dev.rebootmanager.data.SettingsRepository
import dev.rebootmanager.exec.AdbExecutor
import dev.rebootmanager.exec.RootExecutor
import dev.rebootmanager.exec.WirelessAdbExecutor
import java.io.File

/** Manual dependency wiring: small enough that a DI framework would only add weight. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val settings = SettingsRepository(appContext)

    private val adbClient = AdbClient(AdbKeyStore(File(appContext.filesDir, "adb")))
    private val endpoints = AdbEndpointResolver(settings)
    private val root = RootExecutor()

    val rebootManager = RebootManager(
        detector = CapabilityDetector(appContext, root, adbClient, endpoints),
        executors = mapOf(
            ExecutionMethod.ROOT to root,
            ExecutionMethod.USB_ADB to AdbExecutor(adbClient, endpoints),
            ExecutionMethod.WIRELESS_ADB to WirelessAdbExecutor(adbClient, endpoints),
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
