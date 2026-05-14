# PocketPet — AI Android Buddy

> An AI-powered pixel-art pet that lives on your Android screen, reads your notifications, speaks to you, and listens back. Runs **100% on-device** — no cloud, no accounts, completely private.

---

## What It Does

| Feature | How |
|---|---|
| Floating pixel-art pet | Always-on overlay, drag anywhere on screen |
| Reads your notifications | `NotificationListenerService` — real-time buffer |
| Summarises with AI | On-device Qwen3.5-0.8B via MNN |
| Speaks summaries | Kokoro 82M TTS via ONNX Runtime |
| Listens to you | Whisper Small ASR via ONNX Runtime |
| Acts on notifications | Dismiss / reply / open via `AccessibilityService` |
| Quick Settings Tile | One-tap AI summary from notification shade |

---

## Screenshots

> _(Coming soon — build and run to see your pet live!)_

---

## Requirements

| Requirement | Minimum |
|---|---|
| Android version | 8.0 (API 26) |
| RAM | 4 GB (lite model) · 6 GB (standard) · 8 GB (pro) |
| Free storage | ~800 MB for AI models |
| Build tools | Android Studio Hedgehog (2023.1.1) or newer |
| JDK | 17 |
| NDK | 27+ (for MNN JNI bridge) |

---

## Step-by-Step: Build & Run

### 1. Clone the repository

```bash
git clone https://github.com/pragarocks/AiAndroidBuddy.git
cd AiAndroidBuddy
```

### 2. Open in Android Studio

- Open **Android Studio**
- Choose **File → Open** and select the `AiAndroidBuddy` folder
- Wait for Gradle sync to complete (first sync may take 2–5 minutes)

### 3. Download the MNN Android AAR (required for on-device LLM)

The MNN runtime is **not bundled** in the repo due to size. Download it manually:

```bash
# Create the libs directory
mkdir -p app/libs

# Download MNN AAR from the official GitHub releases
# Visit: https://github.com/alibaba/MNN/releases
# Download: MNN-Android-<version>.aar
# Rename it to: MNN.aar
# Place it at: app/libs/MNN.aar
```

> **Skip this step for a quick test build.** Without `MNN.aar`, the app compiles and runs in **stub mode** — the pet animates and the UI works, but the LLM returns placeholder responses. All other features (notifications, ASR, TTS) work independently.

### 4. Select a build variant

In Android Studio's **Build Variants** panel (bottom-left):

| Variant | Model | Min RAM | Use when |
|---|---|---|---|
| `liteDebug` | Qwen3.5-0.8B Q4 (~500 MB) | 4 GB | Development / most phones |
| `standardDebug` | Qwen3.5-2B Q4 (~1.4 GB) | 6 GB | Better responses |
| `proDebug` | Qwen3.5-4B Q4 (~2.5 GB) | 8 GB | Best quality |

**Recommended for first run:** `liteDebug`

### 5. Connect a device or start an emulator

- **Physical device** (recommended): Enable **Developer Options → USB Debugging**, plug in via USB
- **Emulator**: API 26+, x86_64 image with at least 4 GB RAM allocated

> **Note:** The overlay permission (`SYSTEM_ALERT_WINDOW`) cannot be granted on most emulators. A physical Android device gives the best experience.

### 6. Build and install

Click the **Run ▶** button, or from terminal:

```bash
./gradlew installLiteDebug
```

### 7. Grant permissions (first launch)

The app guides you through a 5-step onboarding:

1. **Choose your pet** — pick Boba, Pixel, Cloudy, or Ghostie
2. **Name your pet**
3. **Set personality** (peak and dump stat)
4. **Dashboard** — grant permissions in order:
   - **Screen Overlay** → Settings opens automatically → toggle PocketPet on → back
   - **Notification Access** → toggle PocketPet on → back
   - **Accessibility Service** _(optional, for dismiss/reply actions)_ → toggle on → back
5. Tap **Launch PocketPet 🐾**

### 8. AI model download

On first launch after onboarding, models download in the background (~730 MB total):

```
qwen3.5-0.8b-q4.mnn   ~500 MB   LLM (summarisation, replies)
whisper-small-int8.onnx ~150 MB  Speech recognition
kokoro-int8.onnx        ~80 MB   Text-to-speech
```

The pet shows a **sleeping** animation with a progress bubble while downloading. Once complete, it **wakes up and introduces itself** via voice.

