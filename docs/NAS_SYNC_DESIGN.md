# NAS Sync Design

BabyBuddy keeps a local working copy first, then synchronizes to WebDAV when a NAS account is configured.

## Current Storage Model

- Local app data is stored in SharedPreferences snapshots and the Room media index.
- Imported photos and videos are copied into the app-private files directory before upload.
- WebDAV stores JSON data files under the configured data path and media files under `media/photos` and `media/videos`.
- Media entries keep a stable `remotePath` and derive the absolute display URL from the currently configured WebDAV endpoint.
- On startup, local data is loaded first. If local data is marked dirty, BabyBuddy pushes local changes only when the remote revision is safe to overwrite. If the NAS already contains data and cannot be safely overwritten, BabyBuddy pauses the write and merges the remote media index back into the local view.

## Host-Independent Media Identity

The same NAS can be reached through different hosts, for example:

- Local endpoint: `http://nas-lan.example:5005`
- Public endpoint: `https://dav.example.com`

These hosts must not be treated as different media identities. Media should be identified by a stable remote path such as:

```text
/babybuddy/data/media/photos/example.jpg
/babybuddy/data/media/videos/example.mp4
```

The UI can still render an absolute URL by combining the current WebDAV host with the stable remote path.

## Safety Rules

- Do not delete a remote media index entry only because its host differs from the current WebDAV host.
- Do not treat an `http://` or `https://` media URL as a local file path.
- Only prune a media entry when the current WebDAV host and directory listing or direct existence check confidently prove the file is gone.
- If the media path cannot be mapped safely, preserve the index and log the condition.

## Remaining Next Step

Extend the same stable identity approach to all structured records with per-item `updatedAt`, tombstones, and a conflict-resolution screen. The current media path handling is host-independent, but timeline, feeding, vaccine, and chat records still rely mostly on whole-file JSON replacement.
