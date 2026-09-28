package dev.rebootmanager.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import dev.rebootmanager.R
import dev.rebootmanager.core.ExecutionMethod

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

            var portText by rememberSaveable { mutableStateOf(settings.wirelessPort.toString()) }
            val port = portText.toIntOrNull()
            OutlinedTextField(
                value = portText,
                onValueChange = { value ->
                    portText = value.filter(Char::isDigit).take(5)
                    portText.toIntOrNull()?.takeIf { it in 1..65535 }?.let(viewModel::setWirelessPort)
                },
                label = { Text(stringResource(R.string.settings_wireless_port)) },
                supportingText = { Text(stringResource(R.string.settings_wireless_port_desc)) },
                isError = port == null || port !in 1..65535,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 16.dp),
            )
        }
    }
}
