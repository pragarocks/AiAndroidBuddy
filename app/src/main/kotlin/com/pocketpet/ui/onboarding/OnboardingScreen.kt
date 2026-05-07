package com.pocketpet.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketpet.core.personality.PetProfile
import com.pocketpet.core.personality.PetStat

private val PET_OPTIONS = listOf(
    "boba" to "Boba",
    "pixel" to "Pixel",
    "cloud" to "Cloudy",
    "ghost" to "Ghostie"
)

private val STAT_OPTIONS = listOf(
    PetStat.CARE to "💖 Care" to "Warm and protective",
    PetStat.WISDOM to "🦉 Wisdom" to "Thoughtful and calm",
    PetStat.SNARK to "😏 Snark" to "Witty and sarcastic",
    PetStat.CHAOS to "⚡ Chaos" to "Wild and energetic",
    PetStat.PATIENCE to "🌿 Patience" to "Gentle and steady"
)

@Composable
fun OnboardingScreen(onComplete: (PetProfile) -> Unit) {
    var step by remember { mutableStateOf(0) }
    var selectedPetId by remember { mutableStateOf("boba") }
    var petName by remember { mutableStateOf("Boba") }
    var peakStat by remember { mutableStateOf(PetStat.CARE) }
    var dumpStat by remember { mutableStateOf(PetStat.SNARK) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        AnimatedContent(targetState = step, label = "onboarding_step") { currentStep ->
            when (currentStep) {
                0 -> WelcomeStep()
                1 -> PetPickerStep(selectedPetId, onSelect = { selectedPetId = it })
                2 -> NameStep(petName, onChange = { petName = it })
                3 -> StatStep("Pick your pet's best trait", peakStat, onSelect = { peakStat = it })
                4 -> StatStep("Pick your pet's weakest trait", dumpStat, onSelect = { dumpStat = it })
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (step > 0) {
                Button(onClick = { step-- }) { Text("Back") }
            } else {
                Spacer(Modifier)
            }

            Button(
                onClick = {
                    if (step < 4) {
                        step++
                    } else {
                        val profile = PetProfile.fromStats(
                            petId = selectedPetId,
                            name = petName.ifBlank { "Boba" },
                            species = "blob",
                            peakStat = peakStat,
                            dumpStat = dumpStat
                        )
                        onComplete(profile)
                    }
                },
                enabled = step != 2 || petName.isNotBlank()
            ) {
                Text(if (step == 4) "Let's go! 🎉" else "Next")
            }
        }
    }
}

@Composable
private fun WelcomeStep() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🐾", fontSize = 72.sp)
        Spacer(Modifier.height(16.dp))
        Text("Meet PocketPet", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Your AI companion that lives in your phone, reads your notifications, and talks to you.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PetPickerStep(selected: String, onSelect: (String) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Choose your pet", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(PET_OPTIONS) { (id, name) ->
                PetOption(id, name, selected == id, onClick = { onSelect(id) })
            }
        }
    }
}

@Composable
private fun PetOption(id: String, name: String, isSelected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                .border(
                    if (isSelected) 3.dp else 0.dp,
                    MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(20.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(petEmoji(id), fontSize = 40.sp)
        }
        Spacer(Modifier.height(8.dp))
        Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun NameStep(name: String, onChange: (String) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Name your pet", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { if (it.length <= 20) onChange(it) },
            label = { Text("Pet name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun StatStep(title: String, selected: PetStat, onSelect: (PetStat) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        STAT_OPTIONS.forEach { (statEmoji, description) ->
            val (stat, emoji) = statEmoji
            StatOption(
                label = emoji,
                description = description,
                isSelected = selected == stat,
                onClick = { onSelect(stat) }
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun StatOption(label: String, description: String, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun petEmoji(id: String) = when (id) {
    "boba" -> "🧋"
    "pixel" -> "👾"
    "cloud" -> "☁️"
    "ghost" -> "👻"
    else -> "🐾"
}
