# Yumemi

<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.webp" width="120" alt="Yumemi Logo"/>
</p>

<p align="center">
  <b>A feature-rich manga reader for Android</b>
</p>

<p align="center">
  <a href="https://github.com/AgentKush/Yumemi/releases"><img src="https://img.shields.io/github/v/release/AgentKush/Yumemi?style=flat-square" alt="Release"/></a>
  <a href="https://github.com/AgentKush/Yumemi/blob/devel/LICENSE"><img src="https://img.shields.io/github/license/AgentKush/Yumemi?style=flat-square" alt="License: GPL-3.0"/></a>
  <img src="https://img.shields.io/badge/Android-7.0%2B-brightgreen?style=flat-square" alt="Android 7.0+"/>
  <img src="https://img.shields.io/badge/Kotlin-2.2-blue?style=flat-square" alt="Kotlin"/>
</p>

---

Yumemi is a free and open-source manga reader for Android based on [Kotatsu](https://github.com/KotatsuApp/Kotatsu). It provides a clean, modern interface for reading manga from various online sources with powerful features for organizing and tracking your reading progress.

## Features

- **Multiple Sources** — Access manga from 1000+ sources via [kotatsu-parsers](https://github.com/YakaTeam/kotatsu-parsers)
- **Offline Reading** — Download chapters for reading without internet
- **Reading Progress Tracking** — Automatic history and bookmarks
- **Scrobbling** — Sync progress with MyAnimeList, AniList, Shikimori, and Kitsu
- **Local Manga** — Read downloaded CBZ/CBR/ZIP archives
- **Customizable Reader** — Multiple reading modes, color filters, and gestures
- **Material Design** — Modern UI following Material Design 3 guidelines
- **App Lock** — Protect the app with biometric or PIN authentication
- **Discord Rich Presence** — Share what you're reading on Discord
- **Tracker** — Get notified when new chapters are available
- **Categories & Favorites** — Organize your library your way
- **Search** — Find manga across all sources simultaneously

## Screenshots

<!-- Add screenshots here -->

## Download

Download the latest APK from the [Releases](https://github.com/AgentKush/Yumemi/releases) page.

### Build Variants

| Variant | Description |
|---------|-------------|
| **Release** | Optimized build with ProGuard minification |
| **Debug** | Development build with debugging enabled |
| **Nightly** | Daily builds with latest changes |

## Building from Source

### Prerequisites

- Android Studio Ladybug or later
- JDK 17 or later
- Android SDK 36

### Build Commands

```bash
# Clone the repository
git clone https://github.com/AgentKush/Yumemi.git
cd Yumemi

# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Build nightly APK
./gradlew assembleNightly

# Run tests
./gradlew test
```

### Output Locations

- Debug: `app/build/outputs/apk/debug/app-debug.apk`
- Release: `app/build/outputs/apk/release/app-release.apk`
- Nightly: `app/build/outputs/apk/nightly/app-nightly.apk`

## Tech Stack

- **Language:** Kotlin
- **Architecture:** MVVM with Clean Architecture
- **Dependency Injection:** Hilt
- **Database:** Room
- **Networking:** OkHttp + Coroutines
- **Image Loading:** Coil 3
- **Background Work:** WorkManager
- **UI:** Material Design 3, ViewBinding

## Project Structure

```
app/
├── src/main/kotlin/org/koitharu/kotatsu/
│   ├── bookmarks/       # Bookmark management
│   ├── browser/         # WebView browser for sources
│   ├── core/            # Core utilities, database, network
│   ├── details/         # Manga details screen
│   ├── download/        # Download management
│   ├── explore/         # Source exploration
│   ├── favourites/      # Favorites management
│   ├── filter/          # Search filters
│   ├── history/         # Reading history
│   ├── list/            # Manga list components
│   ├── local/           # Local manga handling
│   ├── main/            # Main activity and navigation
│   ├── reader/          # Manga reader
│   ├── scrobbling/      # External tracker integration
│   ├── search/          # Search functionality
│   ├── settings/        # App settings
│   ├── sync/            # Cloud sync
│   ├── tracker/         # Update tracker
│   └── widget/          # Home screen widgets
```

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

### Reporting Issues

- Check existing issues before creating a new one
- Include device info, Android version, and app version
- Provide steps to reproduce the issue
- Include screenshots or logs if applicable

## Acknowledgements

- [Kotatsu](https://github.com/KotatsuApp/Kotatsu) — Original project
- [YakaTeam/kotatsu-parsers](https://github.com/YakaTeam/kotatsu-parsers) — Manga source parsers
- All the contributors who help improve this project

## License

```
Copyright (C) 2020-2026 Yumemi Contributors

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program. If not, see <https://www.gnu.org/licenses/>.
```

---

<p align="center">Made with ❤️ for manga readers everywhere</p>
