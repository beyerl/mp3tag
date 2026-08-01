# Mp3tag for Android

A Kotlin/Compose reimplementation of [Mp3tag](https://docs.mp3tag.de/) — the universal audio tag editor — as a sideloaded Android app.

## Status

Phase 1 (core tag editor) in progress:

- [x] Folder browser with All Files Access, favorites and recents
- [x] Progressive directory scanning (bounded-parallel tag reading via TagLib)
- [x] File list with multi-select, sorting, dirty markers
- [x] Tag panel with Mp3tag's `< keep >` batch-edit semantics
- [x] Save pipeline (changed fields only, mtime preservation, conflict detection)
- [x] Extended tags dialog (arbitrary fields, multi-value editing)
- [x] Cover art (view / replace via photo picker / remove)
- [x] Rename / delete, undo of saved batches
- [x] Settings screen (preserve mtime)
- [ ] On-device verification: tag round-trip across formats (mp3, flac, m4a, ogg, opus, wav, …)

Phase 2 (scripting engine, converters, actions) implemented:

- [x] Format-string engine (`:scripting`, pure JVM): `%field%` placeholders, `[optional]`, quoting, ~50 `$functions` with lazy evaluation
- [x] Filter bar (HAS/IS/MATCHES/GREATER/LESS/EQUAL, PRESENT/ABSENT, AND/OR/NOT, bare-word search)
- [x] Converters: Tag → Filename, Filename → Tag, Tag → Tag, auto-numbering — all preview-first
- [x] Action groups (case, replace, regex replace, format value, guess values, remove/keep fields, split) persisted as JSON
- [ ] Golden tests against desktop Mp3tag behavior (corpus to be captured on Windows)

Phase 3 (online sources, export, playlists) implemented:

- [x] Tag sources: MusicBrainz (no auth, 1 req/s) and Discogs (personal token in Settings) — search → pick release → track matching (by track/disc number, order fallback) → field-selective import incl. cover art
- [x] Export templates (`$loop`/`$loopend`, `$puts`/`$get`, `%_counter%`/`%_total_files%`/`%_total_time%`) with Text/CSV/HTML built-ins, save-as or share
- [x] Playlist generation: m3u8 with relative paths in the session folder, single or partitioned by format string

## Tech

- Jetpack Compose + Material 3, `:app` + pure-JVM `:scripting` modules
- Tag I/O: [Kyant0/taglib](https://github.com/Kyant0/taglib) — TagLib 2.x via prebuilt JNI AAR (`io.github.kyant0:taglib`), fed by `ContentResolver` file descriptors
- Storage: **no global storage permission** — scoped access to user-granted folders via persisted SAF tree URIs (system folder picker, revocable per folder); only audio file extensions are enumerated and touched
- minSdk 30, targetSdk 35, compileSdk 37

SAF trade-offs vs. the desktop app: file modification times cannot be preserved on save (no SAF API), and Tag → Filename cannot create subdirectories.

## Building

CI builds every push (`.github/workflows/ci.yml`); debug APK is attached as a workflow artifact. Tagging `v*` builds a signed release (requires `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` secrets).

Locally: `./gradlew assembleDebug` (JDK 17 + Android SDK required).
