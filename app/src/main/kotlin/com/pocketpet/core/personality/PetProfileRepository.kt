package com.pocketpet.core.personality

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PetProfileRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val KEY_PET_ID = stringPreferencesKey("pet_id")
    private val KEY_PET_NAME = stringPreferencesKey("pet_name")
    private val KEY_SPECIES = stringPreferencesKey("species")
    private val KEY_PEAK_STAT = stringPreferencesKey("peak_stat")
    private val KEY_DUMP_STAT = stringPreferencesKey("dump_stat")
    private val KEY_SPEECH_STYLE = stringPreferencesKey("speech_style")
    private val KEY_PROMPT_SUFFIX = stringPreferencesKey("prompt_suffix")
    private val KEY_ONBOARDING_DONE = stringPreferencesKey("onboarding_done")

    val profile: Flow<PetProfile> = dataStore.data.map { prefs ->
        val petId = prefs[KEY_PET_ID] ?: return@map PetProfile.DEFAULT
        PetProfile(
            petId = petId,
            name = prefs[KEY_PET_NAME] ?: "Boba",
            species = prefs[KEY_SPECIES] ?: "blob",
            peakStat = prefs[KEY_PEAK_STAT]?.let { runCatching { PetStat.valueOf(it) }.getOrNull() } ?: PetStat.CARE,
            dumpStat = prefs[KEY_DUMP_STAT]?.let { runCatching { PetStat.valueOf(it) }.getOrNull() } ?: PetStat.SNARK,
            speechStyle = prefs[KEY_SPEECH_STYLE] ?: "short and playful",
            systemPromptSuffix = prefs[KEY_PROMPT_SUFFIX] ?: ""
        )
    }

    val isOnboardingDone: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_ONBOARDING_DONE] == "true"
    }

    suspend fun saveProfile(profile: PetProfile) {
        dataStore.edit { prefs ->
            prefs[KEY_PET_ID] = profile.petId
            prefs[KEY_PET_NAME] = profile.name
            prefs[KEY_SPECIES] = profile.species
            prefs[KEY_PEAK_STAT] = profile.peakStat.name
            prefs[KEY_DUMP_STAT] = profile.dumpStat.name
            prefs[KEY_SPEECH_STYLE] = profile.speechStyle
            prefs[KEY_PROMPT_SUFFIX] = profile.systemPromptSuffix
        }
    }

    suspend fun markOnboardingDone() {
        dataStore.edit { prefs -> prefs[KEY_ONBOARDING_DONE] = "true" }
    }
}
