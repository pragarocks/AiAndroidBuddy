package com.pocketpet.ui.main

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.pocketpet.overlay.PetOverlayService
import com.pocketpet.services.PetBrainService
import com.pocketpet.ui.theme.PocketPetTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Request RECORD_AUDIO automatically
    private val audioPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted state read in PetBrainService */ }

    // Request POST_NOTIFICATIONS (Android 13+)
    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted state read by system */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Auto-request runtime permissions on first launch
        audioPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            PocketPetTheme {
                val vm: MainViewModel = hiltViewModel()
                val state by vm.uiState.collectAsState()

                MainNavHost(
                    uiState = state,
                    onRequestOverlayPermission   = { requestOverlayPermission() },
                    onRequestNotificationPermission = { openNotificationSettings() },
                    onRequestAccessibilityPermission = { openAccessibilitySettings() },
                    onRequestUsagePermission     = { openUsageSettings() },
                    onStartPet                   = { startPetService() },
                    onStopPet                    = { stopPetService() },
                    onOnboardingComplete         = { vm.completeOnboarding(it) }
                )
            }
        }
    }

    private fun requestOverlayPermission() {
        startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"))
        )
    }

    private fun openNotificationSettings() {
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun openUsageSettings() {
        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    private fun startPetService() {
        startForegroundService(Intent(this, PetOverlayService::class.java))
        startForegroundService(Intent(this, PetBrainService::class.java))
        finish()
    }

    private fun stopPetService() {
        stopService(Intent(this, PetOverlayService::class.java))
        stopService(Intent(this, PetBrainService::class.java))
    }
}
