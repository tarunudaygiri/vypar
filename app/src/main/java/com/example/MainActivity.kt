package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.AppShell
import com.example.ui.screens.OnboardingScreen
import com.example.ui.theme.VyaparDeskTheme
import com.example.ui.viewmodel.AppViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: AppViewModel = viewModel()
            val state by vm.uiState.collectAsState()

            VyaparDeskTheme(
                language = state.prefs.language,
                fontSizeOption = state.prefs.fontSize,
                darkTheme = state.prefs.darkTheme
            ) {
                when {
                    !state.isReady -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    !state.business.onboardingCompleted -> {
                        OnboardingScreen(
                            selectedLanguage = state.prefs.language,
                            selectedFontSize = state.prefs.fontSize,
                            onLanguageSelected = { vm.setLanguage(it) },
                            onFontSizeSelected = { vm.setFontSize(it) },
                            onComplete = { bizName, mob, addr, loadSample ->
                                vm.completeOnboarding(bizName, mob, addr, state.prefs.language, loadSample)
                            }
                        )
                    }
                    else -> {
                        AppShell(viewModel = vm)
                    }
                }
            }
        }
    }
}
