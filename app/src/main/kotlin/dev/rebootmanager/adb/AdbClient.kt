package dev.rebootmanager.adb

import dev.rebootmanager.core.ExecResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.IOException
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A small, dependency-free client for the classic ADB wire protocol over TCP
 * (the mode enabled by "adb tcpip PORT"). It is the "ADB interface" of the app:
 * the app itself has no ADB privileges, it is simply an ADB client that adbd must trust.
 *
 * NOT covered: Android 11+ "Wireless debugging" with pairing codes and TLS. That would be a
 * second transport behind the same [AdbEndpoint] abstraction.
 */
class AdbClient(private val keys: AdbKeyStore) {

    suspend fun isReachable(endpoint: AdbEndpoint): Boolean = withContext(Dispatchers.IO) {
        try {
            Socket().use { it.connect(InetSocketAddress(endpoint.host, endpoint.port), CONNECT_TIMEOUT_MS) }
            true
        } catch (_: IOException) {
            false
        }
    }

    /** Signature-only handshake: never sends our public key, so it never triggers a dialog. */
    suspend fun isAuthorized(endpoint: AdbEndpoint): Boolean = withContext(Dispatchers.IO) {
        try {
            open(endpoint).use { handshake(it, allowPrompt = false) == Handshake.AUTHORIZED }
        } catch (_: IOException) {
            false
        }
    }

    /** Sends our public key; the device shows "Allow debugging?" and we wait for the answer. */
    suspend fun authorize(endpoint: AdbEndpoint): ExecResult = withContext(Dispatchers.IO) {
        try {
            open(endpoint).use {
                when (handshake(it, allowPrompt = true)) {
                    Handshake.AUTHORIZED -> ExecResult.Success()
                    Handshake.NOT_TRUSTED -> ExecResult.Failure("The device did not accept this app's ADB key.")
                }
            }
        } catch (e: IOException) {
            ExecResult.Failure(describe(e, endpoint))
        }
    }

    suspend fun shell(endpoint: AdbEndpoint, command: String): ExecResult = withContext(Dispatchers.IO) {
        try {
            open(endpoint).use { connection ->
                if (handshake(connection, allowPrompt = false) == Handshake.AUTHORIZED) {
                    runShell(connection, command)
                } else {
                    ExecResult.Failure("This app is not authorized for ADB yet.\nTap Authorize on the main screen.")
                }
            }
        } catch (e: IOException) {
            ExecResult.Failure(describe(e, endpoint))
        }
    }

    // ---- protocol -------------------------------------------------------------------------

    private enum class Handshake { AUTHORIZED, NOT_TRUSTED }

    private fun open(endpoint: AdbEndpoint): AdbConnection {
        val socket = Socket()
        try {
            socket.connect(InetSocketAddress(endpoint.host, endpoint.port), CONNECT_TIMEOUT_MS)
            socket.soTimeout = READ_TIMEOUT_MS
        } catch (e: IOException) {
            socket.close()
            throw e
        }
        return AdbConnection(socket)
    }

    private fun handshake(connection: AdbConnection, allowPrompt: Boolean): Handshake {
        connection.write(AdbMessage(A_CNXN, VERSION, MAX_PAYLOAD, "host::reboot-manager\u0000".toByteArray()))
        var signatureSent = false
        var keySent = false
        while (true) {
            val message = connection.read()
            when {
                message.command == A_CNXN -> return Handshake.AUTHORIZED
                message.command == A_AUTH && message.arg0 == AUTH_TOKEN -> when {
                    !signatureSent -> {
                        connection.write(AdbMessage(A_AUTH, AUTH_SIGNATURE, 0, keys.signToken(message.data)))
                        signatureSent = true
                    }
                    allowPrompt && !keySent -> {
                        connection.setReadTimeout(PROMPT_TIMEOUT_MS)
                        connection.write(AdbMessage(A_AUTH, AUTH_RSAPUBLICKEY, 0, keys.adbPublicKey()))
                        keySent = true
                    }
                    else -> return Handshake.NOT_TRUSTED
                }
                else -> throw IOException("Unexpected reply from ADB (command 0x${message.command.toString(16)}).")
            }
        }
    }

    private fun runShell(connection: AdbConnection, command: String): ExecResult {
        connection.write(AdbMessage(A_OPEN, LOCAL_ID, 0, "shell:$command\u0000".toByteArray()))
        val output = StringBuilder()
        var opened = false
        try {
            while (true) {
                val message = connection.read()
                when (message.command) {
                    A_OKAY -> opened = true
                    A_WRTE -> {
                        output.append(String(message.data))
                        connection.write(AdbMessage(A_OKAY, LOCAL_ID, message.arg0, ByteArray(0)))
                    }
                    A_CLSE -> return ExecResult.Success(output.toString().trim())
                }
            }
        } catch (e: IOException) {
            // Rebooting kills the connection mid-command. Once the stream was opened, that is success.
            return if (opened) {
                ExecResult.Success(output.toString().trim())
            } else {
                ExecResult.Failure(e.message ?: "ADB connection lost.")
            }
        }
    }

    private fun describe(e: IOException, endpoint: AdbEndpoint): String = when (e) {
        is ConnectException -> "ADB is not connected.\nNothing is listening on $endpoint."
        else -> "ADB error: ${e.message ?: e.javaClass.simpleName}"
    }

    private class AdbMessage(val command: Int, val arg0: Int, val arg1: Int, val data: ByteArray)

    private class AdbConnection(private val socket: Socket) : Closeable {
        private val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
        private val output = BufferedOutputStream(socket.getOutputStream())

        fun setReadTimeout(millis: Int) {
            socket.soTimeout = millis
        }

        fun write(message: AdbMessage) {
            val header = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
            header.putInt(message.command)
            header.putInt(message.arg0)
            header.putInt(message.arg1)
            header.putInt(message.data.size)
            header.putInt(0) // data checksum: ignored by adbd since protocol 0x01000001
            header.putInt(message.command.inv())
            output.write(header.array())
            output.write(message.data)
            output.flush()
        }

        fun read(): AdbMessage {
            val raw = ByteArray(HEADER_SIZE)
            input.readFully(raw)
            val header = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
            val command = header.int
            val arg0 = header.int
            val arg1 = header.int
            val length = header.int
            header.int // checksum
            val magic = header.int
            if (magic != command.inv()) throw IOException("Not an ADB peer (bad magic).")
            if (length < 0 || length > MAX_PAYLOAD) throw IOException("Invalid ADB payload size: $length.")
            val data = ByteArray(length)
            input.readFully(data)
            return AdbMessage(command, arg0, arg1, data)
        }

        override fun close() = socket.close()
    }

    private companion object {
        const val A_CNXN = 0x4e584e43
        const val A_AUTH = 0x48545541
        const val A_OPEN = 0x4e45504f
        const val A_OKAY = 0x59414b4f
        const val A_CLSE = 0x45534c43
        const val A_WRTE = 0x45545257

        const val AUTH_TOKEN = 1
        const val AUTH_SIGNATURE = 2
        const val AUTH_RSAPUBLICKEY = 3

        const val VERSION = 0x01000001
        const val MAX_PAYLOAD = 256 * 1024
        const val HEADER_SIZE = 24
        const val LOCAL_ID = 1

        const val CONNECT_TIMEOUT_MS = 800
        const val READ_TIMEOUT_MS = 4_000
        const val PROMPT_TIMEOUT_MS = 30_000
    }
}
