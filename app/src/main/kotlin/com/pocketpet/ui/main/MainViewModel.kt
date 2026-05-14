package com.pocketpet.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketpet.core.personality.PetProfile
import com.pocketpet.core.personality.PetProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MainUiState(
    val isOnboardingDone: Boolean = false,
    val profile: PetProfile = PetProfile.DEFAULT,
    val modelsReady: Boolean = false,
    val isLoading: Boolean = true
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val profileRepository: PetProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                profileRepository.isOnboardingDone,
                profileRepository.profile
            ) { done, profile -> done to profile }
                .collect { (done, profile) ->
                    _uiState.update {
                        it.copy(
                            isOnboardingDone = done,
                            profile = profile,
                            isLoading = false
                        )
                    }
                }
        }
    }

    fun completeOnboarding(profile: PetProfile) {
        viewModelScope.launch {
            profileRepository.saveProfile(profile)
            profileRepository.markOnboardingDone()
        }
    }
}
