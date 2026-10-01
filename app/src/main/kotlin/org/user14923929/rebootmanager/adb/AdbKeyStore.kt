package org.user14923929.rebootmanager.adb

import android.util.Base64
import java.io.File
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

/**
 * Holds this app's own ADB identity (RSA-2048) in app-private storage.
 * Backups are disabled in the manifest so the private key never leaves the device.
 */
class AdbKeyStore(private val dir: File) {

    private val keyPair: KeyPair by lazy { loadOrCreate() }

    private fun loadOrCreate(): KeyPair {
        val privateFile = File(dir, "adbkey")
        val publicFile = File(dir, "adbkey.pub")
        if (privateFile.exists() && publicFile.exists()) {
            runCatching {
                val factory = KeyFactory.getInstance("RSA")
                return KeyPair(
                    factory.generatePublic(X509EncodedKeySpec(publicFile.readBytes())),
                    factory.generatePrivate(PKCS8EncodedKeySpec(privateFile.readBytes())),
                )
            }
        }
        val generated = KeyPairGenerator.getInstance("RSA").apply { initialize(KEY_BITS) }.generateKeyPair()
        dir.mkdirs()
        privateFile.writeBytes(generated.private.encoded)
        publicFile.writeBytes(generated.public.encoded)
        return generated
    }

    /** ADB's AUTH_SIGNATURE: the 20-byte token is treated as an already-hashed SHA-1 digest. */
    fun signToken(token: ByteArray): ByteArray =
        Signature.getInstance("NONEwithRSA").run {
            initSign(keyPair.private)
            update(SHA1_DIGEST_INFO_PREFIX)
            update(token)
            sign()
        }

    /** The public key in ADB's own wire format, base64 encoded and NUL terminated. */
    fun adbPublicKey(): ByteArray {
        val key = keyPair.public as RSAPublicKey
        val n = key.modulus
        val word = BigInteger.ONE.shiftLeft(32)
        val n0inv = word.subtract(n.mod(word).modInverse(word)).toInt()
        val rr = BigInteger.ONE.shiftLeft(KEY_BITS * 2).mod(n)

        val struct = ByteBuffer.allocate(4 + 4 + MODULUS_BYTES * 2 + 4).order(ByteOrder.LITTLE_ENDIAN)
        struct.putInt(MODULUS_BYTES / 4)
        struct.putInt(n0inv)
        struct.put(n.toLittleEndian(MODULUS_BYTES))
        struct.put(rr.toLittleEndian(MODULUS_BYTES))
        struct.putInt(key.publicExponent.toInt())

        val text = Base64.encodeToString(struct.array(), Base64.NO_WRAP) + " reboot-manager@android"
        return text.toByteArray() + 0
    }

    private fun BigInteger.toLittleEndian(size: Int): ByteArray {
        val bigEndian = toByteArray()
        val out = ByteArray(size)
        for (i in 0 until minOf(size, bigEndian.size)) out[i] = bigEndian[bigEndian.size - 1 - i]
        return out
    }

    private companion object {
        const val KEY_BITS = 2048
        const val MODULUS_BYTES = KEY_BITS / 8

        val SHA1_DIGEST_INFO_PREFIX = byteArrayOf(
            0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14,
        )
    }
}
