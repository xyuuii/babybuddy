# Release Signing

Do not commit release signing keys to this repository.

## Recommended Layout

Keep signing material outside the Git worktree, for example:

```text
C:\Users\<you>\.android-signing\babybuddy-release.jks
C:\Users\<you>\.android-signing\babybuddy-release.properties
```

Example properties file:

```properties
storeFile=C:\\Users\\<you>\\.android-signing\\babybuddy-release.jks
storePassword=CHANGE_ME
keyAlias=babybuddy
keyPassword=CHANGE_ME
```

## Generate a Keystore

```powershell
keytool -genkeypair `
  -v `
  -keystore "$env:USERPROFILE\.android-signing\babybuddy-release.jks" `
  -alias babybuddy `
  -keyalg RSA `
  -keysize 4096 `
  -validity 10000
```

## Notes

- Back up the keystore securely. Losing it can prevent app updates.
- Use different credentials for debug, internal testing, and public releases.
- Never paste signing passwords into public issues, logs, screenshots, or CI output.
