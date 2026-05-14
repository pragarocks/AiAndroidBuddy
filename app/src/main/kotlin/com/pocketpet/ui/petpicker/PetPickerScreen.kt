package com.pocketpet.ui.petpicker

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetPickerScreen(
    onBack: () -> Unit,
    viewModel: PetPickerViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // SAF zip picker launcher
    val zipPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importZip(uri)
    }

    // Show snackbar on success/error
    LaunchedEffect(state.importError, state.importSuccess) {
        val msg = state.importError ?: state.importSuccess ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearMessages()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Choose Your Pet", fontWeight = FontWeight.Bold)
                        Text(
                            "${state.bundledPets.size + state.installedPets.size} companions",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { zipPickerLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed")) },
                icon = { Icon(Icons.Filled.Add, "Import") },
                text = { Text("Import .zip") },
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = if (state.importError != null)
                        MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.primaryContainer
                )
            }
        }
    ) { padding ->

        Box(modifier = Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 80.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ── Bundled Pets ──────────────────────────────────────────────
                item(span = { GridItemSpan(2) }) {
                    SectionHeader("🐾 Bundled Pets")
                }
                items(state.bundledPets) { petId ->
                    PetCard(
                        petId = petId,
                        isActive = petId == state.activePetId,
                        onSelect = { viewModel.selectPet(petId) }
                    )
                }

                // ── Installed Pets ─────────────────────────────────────────────
                if (state.installedPets.isNotEmpty()) {
                    item(span = { GridItemSpan(2) }) {
                        SectionHeader("📦 Installed Pets")
                    }
                    items(state.installedPets) { petId ->
                        PetCard(
                            petId = petId,
                            isActive = petId == state.activePetId,
                            onSelect = { viewModel.selectPet(petId) }
                        )
                    }
                }

                // ── Import hint if no installed pets ──────────────────────────
                if (state.installedPets.isEmpty()) {
                    item(span = { GridItemSpan(2) }) {
                        ImportHintCard()
                    }
                }
            }

            // Import progress overlay
            AnimatedVisibility(
                visible = state.isImporting,
                enter = fadeIn(), exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(shape = RoundedCornerShape(20.dp)) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator()
                            Text("Installing pet...", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
private fun PetCard(
    petId: String,
    isActive: Boolean,
    onSelect: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1.03f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "pet_card_scale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(onClick = onSelect),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(if (isActive) 8.dp else 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Pet avatar — colored gradient circle with emoji
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(petGradient(petId)),
                contentAlignment = Alignment.Center
            ) {
                Text(petEmoji(petId), fontSize = 36.sp)
            }

            Text(
                text = petDisplayName(petId),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = petSpecies(petId),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            if (isActive) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Active",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                OutlinedButton(
                    onClick = onSelect,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    Text("Select", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun ImportHintCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("📦", fontSize = 32.sp)
            Text(
                "Import a pet from openpets.dev",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Text(
                "Download any .zip from openpets.dev then tap \"Import .zip\" below",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ── Helpers ────────────────────────────────────────────────────────────────────

private fun petEmoji(id: String) = when (id) {
    "valkyrie"   -> "⚔️"
    "snuglet"    -> "🛌"
    "review-owl" -> "🦉"
    "nova-byte"  -> "🤖"
    "bitboy"     -> "🎮"
    "axobotl"    -> "🦎"
    else         -> "🐾"
}

private fun petDisplayName(id: String) = when (id) {
    "valkyrie"   -> "Valkyrie"
    "snuglet"    -> "Snuglet"
    "review-owl" -> "Review Owl"
    "nova-byte"  -> "Nova Byte"
    "bitboy"     -> "BitBoy"
    "axobotl"    -> "Axobotl"
    else         -> id.replace("-", " ").split(" ")
        .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
}

private fun petSpecies(id: String) = when (id) {
    "valkyrie"   -> "Warrior • Bold & fierce"
    "snuglet"    -> "Plush • Sleepy & cozy"
    "review-owl" -> "Owl • Wise code reviewer"
    "nova-byte"  -> "Cyber Robot • Cheerful & bright"
    "bitboy"     -> "Game Boy • Retro & playful"
    "axobotl"    -> "Axolotl • Cute & bubbly"
    else         -> "Custom • OpenPets"
}

@Composable
private fun petGradient(id: String): Brush = when (id) {
    "valkyrie"   -> Brush.radialGradient(listOf(Color(0xFFE8EAF6), Color(0xFF5C6BC0)))
    "snuglet"    -> Brush.radialGradient(listOf(Color(0xFFFCE4EC), Color(0xFFF48FB1)))
    "review-owl" -> Brush.radialGradient(listOf(Color(0xFFFFF8E1), Color(0xFFFFCA28)))
    "nova-byte"  -> Brush.radialGradient(listOf(Color(0xFFE3F2FD), Color(0xFF42A5F5)))
    "bitboy"     -> Brush.radialGradient(listOf(Color(0xFFE8F5E9), Color(0xFF66BB6A)))
    "axobotl"    -> Brush.radialGradient(listOf(Color(0xFFE0F7FA), Color(0xFF26C6DA)))
    else         -> Brush.radialGradient(listOf(Color(0xFFF5F5F5), Color(0xFF9E9E9E)))
}

