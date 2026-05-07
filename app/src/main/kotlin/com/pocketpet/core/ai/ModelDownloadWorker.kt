package com.pocketpet.core.ai

import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.pocketpet.PocketPetApp
import com.pocketpet.R
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

@HiltWorker
class ModelDownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val deviceChecker: DeviceCapabilityChecker
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_MODEL_FILENAME = "model_filename"
        const val KEY_BASE_URL = "base_url"
        private const val TAG = "ModelDownloadWorker"
        private const val NOTIF_ID = 2001
    }

    private val notifManager = context.getSystemService(NotificationManager::class.java)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val filename = inputData.getString(KEY_MODEL_FILENAME) ?: return@withContext Result.failure()
        val baseUrl = inputData.getString(KEY_BASE_URL) ?: return@withContext Result.failure()

        val destFile = File(applicationContext.filesDir, filename)
        if (destFile.exists() && destFile.length() > 0) {
            Log.i(TAG, "$filename already exists, skipping")
            return@withContext Result.success()
        }

        if (!deviceChecker.hasEnoughStorage()) {
            Log.e(TAG, "Not enough storage to download $filename")
            return@withContext Result.failure(
                Data.Builder().putString("error", "not_enough_storage").build()
            )
        }

        try {
            downloadFile("$baseUrl$filename", destFile, filename)
            Log.i(TAG, "$filename downloaded successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Download failed for $filename: ${e.message}")
            destFile.delete()
            Result.retry()
        }
    }

    private suspend fun downloadFile(url: String, dest: File, name: String) {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connect()
        val totalBytes = connection.contentLengthLong

        val notifBuilder = NotificationCompat.Builder(applicationContext, PocketPetApp.CHANNEL_DOWNLOAD)
            .setSmallIcon(R.drawable.ic_pet_notification)
            .setContentTitle("Downloading AI models")
            .setContentText(name)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        connection.inputStream.use { input ->
            dest.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var downloaded = 0L
                var lastNotifPct = -1
                var read: Int

                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    downloaded += read

                    if (totalBytes > 0) {
                        val pct = (downloaded * 100 / totalBytes).toInt()
                        if (pct != lastNotifPct && pct % 5 == 0) {
                            lastNotifPct = pct
                            notifBuilder.setProgress(100, pct, false)
                            notifManager.notify(NOTIF_ID, notifBuilder.build())
                        }
                    }
                }
            }
        }

        notifManager.cancel(NOTIF_ID)
        connection.disconnect()
    }
}
