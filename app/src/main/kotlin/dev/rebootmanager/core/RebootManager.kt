package dev.rebootmanager.core

import dev.rebootmanager.exec.Authorizable
import dev.rebootmanager.exec.CommandExecutor

/**
 * The only entry point the UI uses. It never runs shell commands itself and never
 * reboots on its own: [reboot] must be called as a result of an explicit user action.
 */
class RebootManager(
    private val detector: CapabilityDetector,
    private val executors: Map<ExecutionMethod, CommandExecutor>,
    private val commands: RebootCommandProvider,
) {
    suspend fun detect(): Capabilities = detector.detect()

    suspend fun reboot(mode: RebootMode, preferred: ExecutionMethod): RebootOutcome {
        // Re-detect: the state shown on screen may be stale.
        val caps = detector.detect()

        val support = caps.modes[mode]
        if (support != null && !support.supported) {
            return RebootOutcome.Failed(support.note ?: "This mode is not supported on this device.")
        }

        val method = caps.resolve(preferred)
            ?: return RebootOutcome.Failed(unavailableMessage(preferred, caps))
        val executor = executors[method]
            ?: return RebootOutcome.Failed("No executor registered for $method.")

        val candidates = commands.candidates(mode, caps.sdkInt)
        if (candidates.isEmpty()) {
            return RebootOutcome.Failed("No command is known for this mode on Android ${caps.sdkInt}.")
        }

        var lastError = "Command failed."
        for (candidate in candidates) {
            when (val result = executor.execute(candidate.shell)) {
                is ExecResult.Success -> return RebootOutcome.Sent(method)
                is ExecResult.Failure -> lastError = result.message
            }
        }
        return RebootOutcome.Failed(lastError)
    }

    /** Asks the device to trust this app (ADB methods only). May show a dialog on the device. */
    suspend fun authorize(method: ExecutionMethod): ExecResult {
        val executor = executors[method] as? Authorizable
            ?: return ExecResult.Failure("$method does not need authorization.")
        return executor.authorize()
    }

    private fun unavailableMessage(preferred: ExecutionMethod, caps: Capabilities): String = when (preferred) {
        ExecutionMethod.AUTO ->
            "No execution method is available.\n" +
                "Grant root access, or connect this device through USB ADB or Wireless Debugging."
        ExecutionMethod.ROOT ->
            "Root access is not available.\n" + caps.root.lines.joinToString("\n")
        ExecutionMethod.USB_ADB, ExecutionMethod.WIRELESS_ADB -> {
            val status = caps.statusOf(preferred)
            when (status?.state) {
                MethodState.UNSUPPORTED -> status.lines.joinToString("\n")
                MethodState.CONNECTED ->
                    "ADB is reachable, but this app is not authorized yet.\nTap Authorize on the main screen."
                else ->
                    "ADB is not connected.\nConnect this device through USB ADB or Wireless Debugging."
            }
        }
    }
}
