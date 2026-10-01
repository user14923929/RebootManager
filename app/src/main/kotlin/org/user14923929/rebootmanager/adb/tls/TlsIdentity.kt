package org.user14923929.rebootmanager.adb.tls

import android.sun.security.x509.AlgorithmId
import android.sun.security.x509.CertificateAlgorithmId
import android.sun.security.x509.CertificateExtensions
import android.sun.security.x509.CertificateIssuerName
import android.sun.security.x509.CertificateSerialNumber
import android.sun.security.x509.CertificateSubjectName
import android.sun.security.x509.CertificateValidity
import android.sun.security.x509.CertificateVersion
import android.sun.security.x509.CertificateX509Key
import android.sun.security.x509.KeyIdentifier
import android.sun.security.x509.PrivateKeyUsageExtension
import android.sun.security.x509.SubjectKeyIdentifierExtension
import android.sun.security.x509.X500Name
import android.sun.security.x509.X509CertImpl
import android.sun.security.x509.X509CertInfo
import java.io.File
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import java.util.Random

/**
 * This app's own ADB identity for "Wireless debugging": an RSA key pair plus a self-signed
 * certificate, generated once and kept in app-private storage. Backups are disabled in the
 * manifest, so the key never leaves the device. adbd remembers this certificate after pairing,
 * the same way it remembers a trusted USB key.
 *
 * Certificate generation uses `android.sun.security.x509` from the `sun-security-android`
 * library (a port of `sun.security.x509`, which Android itself does not expose) — the approach
 * libadb-android's own consumers use, since Android has no other non-hidden API for this.
 */
class TlsIdentity(private val dir: File) {

    val privateKey: PrivateKey by lazy { loadOrCreate().first }
    val certificate: Certificate by lazy { loadOrCreate().second }

    private fun loadOrCreate(): Pair<PrivateKey, Certificate> {
        val keyFile = File(dir, "wireless_adb_key")
        val certFile = File(dir, "wireless_adb_cert")
        if (keyFile.exists() && certFile.exists()) {
            runCatching {
                val key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyFile.readBytes()))
                val cert = CertificateFactory.getInstance("X.509")
                    .generateCertificate(certFile.readBytes().inputStream())
                return key to cert
            }
        }
        val pair = generate()
        dir.mkdirs()
        keyFile.writeBytes(pair.first.encoded)
        certFile.writeBytes(pair.second.encoded)
        return pair
    }

    private fun generate(): Pair<PrivateKey, Certificate> {
        val keyPair = KeyPairGenerator.getInstance("RSA").run {
            initialize(KEY_BITS, SecureRandom.getInstance("SHA1PRNG"))
            generateKeyPair()
        }
        return keyPair.private to selfSignedCertificate(keyPair.public, keyPair.private)
    }

    private fun selfSignedCertificate(publicKey: PublicKey, privateKey: PrivateKey): Certificate {
        val subject = X500Name("CN=Reboot Manager")
        val notBefore = Date()
        val notAfter = Date(notBefore.time + VALIDITY_MS)

        val extensions = CertificateExtensions()
        extensions.set("SubjectKeyIdentifier", SubjectKeyIdentifierExtension(KeyIdentifier(publicKey).identifier))
        extensions.set("PrivateKeyUsage", PrivateKeyUsageExtension(notBefore, notAfter))

        val info = X509CertInfo()
        info.set("version", CertificateVersion(2))
        info.set("serialNumber", CertificateSerialNumber(Random().nextInt() and Int.MAX_VALUE))
        info.set("algorithmID", CertificateAlgorithmId(AlgorithmId.get(SIGNATURE_ALGORITHM)))
        info.set("subject", CertificateSubjectName(subject))
        info.set("key", CertificateX509Key(publicKey))
        info.set("validity", CertificateValidity(notBefore, notAfter))
        info.set("issuer", CertificateIssuerName(subject))
        info.set("extensions", extensions)

        val cert = X509CertImpl(info)
        cert.sign(privateKey, SIGNATURE_ALGORITHM)
        return cert
    }

    private companion object {
        const val KEY_BITS = 2048
        const val SIGNATURE_ALGORITHM = "SHA512withRSA"
        const val VALIDITY_MS = 10L * 365 * 24 * 60 * 60 * 1000 // 10 years; adbd trusts the certificate, not this expiry.
    }
}
