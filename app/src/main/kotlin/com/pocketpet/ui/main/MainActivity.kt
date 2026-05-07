package com.pocketpet.ui.main

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.pocketpet.overlay.PetOverlayService
import com.pocketpet.ui.theme.PocketPetTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PocketPetTheme {
                val vm: MainViewModel = hiltViewModel()
                val state by vm.uiState.collectAsState()

                MainNavHost(
                    uiState = state,
                    onRequestOverlayPermission = { requestOverlayPermission() },
                    onRequestNotificationPermission = { openNotificationSettings() },
                    onRequestAccessibilityPermission = { openAccessibilitySettings() },
                    onStartPet = { startPetService() },
                    onOnboardingComplete = { vm.completeOnboarding(it) }
                )
            }
        }
    }

    private fun requestOverlayPermission() {
        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
    }

    private fun openNotificationSettings() {
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun startPetService() {
        val intent = Intent(this, PetOverlayService::class.java)
        startForegroundService(intent)
        finish()
    }
}
