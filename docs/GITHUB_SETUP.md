# GitHub Setup

This repository is prepared to publish as `xyuuii/babybuddy`.

## Create the Repository

Create an empty public repository on GitHub:

```text
Owner: xyuuii
Repository name: babybuddy
Visibility: Public
Initialize with README: No
Add .gitignore: No
Choose a license: No
```

The local repository already includes README, license, CI, templates, and a clean `main` history.

## Push the Public History

Push only the clean public branch and release tag:

```powershell
git push -u origin main
git push origin v1.0.0
```

Do not use `git push --all` or broad `git push --tags`; local archive branches may contain private development history.

## Recommended Repository Settings

- About description: `Local-first Android baby journal with NAS/WebDAV sync and AI assistant.`
- Topics: `android`, `kotlin`, `jetpack-compose`, `material3`, `webdav`, `nas`, `baby-journal`, `local-first`
- Enable GitHub Security Advisories.
- Enable Dependabot alerts and Dependabot security updates.
- Keep the default branch as `main`.
- Protect `main` after the first push if other contributors join.

## Release Checklist

Before publishing release artifacts:

- Confirm `./gradlew :app:testDebugUnitTest :app:assembleDebug` passes.
- Confirm no real NAS hosts, passwords, API keys, screenshots, APKs, or signing keys are committed.
- Build release APK/AAB with a private keystore stored outside Git.
- Attach release notes from `CHANGELOG.md`.
