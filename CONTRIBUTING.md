# Contributing to babybuddy

Thank you for helping improve babybuddy. This project handles family data, media, storage credentials, and optional AI providers, so changes should be made with extra care.

## Development Setup

1. Install Android Studio or Android SDK command-line tools.
2. Use JDK 17 or newer.
3. Clone the repository.
4. Build the debug app:

```powershell
.\gradlew.bat :app:assembleDebug
```

On macOS or Linux:

```bash
./gradlew :app:assembleDebug
```

## Before Opening a Pull Request

- Run `:app:assembleDebug`.
- Do not commit `local.properties`, build outputs, APKs, screenshots, signing keys, private NAS URLs, API keys, passwords, or real family media.
- Use placeholder hosts such as `dav.example.com` in documentation and tests.
- Keep storage migrations backward-compatible when changing synced JSON models.
- Prefer small pull requests with a clear user-facing reason.

## Code Style

- Kotlin and Jetpack Compose are the primary app stack.
- Keep UI changes consistent with the app's Material 3 and Miuix-inspired visual direction.
- Avoid blocking the main thread during media, NAS, or AI operations.
- Prefer local-first behavior and safe sync conflict handling over destructive remote writes.

## Security and Privacy

If a change touches NAS/WebDAV, AI providers, credentials, or media storage, describe the privacy impact in the pull request. Security issues should follow [SECURITY.md](SECURITY.md).
