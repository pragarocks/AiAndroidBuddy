package com.pocketpet.core.pet

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages user-installed pets stored in filesDir/pets/<petId>/.
 * Active pet ID is persisted in DataStore.
 */
@Singleton
class InstalledPetRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>
) {
    private val KEY_INSTALLED_PET = stringPreferencesKey("installed_pet_id")

    private val petsDir: File
        get() = File(context.filesDir, "pets").also { it.mkdirs() }

    /** Flow of the currently selected installed pet ID (null = use bundled) */
    val activePetIdFlow: Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_INSTALLED_PET]
    }

    suspend fun getActivePetId(): String? = activePetIdFlow.first()

    suspend fun setActivePetId(petId: String) {
        dataStore.edit { prefs -> prefs[KEY_INSTALLED_PET] = petId }
    }

    suspend fun clearInstalledPet() {
        dataStore.edit { prefs -> prefs.remove(KEY_INSTALLED_PET) }
    }

    /** Directory for a specific installed pet */
    fun getInstalledDir(petId: String): File = File(petsDir, petId)

    /** List all installed pet IDs (folders in filesDir/pets/) */
    fun listInstalled(): List<String> =
        petsDir.listFiles { f -> f.isDirectory && File(f, "pet.json").exists() }
            ?.map { it.name }
            ?: emptyList()

    /** Delete an installed pet */
    suspend fun delete(petId: String) {
        getInstalledDir(petId).deleteRecursively()
        if (getActivePetId() == petId) clearInstalledPet()
    }
}
