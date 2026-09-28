package dev.rebootmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.rebootmanager.ui.MainViewModel
import dev.rebootmanager.ui.RebootManagerApp
import dev.rebootmanager.ui.theme.RebootManagerTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        viewModelFactory {
            initializer {
                val container = (application as RebootApp).container
                MainViewModel(container.rebootManager, container.settings)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RebootManagerTheme {
                RebootManagerApp(viewModel)
            }
        }
    }
}
