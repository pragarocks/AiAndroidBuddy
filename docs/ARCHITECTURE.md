# CLAUDE.md — PocketPet Android

> AI-powered pixel-art pet that lives on your Android screen, reads notifications, speaks to you, and listens back. Runs 100% on-device.

---

## Project Identity

**App name:** PocketPet (working title)
**Platform:** Android only (API 26+ / Android 8.0+)
**Primary language:** Kotlin
**Build system:** Gradle (Kotlin DSL)
**Min SDK:** 26 | **Target SDK:** 35
**Architecture:** Clean Architecture — UI · Domain · Data layers

---

## What This App Does

1. **Floating pixel-art pet** — always-on overlay rendered via `SurfaceView` + `Canvas`, animates from a Petdex-compatible `spritesheet.webp` + `pet.json`
2. **Notification reader** — `NotificationListenerService` buffers all notifications in real-time
3. **On-device LLM** — Qwen3.5-0.8B (MNN format, Q4) summarises, ranks, and responds to notifications
4. **Voice input (ASR)** — Whisper Small via ONNX Runtime — tap-and-hold pet or wake keyword
5. **Voice output (TTS)** — Kokoro 82M via ONNX Runtime — pet speaks summaries and replies
6. **Notification actions** — via `AccessibilityService`: dismiss, reply, open, snooze notifications

---

## Repository Layout

```
pocketpet/
├── CLAUDE.md                        ← you are here
├── app/
│   ├── src/main/
│   │   ├── kotlin/com/pocketpet/
│   │   │   ├── core/
│   │   │   │   ├── pet/             ← PetLoader, PetState, SpriteAnimator
│   │   │   │   ├── ai/              ← LlmEngine, AsrEngine, TtsEngine
│   │   │   │   ├── notifications/   ← NotificationRepository, NotificationItem
│   │   │   │   └── personality/     ← PetPersonality, PetProfile
│   │   │   ├── overlay/
│   │   │   │   ├── PetOverlayService.kt     ← WindowManager service
│   │   │   │   ├── PetSurfaceView.kt        ← SurfaceView + Canvas renderer
│   │   │   │   └── SpeechBubbleView.kt      ← popup text/speech bubbles
│   │   │   ├── services/
│   │   │   │   ├── PetNotificationService.kt  ← NotificationListenerService
│   │   │   │   ├── PetAccessibilityService.kt ← AccessibilityService
│   │   │   │   └── PetBrainService.kt         ← foreground service coordinating AI
│   │   │   ├── ui/
│   │   │   │   ├── onboarding/      ← pet picker, name, personality
│   │   │   │   ├── settings/        ← Compose settings screen
│   │   │   │   └── main/            ← launcher / dashboard
│   │   │   └── PocketPetApp.kt      ← Application class, DI init
│   │   ├── jni/
│   │   │   └── mnn_bridge.cpp       ← JNI bridge → MNN LLM inference
│   │   ├── assets/
│   │   │   ├── pets/                ← bundled pet packs (pet.json + spritesheet.webp)
│   │   │   └── models/              ← placeholder; models downloaded post-install
│   │   └── res/
│   ├── CMakeLists.txt               ← native build for MNN JNI bridge
│   └── build.gradle.kts
├── models/                          ← gitignored; downloaded at runtime
│   ├── qwen3.5-0.8b-q4.mnn
│   ├── whisper-small-int8.onnx
│   └── kokoro-int8.onnx
└── gradle/
    └── libs.versions.toml           ← version catalog
```

---

## Technology Stack

### Language & UI

| Layer | Technology |
|---|---|
| App logic | **Kotlin** (coroutines, Flow, StateFlow) |
| Settings / Onboarding UI | **Jetpack Compose** + Material 3 |
| Pet rendering | **SurfaceView** + `Canvas` (dedicated render thread) |
| DI | **Hilt** |
| Async | **Kotlin Coroutines** + `Dispatchers.IO` / `Default` |
| Navigation | Compose Navigation |

### On-Device AI

| Component | Model | Runtime | Format | Size |
|---|---|---|---|---|
| LLM | Qwen3.5-0.8B | **MNN** (via JNI) | `.mnn` Q4 asym | ~500MB |
| ASR | Whisper Small | **ONNX Runtime** (NNAPI EP) | `.onnx` int8 | ~150MB |
| TTS | Kokoro 82M | **ONNX Runtime** | `.onnx` int8 fp16 | ~80MB |

**Why MNN for the LLM:** Alibaba's own stack, officially supports Qwen3 MoE models, has SME/OpenCL kernels tuned for ARM, converts GGUF→MNN natively, and delivers the best energy/latency on Snapdragon + Dimensity.

