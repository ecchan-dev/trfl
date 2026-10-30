# Trfl development

Trfl is a native, read-only Android app for browsing 4chan, built with Kotlin, Jetpack Compose and Material Design 3, with edge-to-edge system bars and light/dark themes.

For installation and updates, see the [README](../README.md).

## Included

- Bottom navigation: Boards → Watched → Saves → Settings, with app-bar search. Saves has Threads/Photos/Videos tabs: search saved thread text/titles/board IDs or media filenames and source text. Photos/videos include locally completed thread attachments and completed Android downloads; partial remote attachments are excluded. Existing saved-thread storage is retained, including archive/unarchive and removal confirmation. Media opens in the shared immersive viewer.
- Predictive back uses Android's gesture progress and retained parent-page renderings. Cancelling does not navigate or trigger requests/read updates. Back returns to the actual origin (including bookmarks/watchlist), not an invented catalog. Normal back uses the same history. Search/dialogs retain their own dismissal priority; root delegates back-to-home to Android.
- Parent previews are memory-only, half-resolution and retained for the bounded eight-page navigation history; no screenshot files are created. After process recreation, theme changes or navigation before capture, Back still works but keeps the current page opaque until completion rather than revealing an empty grey window. In-flight captures from outgoing routes and back gestures are rejected. Page scroll state and up to eight loaded post lists are retained during navigation; Refresh or changing preview mode invalidates those lists.
- Live board directory; adult boards opt-in. Favorites stay first; long-press drag handles reorder within favorites or other boards, with edge auto-scroll and accessibility move actions.
- Settings: Yotsuba B blue SFW / Yotsuba warm NSFW colors, with System/Light/Dark appearance, system theme fallback. Catalog/list view is selected while browsing and the last choice persists across boards and restarts; no duplicate Settings toggle. Dark text/background pairs have automated WCAG AA contrast checks.
- Pull-to-refresh, catalog/thread scroll-to-top, in-thread quote previews/jumps, Android thread sharing and thread-link intent handling. Domain verification is not claimed: opening website links may require enabling TRFL in Android's Open by default settings.
- Viewer has separate Save (private TRFL library, up to 128 MiB per attachment) and Download to phone (public Downloads) actions. Private saves are activity-scoped and show completion/failure; leaving the viewer cancels an unfinished save. Completed saves appear in Saves Photos/Videos. Previously listed downloads remain visible for compatibility; new phone-only downloads do not automatically enter Saves. Local-to-Downloads export requires Android 10+.
- Media export via Android DownloadManager with OS-owned background transfers, network retries, progress/cancel/retry in Settings → Downloads. Failed retries start a new transfer; manual byte-range resume is not implemented. Existing bookmark snapshot downloads are separate and still activity-scoped.
- Tablet-width navigation rail (600dp+) and adaptive grids; this is responsive sizing, not fold-hinge-aware two-pane support. Catalog card heights grow with system text scale, quote previews expose screen-reader actions, and controls use Material touch targets.
- Shared bounded HTTP disk cache: board directory 24h, catalogs 60s, threads 30s; conditional ETag/Last-Modified revalidation, explicit Refresh bypasses freshness, and stale cached fallback on network/server failures. Confirmed 404s invalidate disposable thread entries, never explicit bookmarks. Cache failures do not prevent browsing. API disk budget 64 MiB; image cache 256 MiB plus 15% memory cache. Board lists and downloaded images survive app restarts subject to Android cache eviction.
- Equal-height catalog cards and compact list mode; search loaded posts.
- Thread reading, basic saved scroll positions and explicit local bookmarks (text plus media, bounded to 64 MiB/file and 256 MiB/thread; partial download counts shown). Network failure/dead threads fall back to the saved copy. No automatic bookmark refresh/download.
- Confirm before removing a bookmark and its local files; archive/unarchive without deleting data.
- Watched threads: manual checks on opening/refresh, mark read/all read, prune confirmed dead threads. Optional Settings → Background reply notifications checks about every 15 minutes using network-constrained Android WorkManager (OS power restrictions may delay checks). Android 13+ asks notification permission. A private summary notification enables launcher dots on supported launchers; reading all replies or stopping watches clears it. Dismissing notifications or disabling launcher/channel badges can hide the dot. Dead/archived server threads are labeled in both lists; network failures are not treated as dead.
- Thread image grid; swipe/previous/next full-size viewer, pinch zoom/pan, WebM playback.
- Animated list moves, title transitions, board colors, loading and Material controls; Compose respects the system animator duration scale.
- Refresh, visible network errors/retry, offline Preview mode.

