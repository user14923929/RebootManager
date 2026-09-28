package dev.rebootmanager.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

private enum class Screen { MAIN, SETTINGS, ABOUT }

@Composable
fun RebootManagerApp(viewModel: MainViewModel) {
    var screen by rememberSaveable { mutableStateOf(Screen.MAIN) }

    BackHandler(enabled = screen != Screen.MAIN) { screen = Screen.MAIN }
    // Connection state changes outside the app (cable, Wi-Fi, root prompts), so re-check on every resume.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    when (screen) {
        Screen.MAIN -> MainScreen(
            viewModel = viewModel,
            onOpenSettings = { screen = Screen.SETTINGS },
            onOpenAbout = { screen = Screen.ABOUT },
        )
        Screen.SETTINGS -> SettingsScreen(viewModel = viewModel, onBack = { screen = Screen.MAIN })
        Screen.ABOUT -> AboutScreen(onBack = { screen = Screen.MAIN })
    }
}
