# babybuddy

<img src="docs/assets/icon-light.png" alt="babybuddy icon" width="128" />

babybuddy is an open-source Android app for recording a baby's everyday growth, photos, videos, feeding logs, vaccines, and AI-assisted parenting notes. It is designed for families who want local-first data ownership with optional NAS/WebDAV sync.

## Highlights

- Native Android app built with Kotlin and Jetpack Compose.
- Baby profiles, timeline records, growth milestones, feeding logs, and vaccine status.
- Photo and video library with local thumbnails and remote media support.
- Optional NAS/WebDAV sync for JSON data, chat history, photos, and videos.
- AI assistant with configurable OpenAI-compatible providers and streaming responses.
- Local-first defaults: the app can be used without a cloud account.

## Status

This repository starts at version `1.0`. The app is usable, but still early-stage. Please keep backups of important family media and review the privacy notes before using it for real family data.

## Screens

- Home dashboard for baby overview and recent media.
- Timeline for growth events and daily notes.
- Photos for image/video browsing.
- AI assistant for contextual parenting conversations.
- Settings for AI providers and NAS/WebDAV storage.

## Tech Stack

- Kotlin
- Jetpack Compose + Material 3
- AndroidX Navigation
- DataStore / SharedPreferences for local snapshots and secure settings
- OkHttp WebDAV client
- Coil image loading
- Media3 ExoPlayer
- WorkManager

## Build

Requirements:

- Android Studio or Android SDK command-line tools
- JDK 17 or newer
- Android SDK with compile SDK 35

Build debug APK:

```powershell
.\gradlew.bat :app:assembleDebug
```

Install on a connected device:

```powershell
.\gradlew.bat :app:installDebug
```

## Configuration

babybuddy does not require hardcoded secrets. Configure AI providers and NAS/WebDAV from the app settings screen.

- See [docs/CONFIGURATION.md](docs/CONFIGURATION.md) for example settings.
- See [docs/NAS_SYNC_DESIGN.md](docs/NAS_SYNC_DESIGN.md) for the local-first WebDAV sync model.
- See [docs/MIUIX_DESIGN_PLAN.md](docs/MIUIX_DESIGN_PLAN.md) for the gradual Miuix-inspired design plan.
- See [docs/FONTS.md](docs/FONTS.md) for optional MiSans local font setup.
- See [docs/GITHUB_SETUP.md](docs/GITHUB_SETUP.md) for GitHub repository setup notes.
- See [docs/RELEASE_SIGNING.md](docs/RELEASE_SIGNING.md) for release signing guidance.
- See [PRIVACY.md](PRIVACY.md) for data and privacy notes.
- See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for third-party notices.
- See [CHANGELOG.md](CHANGELOG.md) for release history.

## Storage Model

By default, babybuddy keeps data on-device. When NAS/WebDAV is configured, it syncs structured JSON data and uploads media files under the configured remote path, for example:

```text
/babybuddy/
  data/
    babies.json
    timeline.json
    photos.json
    feeding.json
    vaccine_statuses.json
    ai_profiles.json
    chat_messages.json
    settings.json
  media/
    photos/
    videos/
```

API keys and storage passwords are intended to stay local on the device and should not be committed to this repository.

## Open Source Notes

Before publishing your own fork:

- Do not commit `local.properties`, `.gradle/`, `.kotlin/`, `.claude/`, build outputs, APKs, or personal screenshots.
- Do not hardcode private NAS domains, LAN IPs, usernames, passwords, or API keys.
- Do not commit optional font binaries unless you have verified their redistribution license.
- Use HTTPS for remote WebDAV whenever possible.
- Review `network_security_config.xml` before release builds.

## License

Apache License 2.0. See [LICENSE](LICENSE).