## Build and iterate

Requires JDK 17+ and an Android SDK with platform 35. Set `ANDROID_HOME` or create an ignored `local.properties` with `sdk.dir=/absolute/path/to/sdk`.

```sh
./gradlew :app:assembleDebug :app:lintDebug
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell am start -n org.trfl.app/.MainActivity
python3 tools/emulator-smoke.py
```

The smoke script uses live `/g/` content and adds a favorite, watched thread and local bookmark. It navigates the UI and can change preferences: run only on a dedicated development device. `ADB` and `ANDROID_SERIAL` override its defaults. It does not start an emulator.

`tools/install-phone.py` is an explicit worker deployment command: checks the expected hardware serial, builds/tests, installs without clearing app data, verifies the installed APK SHA-256 against the exact source build, then launches TRFL. Phone installs use the optimized, resource-shrunk, non-debuggable release build rather than the slower Compose debug build. Release is signed with the existing local debug certificate to preserve in-place updates and app data; this is not store distribution signing. Debug builds remain available for development. It never pairs, searches for devices or starts a VM. The source commit is visible in Settings.

For this workstation, the existing Android Studio AVD is `KurobaEx_Phone`; it needs `ANDROID_AVD_HOME=$HOME/.var/app/com.google.AndroidStudio/config/.android/avd`. VMs are manual-only; no automatic emulator startup is configured. Host/headless graphics proved unreliable on this workstation. Phone deployment is now the authorized development target. No posting or paid API calls are involved.

## GitHub APK builds

The `Build APK` GitHub Actions workflow tests, lints and builds a debug APK on pushes to `main`/`master` and pull requests. You can also run it manually from **Actions → Build APK → Run workflow**.

After a successful run, open its summary and download **trfl-apk-…** under **Artifacts**, then extract the ZIP to get `app-debug.apk`. Artifacts are kept for 14 days. No signing secrets are required. These are development builds signed with the runner's temporary debug key, not the existing phone/release key: they cannot update that installation, and signing keys may differ between runs. Do not uninstall your existing app without backing up its data.

## Play-only build (not publication-ready)

`./gradlew :app:bundlePlay :app:lintPlay :app:testPlayUnitTest` creates an **unsigned** optimized Play bundle under `app/build/outputs/bundle/play/`. Secure upload signing and Play Console setup remain required. Its application ID is `org.trfl.app.play`, separate from sideloaded `org.trfl.app`, so adult preferences, caches and saved media are not inherited. The existing phone deployment still uses the unrestricted release variant.

The Play variant has no adult-board setting. A fail-closed allowlist based on the official `ws_board` classification restricts directory entries, deep links, API/cache access, watched/saved entries and offline thread loading. New boards require an explicit allowlist review; directory entries must also retain their safe flag. This removes adult boards, **not all potentially objectionable posts on safe boards**. UGC moderation/reporting/blocking, privacy disclosures, age rating and other Play requirements still need review; this build does not imply Play approval.

## Scope and known limitations

Trfl is intentionally read-only: posting, replying and CAPTCHA flows are outside its scope. Board categories are not currently supported. Cross-thread quote resolution, board links, shared local-file export, fold-hinge-aware layouts, GIF animation and Media3 playback remain follow-ups. Local bookmark snapshots are taken when bookmarked; media downloads can be partial or interrupted (text is saved first). Interrupted downloads do not resume automatically. Archive is a local organizational flag, not a new server snapshot. Watch unread counts use the last checked post total, not per-post read tracking. Scroll restoration is index-based. Dependencies remain pinned, not claimed to be the newest releases. Build, unit tests and lint are run locally and by the worker's phone deployment. The full expanded emulator smoke was interrupted by graphics failures/user navigation and is not claimed as passing.
