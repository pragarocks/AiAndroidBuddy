package com.pocketpet

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class PocketPetApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            val petChannel = NotificationChannel(
                CHANNEL_PET,
                "PocketPet",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "PocketPet overlay service notification"
                setShowBadge(false)
            }

            val downloadChannel = NotificationChannel(
                CHANNEL_DOWNLOAD,
                "Model Download",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "AI model download progress"
            }

            manager.createNotificationChannels(listOf(petChannel, downloadChannel))
        }
    }

    companion object {
        const val CHANNEL_PET = "pocketpet_pet"
        const val CHANNEL_DOWNLOAD = "pocketpet_download"
    }
}