**Why ONNX Runtime for ASR/TTS:** NNAPI Execution Provider routes to NPU/GPU automatically on Android 8.1+. Single `.onnx` file, no custom ops needed for Whisper or Kokoro.

**Why NOT TFLite/LiteRT for LLM:** LiteRT-LM is excellent for Gemma but Qwen3.5 MNN support is more mature and ships with Alibaba's own quantisation tooling.

### Android System APIs

| Feature | API |
|---|---|
| Always-on overlay | `WindowManager` + `TYPE_APPLICATION_OVERLAY` |
| Notification reading | `NotificationListenerService` |
| Notification actions (dismiss/reply) | `AccessibilityService` |
| Microphone (ASR) | `AudioRecord` (16kHz, PCM 16-bit) |
| Audio playback (TTS) | `AudioTrack` (24kHz, PCM float) |
| Foreground AI service | `Service` with `FOREGROUND_SERVICE_TYPE_MICROPHONE` |
| Model download | `WorkManager` + `DownloadManager` |

---

## Pet System

### Petdex Format (canonical)

Each pet pack is two files:

```
pets/boba/
├── pet.json          ← name, states, frame durations
└── spritesheet.webp  ← horizontal strip, 192×208px per cell
```

**pet.json schema:**
```json
{
  "name": "Boba",
  "species": "blob",
  "author": "crafter-station",
  "frameSize": { "width": 192, "height": 208 },
  "states": {
    "idle":     { "row": 0, "frames": 4, "fps": 4 },
    "thinking": { "row": 1, "frames": 6, "fps": 8 },
    "working":  { "row": 2, "frames": 6, "fps": 8 },
    "sleeping": { "row": 3, "frames": 4, "fps": 2 },
    "success":  { "row": 4, "frames": 5, "fps": 10 },
    "error":    { "row": 5, "frames": 4, "fps": 6 },
    "waiting":  { "row": 6, "frames": 4, "fps": 5 },
    "excited":  { "row": 7, "frames": 6, "fps": 12 },
    "running":  { "row": 8, "frames": 6, "fps": 10 }
  }
}
```

### State Machine

```
Android Event                    →   Pet State
─────────────────────────────────────────────────
Default / idle                   →   idle
New notification arrived         →   excited
User says "summarise"            →   working (LLM)
LLM generating                   →   thinking
LLM speaking response            →   success
Listening for voice              →   waiting
ASR/LLM error                    →   error
Night mode / DND on              →   sleeping
Background task running          →   running
```

### Personality Profile

Generated once at first launch, stored in `DataStore`:

```kotlin
data class PetProfile(
    val name: String,           // e.g. "Boba"
    val species: String,        // from Petdex
    val peakStat: PetStat,      // PATIENCE | SNARK | WISDOM | CHAOS | CARE
    val dumpStat: PetStat,
    val speechStyle: String,    // "short and playful" | "sarcastic" | "calm"
    val systemPromptSuffix: String  // injected into LLM prompt
)
```

---

## AI Integration

### LLM — MNN Bridge

```
Kotlin ──► JNI (mnn_bridge.cpp) ──► MNN C++ Runtime ──► Qwen3.5-0.8B
```

**JNI interface (`MnnLlmEngine.kt`):**
```kotlin
class MnnLlmEngine(private val modelPath: String) {
    external fun nativeInit(modelPath: String): Long
    external fun nativeGenerate(handle: Long, prompt: String, maxTokens: Int): String
    external fun nativeDestroy(handle: Long)

    private var handle: Long = 0

    fun load() { handle = nativeInit(modelPath) }
    fun generate(prompt: String, maxTokens: Int = 200): Flow<String> = flow {
        emit(nativeGenerate(handle, prompt, maxTokens))
    }.flowOn(Dispatchers.Default)
}
```

**System prompt template:**
```
You are [PET_NAME], a [SPECIES] living inside [USER_NAME]'s Android phone.
Personality: [SPEECH_STYLE]. Peak trait: [PEAK_STAT].
Rules:
- Respond in max 2 short sentences
- Use first person ("I noticed...", "Your...")
- Never mention being an AI
- If urgent notification: say so first
[PERSONALITY_SUFFIX]
```

**Notification summarisation prompt:**
```
Recent notifications (newest first):
[NOTIFICATION_LIST as JSON]

Task: Summarise the top 3 most important in 2 sentences.
Flag any that need immediate action. Stay in character as [PET_NAME].
```

### ASR — ONNX Whisper

