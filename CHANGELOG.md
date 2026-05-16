# Changelog

All notable changes to babybuddy are documented here.

## 1.0.0 - 2026-05-16

Initial open-source release.

### Added

- Native Android app built with Kotlin and Jetpack Compose.
- Baby dashboard, timeline, feeding logs, vaccine status, growth records, photos, and videos.
- Local-first data model with optional NAS/WebDAV synchronization.
- Host-independent remote media paths for switching between LAN and public NAS endpoints.
- AI assistant with configurable OpenAI-compatible providers and local API key storage.
- Miuix-inspired visual layer, motion helpers, launcher icons, and optional MiSans local font support.
- Open-source repository shell: README, Apache 2.0 license, privacy policy, security policy, contribution guide, issue templates, and release checklist.

### Notes

- The package name remains `com.yueming.baby` for upgrade compatibility.
- Raw MiSans font files are intentionally not redistributed in source form.
- Debug builds allow cleartext traffic for LAN/NAS testing; release builds disable cleartext traffic by default.
