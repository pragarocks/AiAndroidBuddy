package com.pocketpet.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pocketpet.overlay.PetOverlayService
import com.pocketpet.core.personality.PetProfileRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var profileRepository: PetProfileRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val done = profileRepository.isOnboardingDone.first()
                if (done) {
                    context.startForegroundService(Intent(context, PetOverlayService::class.java))
                }
            } finally {
                pending.finish()
            }
        }
    }
}
