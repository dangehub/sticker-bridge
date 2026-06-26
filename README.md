# Sticker Bridge

Cross-platform sticker manager. Tap to send anywhere on Android, Windows, macOS & iOS.

## Project Status

**Prototype v0.1** — Android only:
- [x] Accessibility service detects QQ chat dialog
- [x] Floating draggable bubble overlay
- [x] Sticker picker with placeholder images
- [x] Direct share to QQ「Send to Friend」activity
- [ ] Load stickers from Eagle library / other sources
- [ ] Category & tag filtering
- [ ] Search
- [ ] WeChat support
- [ ] Windows clipboard integration
- [ ] macOS / iOS support

## Tech Stack

- **Platform:** Android (prototype), expanding to Windows, macOS, iOS
- **Language:** Kotlin
- **UI:** Jetpack Compose + Material 3
- **Image loading:** Coil
- **Accessibility:** AccessibilityService
- **Build:** Gradle + AGP 8.2.2
- **Data source:** Eagle SQLite + images (planned; extensible to other sources)

## Quick Start

```bash
git clone https://github.com/dangehub/sticker-bridge.git
cd sticker-bridge
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```
