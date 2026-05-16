# Privacy Policy

Last updated: 2026-05-16

babybuddy is a local-first baby journal app. This document explains what data the app is designed to handle and where it may go.

## Data You May Store

Depending on how you use the app, babybuddy may store:

- Baby profile information such as name, nickname, birthday, gender, and avatar.
- Timeline records, milestones, growth notes, feeding records, and vaccine status.
- Photos and videos selected by the user.
- AI assistant messages and generated replies.
- App settings, AI provider configuration, and NAS/WebDAV storage configuration.

## Local Storage

The app is designed to work without a cloud account. Structured data is stored on the device. Sensitive configuration such as API keys and storage credentials is intended to be kept locally and should not be uploaded as public repository content.

## Optional NAS/WebDAV Sync

If you configure NAS/WebDAV, babybuddy may upload structured JSON files, AI chat history, photos, and videos to the server you provide. The server address, username, password, path, and retention policy are controlled by you.

Use HTTPS when possible. Plain HTTP may expose data on untrusted networks.

## AI Providers

If you configure an AI provider, babybuddy may send conversation text and relevant baby context to that provider to generate a reply. Review the provider's own privacy policy before enabling AI features.

## No Built-In Analytics

The app does not include a third-party analytics SDK in this repository.

## Your Responsibilities

- Keep your NAS/WebDAV credentials private.
- Keep AI API keys private.
- Back up important family media.
- Avoid storing sensitive medical records unless you understand the risks.
- Review code changes before distributing modified builds.

## Contact

This is an open-source project. For privacy or security issues, open a GitHub issue or contact the repository owner through GitHub.
