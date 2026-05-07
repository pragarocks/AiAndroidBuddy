package com.pocketpet.ui.settings

import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketpet.core.personality.PetProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    profile: PetProfile,
    onRequestOverlayPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRequestAccessibilityPermission: () -> Unit,
    onStartPet: () -> Unit
) {
    val context = LocalContext.current
    val hasOverlay = Settings.canDrawOverlays(context)

    Scaffold(
        topBar = { TopAppBar(title = { Text("PocketPet") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            PetCard(profile)

            PermissionCard(
                title = "Screen Overlay",
                description = "Required to show your pet on screen",
                granted = hasOverlay,
                onRequest = onRequestOverlayPermission
            )

            PermissionCard(
                title = "Notification Access",
                description = "Required to read your notifications",
                granted = false,
                onRequest = onRequestNotificationPermission
            )

            PermissionCard(
                title = "Accessibility Service",
                description = "Required to dismiss or reply to notifications",
                granted = false,
                onRequest = onRequestAccessibilityPermission
            )

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = onStartPet,
                modifier = Modifier.fillMaxWidth(),
                enabled = hasOverlay
            ) {
                Text("Launch PocketPet 🐾", fontSize = 16.sp)
            }

            if (!hasOverlay) {
                Text(
                    "Grant screen overlay permission first",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PetCard(profile: PetProfile) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(petEmoji(profile.petId), fontSize = 48.sp)
            Column {
                Text(profile.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("${profile.species} · ${profile.speechStyle}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Peak: ${profile.peakStat.name} · Low: ${profile.dumpStat.name}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    granted: Boolean,
    onRequest: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = if (granted) Icons.Filled.Check else Icons.Filled.Warning,
                contentDescription = null,
                tint = if (granted) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!granted) {
                FilledTonalButton(onClick = onRequest) { Text("Grant") }
            }
        }
    }
}

private fun petEmoji(id: String) = when (id) {
    "boba" -> "🧋"
    "pixel" -> "👾"
    "cloud" -> "☁️"
    "ghost" -> "👻"
    else -> "🐾"
}
