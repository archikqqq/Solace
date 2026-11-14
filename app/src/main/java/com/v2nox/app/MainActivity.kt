package com.v2nox.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.v2nox.app.ui.theme.V2NoxTheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val appearanceRepository = remember { AppearanceRepository(applicationContext) }
            val appearance by appearanceRepository.settings.collectAsStateWithLifecycle(initialValue = AppearanceSettings())
            V2NoxTheme(
                darkTheme = if (appearance.followSystem) isSystemInDarkTheme() else appearance.darkTheme,
                dynamicColor = appearance.dynamicColor,
            ) {
                MainScreen(viewModel)
            }
        }
    }
}
