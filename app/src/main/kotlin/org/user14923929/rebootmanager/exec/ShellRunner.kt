package org.user14923929.rebootmanager.exec

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

internal data class ProcessResult(val exitCode: Int, val output: String, val timedOut: Boolean)

/** Runs a local process with a hard timeout. No shell string interpolation: argv only. */
internal object ShellRunner {

    suspend fun run(argv: List<String>, timeoutMs: Long): ProcessResult = withContext(Dispatchers.IO) {
        val process = try {
            ProcessBuilder(argv).redirectErrorStream(true).start()
        } catch (e: IOException) {
            return@withContext ProcessResult(-1, e.message.orEmpty(), timedOut = false)
        }

        val timedOut = AtomicBoolean(false)
        // Destroying the process closes its stream, which unblocks the read below.
        val watchdog = launch {
            delay(timeoutMs)
            timedOut.set(true)
            process.destroy()
        }
        try {
            val output = try {
                process.inputStream.bufferedReader().use { it.readText() }
            } catch (_: IOException) {
                ""
            }
            val code = process.waitFor()
            ProcessResult(code, output.trim(), timedOut.get())
        } finally {
            watchdog.cancel()
            process.destroy()
        }
    }

    suspend fun getProp(key: String): String = run(listOf("getprop", key), 2_000).output
}
