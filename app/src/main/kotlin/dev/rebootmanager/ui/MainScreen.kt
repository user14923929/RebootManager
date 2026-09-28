package dev.rebootmanager.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.rebootmanager.R
import dev.rebootmanager.core.Capabilities
import dev.rebootmanager.core.ExecutionMethod
import dev.rebootmanager.core.MethodState
import dev.rebootmanager.core.RebootMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(ui.message) {
        ui.message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    val caps = ui.capabilities
    val active = caps?.resolve(settings.method)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = viewModel::refresh) { Text("⟳", fontSize = 22.sp) }
                    IconButton(onClick = onOpenSettings) { Text("⚙", fontSize = 22.sp) }
                    IconButton(onClick = onOpenAbout) { Text("ⓘ", fontSize = 22.sp) }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (ui.refreshing || ui.busy) LinearProgressIndicator(Modifier.fillMaxWidth())

            SectionTitle(stringResource(R.string.section_connection))
            ConnectionCard(caps, active, onAuthorize = viewModel::authorize)

            SectionTitle(stringResource(R.string.section_reboot))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                RebootMode.entries.forEachIndexed { index, mode ->
                    if (index > 0) HorizontalDivider()
                    val support = caps?.modes?.get(mode)
                    val enabled = active != null && support?.supported != false && !ui.busy
                    ModeRow(mode, note = support?.note, enabled = enabled) { viewModel.onModeClicked(mode) }
                }
            }
            Text(
                stringResource(R.string.modes_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }
    }

    ui.pendingConfirm?.let { mode ->
        val text = mode.ui()
        AlertDialog(
            onDismissRequest = viewModel::dismissConfirm,
            title = { Text(stringResource(text.confirmTitle)) },
            text = { Text(stringResource(text.confirmBody)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmPending) { Text(stringResource(text.confirmAction)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissConfirm) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun ConnectionCard(
    caps: Capabilities?,
    active: ExecutionMethod?,
    onAuthorize: (ExecutionMethod) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (caps != null && active == null) {
                Text(
                    stringResource(R.string.unavailable_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Capabilities.AUTO_ORDER.forEachIndexed { index, method ->
                if (index > 0) HorizontalDivider()
                val status = caps?.statusOf(method)
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        val name = stringResource(method.titleRes())
                        val suffix = if (method == active) " · " + stringResource(R.string.active) else ""
                        Text(name + suffix, style = MaterialTheme.typography.titleSmall)
                        status?.lines?.forEach { line ->
                            Text(line, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    val adb = method != ExecutionMethod.ROOT
                    if (adb && status?.state == MethodState.CONNECTED) {
                        TextButton(onClick = { onAuthorize(method) }) {
                            Text(stringResource(R.string.authorize))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeRow(mode: RebootMode, note: String?, enabled: Boolean, onClick: () -> Unit) {
    val text = mode.ui()
    ListItem(
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.38f),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Text(text.emoji, fontSize = 22.sp) },
        headlineContent = { Text(stringResource(text.title)) },
        supportingContent = if (note != null) {
            { Text(note) }
        } else {
            null
        },
    )
}