> If you want to pre-load models manually, place them in the app's files directory:
> `adb push <model.onnx> /sdcard/Android/data/com.pocketpet/files/`

### 9. Using the pet

| Action | Trigger |
|---|---|
| Hear notification summary | Say "summarise" or "what's new" |
| Hear urgent items only | Say "what's urgent" or "what's important" |
| Dismiss a notification | Say "dismiss the [app] notification" |
| Reply to a message | Say "reply to [name] saying [your reply]" |
| Start voice input | **Long-press** the pet (hold 400ms+) |
| Drag pet | **Tap and drag** anywhere |
| Quick summary | Pull down notification shade → tap **PocketPet tile** |
| Wake pet from sleep | Tap the sleeping pet |

---

## Project Structure

```
AiAndroidBuddy/
├── app/
│   ├── src/main/
│   │   ├── kotlin/com/pocketpet/
│   │   │   ├── core/
│   │   │   │   ├── pet/             PetState, PetStateMachine, SpriteAnimator, PetLoader
│   │   │   │   ├── ai/              LlmEngine, AsrEngine, TtsEngine, IntentRecognizer
│   │   │   │   ├── notifications/   NotificationItem, NotificationRepository (Room)
│   │   │   │   └── personality/     PetProfile, PetProfileRepository (DataStore)
│   │   │   ├── overlay/             PetOverlayService, PetSurfaceView, SpeechBubbleView
│   │   │   ├── services/            PetNotificationService, PetBrainService,
│   │   │   │                        PetAccessibilityService, QuickSummaryTileService
│   │   │   ├── receivers/           BootReceiver
│   │   │   ├── di/                  AppModule, AiModule (Hilt)
│   │   │   └── ui/                  Onboarding, Dashboard, Theme
│   │   ├── jni/
│   │   │   └── mnn_bridge.cpp       JNI → MNN C++ runtime
│   │   └── assets/pets/boba/        pet.json + spritesheet.webp
│   └── build.gradle.kts
├── docs/
│   └── ARCHITECTURE.md              Full technical spec
├── gradle/
│   └── libs.versions.toml
└── README.md
```

---

## Architecture

```
User Voice / Tap
       │
       ▼
PetOverlayService (WindowManager overlay)
       │ gesture events
       ▼
PetBrainService (foreground, coordinates AI)
       │
   ┌───┴────────────────────────────┐
   │                                │
   ▼                                ▼
WhisperAsrEngine              LlmPromptBuilder
(AudioRecord → ONNX)         (builds prompts)
                                    │
                                    ▼
                            MnnLlmEngine (JNI → MNN)
                                    │
                                    ▼
                            KokoraTtsEngine (ONNX → AudioTrack)
                                    │
                                    ▼
                        PetOverlayService.showBubble()
                        PetStateMachine.send(TtsStarted)
```

All AI runs on `Dispatchers.Default`. Pet state updates flow through `PetStateMachine` → `StateFlow` → `PetSurfaceView`.

---

## Privacy

- No notification content ever leaves the device
- No analytics, no telemetry, no accounts required
- Voice audio processed in-memory, never written to disk
- All AI inference on-device (MNN + ONNX Runtime)

---

## Technology Stack

| Layer | Technology |
|---|---|
| Language | Kotlin 2.1 |
| UI | Jetpack Compose + Material 3 |
| Pet rendering | SurfaceView + Canvas (HandlerThread, 30fps) |
| Dependency injection | Hilt 2.55 |
| LLM runtime | MNN (Alibaba) via JNI |
| ASR / TTS runtime | ONNX Runtime 1.21 + NNAPI |
| Database | Room 2.7 |
| Preferences | DataStore |
| Background | WorkManager 2.10 |
| Models | Qwen3.5-0.8B · Whisper Small · Kokoro 82M |

---

## Contributing

1. Fork the repo
2. Create a feature branch: `git checkout -b feature/my-thing`
3. Follow the [Architecture docs](docs/ARCHITECTURE.md)
4. Run tests: `./gradlew test`
5. Open a PR

---

## Roadmap

- [ ] Petdex catalog with downloadable pet packs
- [ ] Wake keyword detection (always-on, ~2MB spotter)
- [ ] Battery-aware inference throttling (pause below 20%)
- [ ] Night mode (auto-sleep on DND / scheduled hours)
- [ ] Widget for home screen quick summary
- [ ] Smart contact priority (learn important senders)
- [ ] Multiple pet slots

---

## License

MIT — see [LICENSE](LICENSE) for details.
