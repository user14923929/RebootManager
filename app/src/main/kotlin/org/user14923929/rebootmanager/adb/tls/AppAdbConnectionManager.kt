package org.user14923929.rebootmanager.adb.tls

import android.os.Build
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import java.security.PrivateKey
import java.security.cert.Certificate

/**
 * Wires this app's [TlsIdentity] into libadb-android's connection manager.
 *
 * The base class' package is `io.github.muntashirakon.adb` — note this differs from the
 * `com.github.MuntashirAkon:libadb-android` Maven/JitPack coordinate, which only names where
 * the artifact is hosted, not the Java package inside it. Verified against a real app
 * (github.com/sam1am/anyapk) built on the same library version.
 */
class AppAdbConnectionManager(private val identity: TlsIdentity) : AbsAdbConnectionManager() {

    init {
        setApi(Build.VERSION.SDK_INT)
    }

    public override fun getPrivateKey(): PrivateKey = identity.privateKey

    public override fun getCertificate(): Certificate = identity.certificate

    public override fun getDeviceName(): String = "Reboot Manager"
}
