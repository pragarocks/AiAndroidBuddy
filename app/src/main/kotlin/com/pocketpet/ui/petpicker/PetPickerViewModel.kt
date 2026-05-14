package com.pocketpet.ui.petpicker

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketpet.core.pet.BUNDLED_PET_IDS
import com.pocketpet.core.pet.InstalledPetRepository
import com.pocketpet.core.pet.PetInstaller
import com.pocketpet.core.personality.PetProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PetPickerUiState(
    val bundledPets: List<String> = BUNDLED_PET_IDS,
    val installedPets: List<String> = emptyList(),
    val activePetId: String = "boba",
    val isImporting: Boolean = false,
    val importError: String? = null,
    val importSuccess: String? = null
)

@HiltViewModel
class PetPickerViewModel @Inject constructor(
    private val installedPetRepository: InstalledPetRepository,
    private val petInstaller: PetInstaller,
    private val profileRepository: PetProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PetPickerUiState())
    val uiState: StateFlow<PetPickerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            profileRepository.profile.collect { profile ->
                _uiState.update { it.copy(activePetId = profile.petId) }
            }
        }
        refreshInstalled()
    }

    private fun refreshInstalled() {
        viewModelScope.launch {
            val installed = installedPetRepository.listInstalled()
                .filter { id -> !BUNDLED_PET_IDS.contains(id) } // Don't duplicate bundled
            _uiState.update { it.copy(installedPets = installed) }
        }
    }

    fun selectPet(petId: String) {
        viewModelScope.launch {
            val current = profileRepository.profile.first()
            val updated = current.copy(petId = petId)
            profileRepository.saveProfile(updated)
            _uiState.update { it.copy(activePetId = petId) }
        }
    }

    fun importZip(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, importError = null, importSuccess = null) }
            petInstaller.installFromUri(uri)
                .onSuccess { petId ->
                    refreshInstalled()
                    selectPet(petId)
                    _uiState.update {
                        it.copy(isImporting = false, importSuccess = "Pet installed! 🎉")
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(isImporting = false, importError = "Import failed: ${err.message}")
                    }
                }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(importError = null, importSuccess = null) }
    }
}