```kotlin
class WhisperAsrEngine(private val modelPath: String) {
    // AudioRecord at 16kHz PCM16 → mel spectrogram → ONNX session
    // Returns transcription string via Flow
    fun startListening(): Flow<AsrResult>
    fun stopListening()
}
```

Trigger modes:
1. **Tap-and-hold** the floating pet
2. **Wake keyword** detection (lightweight keyword spotter runs always-on at ~2MB)
3. **Notification panel button** "Ask pet"

### TTS — ONNX Kokoro

```kotlin
class KokoraTtsEngine(private val modelPath: String) {
    // Input: text string
    // Output: PCM float32 audio at 24kHz → AudioTrack
    suspend fun speak(text: String, speed: Float = 1.1f)
    fun stop()
}
```

Pet voice is `speed = 1.1f` (slightly fast, more energetic) with a higher pitch multiplier applied post-inference via `AudioTrack` sample rate trick.

---

## Notification Handling

### Data Model

```kotlin
data class NotificationItem(
    val id: String,
    val packageName: String,
    val appLabel: String,
    val title: String?,
    val text: String?,
    val timestamp: Long,
    val priority: Int,    // from Notification.priority
    val category: String? // Notification.CATEGORY_*
)
```

### Notification Buffer

Rolling circular buffer of last 50 notifications (configurable). Persisted in `Room` for session memory.

### Actions via AccessibilityService

```kotlin
sealed class NotificationAction {
    data class Dismiss(val notificationKey: String) : NotificationAction()
    data class Reply(val notificationKey: String, val text: String) : NotificationAction()
    data class Open(val notificationKey: String) : NotificationAction()
    data class Snooze(val notificationKey: String, val durationMs: Long) : NotificationAction()
}
```

The pet LLM can propose actions; the pet speaks them for confirmation before executing.

---

## Permissions Required

```xml
<!-- Overlay -->
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />

<!-- Notification reading -->
<uses-permission android:name="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE" />

<!-- Microphone for ASR -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />

<!-- Foreground service -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />

<!-- Accessibility for notification actions -->
<!-- Declared in AccessibilityService XML config, not manifest -->

<!-- Vibration for pet reactions -->
<uses-permission android:name="android.permission.VIBRATE" />

<!-- Keep CPU alive for AI inference -->
<uses-permission android:name="android.permission.WAKE_LOCK" />
```

**Permission flow:** Onboarding requests overlay → notification access → mic → accessibility in a guided multi-step screen. Pet reacts to each grant ("You can hear me now!").

---

## Model Distribution

Models are NOT bundled in the APK. They're downloaded post-install:

```
First launch flow:
1. Show onboarding + pet picker
2. Check storage (need ~800MB free)
3. WorkManager downloads models in background:
   - qwen3.5-0.8b-q4.mnn     (~500MB)  priority: high
   - whisper-small-int8.onnx (~150MB)  priority: high
   - kokoro-int8.onnx        (~80MB)   priority: medium
4. Pet shows "sleeping" state with progress bubble during download
5. On complete: pet wakes up and introduces itself via TTS
```

Model host: GitHub Releases or R2/S3 bucket (configurable via `BuildConfig.MODEL_BASE_URL`).

---

## Build Variants

| Variant | LLM Model | Description |
|---|---|---|
| `lite` | Qwen3.5-0.8B Q4 | ~500MB, 4GB+ RAM |
| `standard` | Qwen3.5-2B Q4 | ~1.4GB, 6GB+ RAM |
| `pro` | Qwen3.5-4B Q4 | ~2.5GB, 8GB+ RAM |

Variant selected at first launch based on detected device RAM. User can override in settings.

---

## Key Dependencies (`libs.versions.toml`)

```toml
[versions]
kotlin = "2.1.0"
compose-bom = "2025.04.00"
hilt = "2.55"
onnxruntime-android = "1.21.0"
room = "2.7.0"
work = "2.10.0"
coroutines = "1.9.0"

[libraries]
# Compose
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "compose-bom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }

# AI Runtime
onnxruntime-android = { group = "com.microsoft.onnxruntime", name = "onnxruntime-android", version.ref = "onnxruntime-android" }
# MNN pulled in via CMakeLists.txt / AAR from alibaba/MNN releases

# DI
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-android-compiler", version.ref = "hilt" }

# Persistence
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }

# Background
work-runtime-ktx = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "work" }

# Image loading (for pet spritesheet)
coil-compose = { group = "io.coil-kt.coil3", name = "coil-compose", version = "3.1.0" }

# JSON (pet.json parsing)
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version = "1.7.3" }
```

---

