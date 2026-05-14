package com.pocketpet.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pocketpet.core.personality.PetProfile
import com.pocketpet.ui.onboarding.OnboardingScreen
import com.pocketpet.ui.petpicker.PetPickerScreen
import com.pocketpet.ui.settings.DashboardScreen

private const val ROUTE_ONBOARDING  = "onboarding"
private const val ROUTE_DASHBOARD   = "dashboard"
private const val ROUTE_PET_PICKER  = "pet_picker"

@Composable
fun MainNavHost(
    uiState: MainUiState,
    onRequestOverlayPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRequestAccessibilityPermission: () -> Unit,
    onRequestUsagePermission: () -> Unit,
    onStartPet: () -> Unit,
    onStopPet: () -> Unit,
    onOnboardingComplete: (PetProfile) -> Unit
) {
    if (uiState.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val navController = rememberNavController()
    val startRoute = if (uiState.isOnboardingDone) ROUTE_DASHBOARD else ROUTE_ONBOARDING

    NavHost(navController = navController, startDestination = startRoute) {
        composable(ROUTE_ONBOARDING) {
            OnboardingScreen(
                onComplete = { profile ->
                    onOnboardingComplete(profile)
                    navController.navigate(ROUTE_DASHBOARD) {
                        popUpTo(ROUTE_ONBOARDING) { inclusive = true }
                    }
                }
            )
        }
        composable(ROUTE_DASHBOARD) {
            DashboardScreen(
                profile = uiState.profile,
                onRequestOverlayPermission = onRequestOverlayPermission,
                onRequestNotificationPermission = onRequestNotificationPermission,
                onRequestAccessibilityPermission = onRequestAccessibilityPermission,
                onRequestUsagePermission = onRequestUsagePermission,
                onStartPet = onStartPet,
                onStopPet  = onStopPet,
                onChangePet = { navController.navigate(ROUTE_PET_PICKER) }
            )
        }
        composable(ROUTE_PET_PICKER) {
            PetPickerScreen(onBack = { navController.popBackStack() })
        }
    }
}
