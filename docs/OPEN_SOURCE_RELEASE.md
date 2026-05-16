# Open Source Release Checklist

Use this checklist before pushing or tagging a public release.

## Repository

- [ ] `README.md` explains the app, build steps, and storage model.
- [ ] `LICENSE` is present.
- [ ] `PRIVACY.md` is present.
- [ ] `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, and issue templates are present.
- [ ] Example configuration contains placeholders only.
- [ ] No personal screenshots, generated APKs, Gradle caches, or local config files are tracked.
- [ ] Optional font binaries are not committed unless their redistribution license is verified.
- [ ] `git status --short` contains only intentional source and documentation changes.

## Secrets

- [ ] No API keys are committed.
- [ ] No NAS/WebDAV password is committed.
- [ ] No personal NAS domain or LAN IP is committed.
- [ ] No release signing key is committed.

## Android

- [ ] `versionName` and `versionCode` are correct.
- [ ] App label is correct.
- [ ] Launcher icons are correct.
- [ ] Debug build succeeds.
- [ ] Release build succeeds if publishing APK/AAB.

## Suggested Commands

```powershell
rg -n --hidden --glob '!**/.git/**' --glob '!**/build/**' --glob '!**/.gradle/**' "apiKey|password|secret|token|Bearer|192\\.168|10\\.|172\\.(1[6-9]|2[0-9]|3[0-1])"
.\gradlew.bat :app:assembleDebug
git status --short
```

## Publishing Notes

If local development history contains private NAS hosts, LAN IPs, screenshots, or local paths, publish from a clean root commit instead of pushing the full private history. Push only the public branch and the intended release tag.

```powershell
git push -u origin main
git push origin v1.0.0
```

Avoid `git push --all` or broad `git push --tags` when private archive branches or old local tags exist.