## MNN Native Setup (`CMakeLists.txt`)

```cmake
cmake_minimum_required(VERSION 3.22)
project(pocketpet_native)

# MNN prebuilt AAR ships libMNN.so — link against it
find_library(MNN_LIB MNN)

add_library(mnn_bridge SHARED jni/mnn_bridge.cpp)

target_link_libraries(mnn_bridge
    ${MNN_LIB}
    android
    log
)
```

Download MNN Android AAR from `https://github.com/alibaba/MNN/releases` and place in `app/libs/`.

---

## Coding Conventions

- All AI inference runs on `Dispatchers.Default` — never block the main thread
- Pet state transitions go through a single `PetStateMachine` (sealed class, StateFlow)
- `SurfaceView` has its own `HandlerThread` render loop at 30fps cap
- Speech bubble text is always ≤ 120 characters (enforced by `TtsEngine` pre-processing)
- Notification PII (phone numbers, email content) is never logged — debug mode strips it
- All `suspend` functions in AI layer have a 30-second timeout via `withTimeout`
- Use `@Stable` / `@Immutable` on Compose state holders
- Prefer `sealed interface` over `sealed class` for actions/events

---

## Development Milestones

### Phase 1 — Pet Lives (no AI)
- [ ] `PetOverlayService` with `WindowManager` overlay working
- [ ] `PetSurfaceView` loading `spritesheet.webp` and cycling `pet.json` states
- [ ] Manual state toggle via debug buttons
- [ ] Drag-to-reposition pet on screen
- [ ] Speech bubble popup with hardcoded text

### Phase 2 — Notifications
- [ ] `NotificationListenerService` capturing real notifications
- [ ] `Room` DB storing notification buffer
- [ ] Pet reacts to new notifications (idle → excited)
- [ ] Debug UI showing notification list

### Phase 3 — LLM Brain
- [ ] MNN AAR integrated + JNI bridge compiling
- [ ] Qwen3.5-0.8B model loading and generating text
- [ ] Notification summarisation prompt working
- [ ] Pet state cycles: working → thinking → success during inference
- [ ] Speech bubble shows LLM output

### Phase 4 — Voice
- [ ] ONNX Runtime for Android integrated
- [ ] Kokoro TTS: pet speaks the summary
- [ ] Whisper ASR: tap-and-hold records, transcribes
- [ ] Voice command "summarise" triggers summarisation flow
- [ ] Full voice loop: hear → understand → think → speak

### Phase 5 — Actions
- [ ] `AccessibilityService` enabled
- [ ] Dismiss notification by voice: "dismiss the Swiggy one"
- [ ] Reply to message by voice with LLM draft
- [ ] Pet asks for confirmation before acting
- [ ] "What's urgent?" priority ranking

### Phase 6 — Polish
- [ ] Petdex catalog browser in onboarding
- [ ] Model variant auto-detection by RAM
- [ ] Night mode (pet sleeps, DND respected)
- [ ] Battery optimisation (inference throttled below 20%)
- [ ] Widget / quick tile for quick summarise

---

## Common Claude Tasks

When asked to work on this project, Claude should:

**"Add a new voice command"**
→ Add intent pattern to `IntentRecognizer`, add handler in `PetBrainService`, add pet state transition, write unit test

**"Improve notification summarisation"**
→ Edit the prompt template in `LlmPromptBuilder.kt`, keep system prompt under 300 tokens, test with `NotificationSummarisationTest`

**"Add a new pet animation state"**
→ Add entry to `PetState` sealed class, update `pet.json` schema docs, update `SpriteAnimator` state mapping, update state machine

**"Fix jank in pet animation"**
→ Check `PetSurfaceView` render thread, ensure bitmap decoding is not on render thread, verify `invalidate()` cadence matches fps

**"The LLM is too slow"**
→ Check `MnnLlmEngine` thread allocation, reduce `maxTokens`, try reducing context window, check if model is loaded once vs. per-request

**"Add support for a new model size"**
→ Add build variant in `build.gradle.kts`, add model URL in `ModelConfig`, update RAM check in `DeviceCapabilityChecker`

---

## Project Philosophy

This is a **privacy-first, offline-first** app. Every decision should preserve that:

- No notification content ever leaves the device
- No telemetry or analytics by default
- AI models run entirely on-device
- Voice audio is processed in-memory and never written to disk
- Users own their pet — no account required to use core features

The pet should feel **alive, not robotic**. Its personality is consistent across sessions. It grumbles about too many notifications. It gets excited about messages from important contacts. It sleeps at night. Small, persistent personality beats impressive-but-cold AI every time.
