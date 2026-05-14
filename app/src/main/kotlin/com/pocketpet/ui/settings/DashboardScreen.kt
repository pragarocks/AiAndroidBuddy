package com.pocketpet.ui.settings

import android.app.ActivityManager
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.pocketpet.core.personality.PetProfile

private val GradientTop    = Color(0xFF1A1A2E)
private val GradientMid    = Color(0xFF16213E)
private val GradientAccent = Color(0xFF0F3460)
private val NeonGreen      = Color(0xFF00E676)
private val NeonPurple     = Color(0xFFBA68C8)
private val CardBg         = Color(0xFF1E2A4A)

@Composable
fun DashboardScreen(
    profile: PetProfile,
    onRequestOverlayPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRequestAccessibilityPermission: () -> Unit,
    onRequestUsagePermission: () -> Unit,
    onStartPet: () -> Unit,
    onStopPet: () -> Unit,
    onChangePet: () -> Unit
) {
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasOverlay    by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var hasUsageStats by remember { mutableStateOf(checkUsageStatsPermission(context)) }
    var isPetRunning  by remember { mutableStateOf(isPetServiceRunning(context)) }

    // Re-check on every resume (user returning from Settings)
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasOverlay    = Settings.canDrawOverlays(context)
                hasUsageStats = checkUsageStatsPermission(context)
                isPetRunning  = isPetServiceRunning(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val allRequiredGranted = hasOverlay

    // Pulse animation for the pet emoji
    val infiniteTransition = rememberInfiniteTransition(label = "pet_pulse")
    val petScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.12f, label = "scale",
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(GradientTop, GradientMid, GradientAccent)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Animated pet hero ─────────────────────────────────────────────
            Text(
                text = petEmoji(profile.petId),
                fontSize = 88.sp,
                modifier = Modifier.scale(petScale)
            )
            Text(
                text = profile.name,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
            Text(
                text = "Your pocket companion is ready",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            // ── Pet info card ─────────────────────────────────────────────────
            GlassCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Box(
                        Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(NeonPurple.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) { Text(petEmoji(profile.petId), fontSize = 30.sp) }

                    Column(Modifier.weight(1f)) {
                        Text(profile.name, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 17.sp)
                        Text(
                            "${profile.species}  ·  ${profile.speechStyle}",
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 13.sp
                        )
                    }
                    FilledTonalButton(
                        onClick = onChangePet,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = NeonPurple.copy(alpha = 0.25f),
                            contentColor   = NeonPurple
                        )
                    ) { Text("Change", fontSize = 13.sp) }
                }
            }

            // ── Permissions ───────────────────────────────────────────────────
            Text(
                "Permissions",
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 13.sp,
                modifier = Modifier.align(Alignment.Start)
            )

            PermRow(
                emoji = "🪟", title = "Screen Overlay",
                desc  = "Show pet on top of other apps",
                granted = hasOverlay, required = true,
                onGrant = onRequestOverlayPermission
            )
            PermRow(
                emoji = "📊", title = "Screen Time Access",
                desc  = "Smart nudges based on app usage",
                granted = hasUsageStats, required = false,
                onGrant = onRequestUsagePermission
            )
            PermRow(
                emoji = "🔔", title = "Notification Access",
                desc  = "Let pet see your notifications",
                granted = false, required = false,
                onGrant = onRequestNotificationPermission
            )

            Spacer(Modifier.height(8.dp))

            // ── Launch / Stop buttons ──────────────────────────────────────────
            if (isPetRunning) {
                // Pet is live — show Stop button prominently
                Button(
                    onClick = { onStopPet(); isPetRunning = false },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF5252),
                        contentColor   = Color.White
                    )
                ) {
                    Text("Stop PocketPet", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                }
            } else {
                val btnColor by animateColorAsState(
                    targetValue = if (allRequiredGranted) NeonGreen else Color.Gray, label = "btn_color"
                )
                Button(
                    onClick = { onStartPet(); isPetRunning = true },
                    enabled = allRequiredGranted,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = btnColor, contentColor = Color(0xFF0D1117)
                    )
                ) {
                    Text(
                        if (allRequiredGranted) "Launch PocketPet" else "Grant Overlay Permission First",
                        fontWeight = FontWeight.ExtraBold, fontSize = 15.sp
                    )
                }
            }

            // ── Tips ──────────────────────────────────────────────────────────
            GlassCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("How to interact", fontWeight = FontWeight.Bold, color = NeonPurple, fontSize = 14.sp)
                    TipRow("👆 Tap",         "Say hello, pet reacts")
                    TipRow("👊 Rapid tap",   "Beat it — it runs away!")
                    TipRow("🎤 Hold 600ms",  "Talking Tom echo mode")
                    TipRow("😴 Hold 10s",    "Pet sleeps in corner")
                    TipRow("🏃 Drag",        "Move pet anywhere")
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

// ── Reusable components ──────────────────────────────────────────────────────

@Composable
private fun GlassCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg.copy(alpha = 0.85f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) { content() }
}

@Composable
private fun PermRow(
    emoji: String, title: String, desc: String,
    granted: Boolean, required: Boolean, onGrant: () -> Unit
) {
    val accentColor = if (granted) NeonGreen else if (required) Color(0xFFFF6B6B) else Color(0xFFFFB74D)

    GlassCard {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Status dot
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                if (granted) {
                    Icon(Icons.Filled.Check, null, tint = NeonGreen, modifier = Modifier.size(20.dp))
                } else {
                    Text(emoji, fontSize = 18.sp)
                }
            }

            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontWeight = FontWeight.SemiBold, color = Color.White, fontSize = 14.sp)
                    if (required && !granted) {
                        Text("Required", fontSize = 10.sp, color = Color(0xFFFF6B6B),
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFFFF6B6B).copy(alpha = 0.15f))
                                .padding(horizontal = 4.dp, vertical = 1.dp))
                    }
                }
                Text(desc, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
            }

            if (!granted) {
                FilledTonalButton(
                    onClick = onGrant,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = accentColor.copy(alpha = 0.2f),
                        contentColor   = accentColor
                    ),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(Icons.Filled.Lock, null, modifier = Modifier.size(14.dp))
                    Text(" Grant", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun TipRow(gesture: String, action: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(gesture, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(0.45f))
        Text(action,  color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp, modifier = Modifier.weight(0.55f))
    }
}

private fun petEmoji(id: String) = when (id) {
    "valkyrie"   -> "⚔️"
    "snuglet"    -> "🛌"
    "review-owl" -> "🦉"
    "nova-byte"  -> "🤖"
    "bitboy"     -> "🎮"
    "axobotl"    -> "🦎"
    else         -> "🐾"
}

private fun checkUsageStatsPermission(context: android.content.Context): Boolean {
    val mgr = context.getSystemService(android.content.Context.USAGE_STATS_SERVICE)
            as? android.app.usage.UsageStatsManager ?: return false
    return try {
        val now = System.currentTimeMillis()
        !mgr.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_DAILY,
            now - 86_400_000L, now).isNullOrEmpty()
    } catch (_: Exception) { false }
}

@Suppress("DEPRECATION")
private fun isPetServiceRunning(context: android.content.Context): Boolean {
    val am = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as? ActivityManager
        ?: return false
    return am.getRunningServices(50).any {
        it.service.className == "com.pocketpet.overlay.PetOverlayService"
    }
}
