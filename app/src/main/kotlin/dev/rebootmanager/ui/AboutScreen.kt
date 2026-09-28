package dev.rebootmanager.ui

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.rebootmanager.BuildConfig
import dev.rebootmanager.R

private val LIBRARIES = listOf(
    "Kotlin & Kotlin Coroutines — Apache-2.0",
    "AndroidX Core KTX — Apache-2.0",
    "AndroidX Activity Compose — Apache-2.0",
    "AndroidX Lifecycle (ViewModel, Runtime Compose) — Apache-2.0",
    "Jetpack Compose UI & Material 3 — Apache-2.0",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(topBar = { SimpleTopBar(stringResource(R.string.about), onBack) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            Fact(stringResource(R.string.about_version), BuildConfig.VERSION_NAME)
            Fact(stringResource(R.string.about_sdk), "${Build.VERSION.SDK_INT} (Android ${Build.VERSION.RELEASE})")
            Fact(stringResource(R.string.about_license), stringResource(R.string.about_license_value))
            Text(stringResource(R.string.about_privacy), style = MaterialTheme.typography.bodyMedium)

            Text(
                stringResource(R.string.about_libraries),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp),
            )
            LIBRARIES.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
