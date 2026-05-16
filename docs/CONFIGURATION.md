# Configuration Guide

babybuddy is configured from the Android app UI. Do not hardcode private endpoints or secrets in source code.

## AI Provider Example

The AI assistant uses OpenAI-compatible chat completion APIs.

Example values:

```json
{
  "name": "DeepSeek",
  "apiBaseUrl": "https://api.deepseek.com",
  "apiKey": "YOUR_API_KEY_HERE",
  "model": "deepseek-chat",
  "temperature": 0.7,
  "maxTokens": 32768
}
```

Notes:

- Keep `apiKey` on your device only.
- Different providers may require different model names.
- Avoid pasting sensitive medical details into AI chats unless you trust the provider.

## WebDAV / NAS Example

Example values:

```json
{
  "protocol": "WEBDAV",
  "host": "dav.example.com",
  "port": 443,
  "useHttps": true,
  "username": "babybuddy-user",
  "password": "YOUR_WEBDAV_PASSWORD_HERE",
  "webdavPath": "/babybuddy/"
}
```

Expected remote layout:

```text
/babybuddy/
  data/
  media/
    photos/
    videos/
```

Recommendations:

- Prefer HTTPS over HTTP.
- Use a dedicated NAS/WebDAV account with access only to the babybuddy folder.
- Back up your NAS separately.
- Do not commit real hostnames, usernames, passwords, or API keys.

## Release Build Network Policy

Release builds default to disallowing cleartext traffic. If you need HTTP WebDAV for local testing, use a debug build or adjust the configuration consciously before distributing your own APK.
