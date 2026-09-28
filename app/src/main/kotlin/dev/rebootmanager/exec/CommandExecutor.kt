package dev.rebootmanager.exec

import dev.rebootmanager.core.ExecResult
import dev.rebootmanager.core.ExecutionMethod

interface CommandExecutor {
    val method: ExecutionMethod
    suspend fun execute(command: String): ExecResult
}

/** Implemented by executors whose link must be explicitly trusted by the device (ADB). */
interface Authorizable {
    suspend fun authorize(): ExecResult
}
