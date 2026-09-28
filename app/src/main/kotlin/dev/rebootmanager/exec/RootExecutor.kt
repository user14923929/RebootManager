package dev.rebootmanager.exec

import dev.rebootmanager.core.ExecResult
import dev.rebootmanager.core.ExecutionMethod
import java.io.File

class RootExecutor : CommandExecutor {
    override val method = ExecutionMethod.ROOT

    override suspend fun execute(command: String): ExecResult {
        val result = ShellRunner.run(listOf("su", "-c", command), timeoutMs = 15_000)
        return when {
            result.exitCode == 0 -> ExecResult.Success(result.output)
            // Killed by a signal (exit code > 128): the device is going down, which is what we asked for.
            result.exitCode > 128 && !result.timedOut -> ExecResult.Success(result.output)
            result.timedOut -> ExecResult.Failure("Root command timed out.")
            else -> ExecResult.Failure(result.output.ifBlank { "su exited with code ${result.exitCode}." })
        }
    }

    fun isSuBinaryPresent(): Boolean {
        val fromPath = System.getenv("PATH").orEmpty().split(':').filter { it.isNotBlank() }
        val known = listOf("/system/bin", "/system/xbin", "/sbin", "/su/bin", "/system/sbin", "/debug_ramdisk")
        return (fromPath + known).distinct().any { File(it, "su").exists() }
    }

    /** May show the root manager prompt, so the timeout leaves room for the user to tap. */
    suspend fun isRootGranted(): Boolean {
        val result = ShellRunner.run(listOf("su", "-c", "id"), timeoutMs = 10_000)
        return result.exitCode == 0 && "uid=0" in result.output
    }
}
