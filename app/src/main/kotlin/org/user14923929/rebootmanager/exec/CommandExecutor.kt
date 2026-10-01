package org.user14923929.rebootmanager.exec

import org.user14923929.rebootmanager.core.ExecResult
import org.user14923929.rebootmanager.core.ExecutionMethod

interface CommandExecutor {
    val method: ExecutionMethod
    suspend fun execute(command: String): ExecResult
}

/** Implemented by executors whose link must be explicitly trusted by the device (ADB). */
interface Authorizable {
    suspend fun authorize(): ExecResult
}
