package org.user14923929.rebootmanager.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.user14923929.rebootmanager.R
import org.user14923929.rebootmanager.core.ExecutionMethod

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val caps = ui.capabilities

    Scaffold(topBar = { SimpleTopBar(stringResource(R.string.settings), onBack) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.settings_method),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            ExecutionMethod.entries.forEach { method ->
                val status = caps?.statusOf(method)
                val selected = settings.method == method
                // AUTO is always selectable. A concrete method is selectable only when it is ready
                // (or already selected, so the user can always leave it).
                val enabled = method == ExecutionMethod.AUTO || status?.isReady == true || selected
                val description = when (method) {
                    ExecutionMethod.AUTO -> stringResource(R.string.method_auto_desc)
                    else -> status?.lines?.joinToString("\n").orEmpty()
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(if (enabled) 1f else 0.5f)
                        .clickable(enabled = enabled) { viewModel.setMethod(method) },
                    verticalAlignment = Alignment.Top,
                ) {
                    RadioButton(selected = selected, onClick = null, enabled = enabled, modifier = Modifier.padding(top = 4.dp, end = 12.dp))
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Text(stringResource(method.titleRes()), style = MaterialTheme.typography.bodyLarge)
                        if (description.isNotBlank()) {
                            Text(
                                description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Text(
                stringResource(R.string.settings_behavior),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 16.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_confirm_restart), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.settings_confirm_restart_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = settings.confirmRestart, onCheckedChange = viewModel::setConfirmRestart)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_pair), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.settings_pair_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = viewModel::openPairingDialog, enabled = !ui.busy) {
                    Text(stringResource(R.string.settings_pair))
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (ui.pairingDialogOpen) {
        PairingDialog(onDismiss = viewModel::dismissPairingDialog, onPair = viewModel::pairWireless)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PairingDialog(onDismiss: () -> Unit, onPair: (port: Int, code: String) -> Unit) {
    var portText by rememberSaveable { mutableStateOf("") }
    var codeText by rememberSaveable { mutableStateOf("") }
    val port = portText.toIntOrNull()
    val canPair = port != null && port in 1..65535 && codeText.length == 6

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pair_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.pair_dialog_body), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = portText,
                    onValueChange = { portText = it.filter(Char::isDigit).take(5) },
                    label = { Text(stringResource(R.string.pair_dialog_port)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = codeText,
                    onValueChange = { codeText = it.filter(Char::isDigit).take(6) },
                    label = { Text(stringResource(R.string.pair_dialog_code)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onPair(port!!, codeText) }, enabled = canPair) {
                Text(stringResource(R.string.pair))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
