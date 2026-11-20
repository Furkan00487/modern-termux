# Upstream Sync Design (`termux-app` & `termux-x11`) Across `modern-termux` Ecosystem

**Date:** 2026-11-20  
**Status:** Ready for User Approval (Builder Check Completed)

---

## 1. Intent & Why

Bring all valuable upstream improvements, security patches, bugfixes, and performance enhancements from `termux/termux-app` (upstream `master` `8629e63`, 22–23 commits ahead of the `2026-02-12` base) and `termux/termux-x11` (upstream `master` `a00942e`, 30 commits ahead of the `2026-10-23` `7e5f5c5` base) into the three active repositories:

1. **`modern-termux`** (`~/work/modern-termux`, currently `v0.124.7` / `versionCode 133`)
2. **`modern-termux-advanced`** (`~/work/modern-termux-advanced`, currently `v0.124.7` / `versionCode 133`)
3. **`modern-termux-tv`** (`~/work/modern-termux-tv`, currently `v0.124.1` / `versionCode 127`)

Because all three repositories started with fresh git histories (or forked from `modern-termux`) and carry custom architecture (in-app `:x11` module, `ScreenIdlePolicy` / `ScreenSaverSuspend`, Mali/AHB/EXA GPU acceleration, Compose for TV, low-RAM safeguards, Play Protect cleanliness), upstream commits must be selectively ported and adapted rather than blindly merged.

---

## 2. Goals, Non-Goals, and Anti-Goals

### Goals
- Port all applicable `termux/termux-app` bugfixes, security patches, `64KB` read buffer, and Sixel (`DCS q`) / iTerm Image (`OSC 1337`) + `100KB` OSC 52 support to all three repositories.
- Port all applicable `termux/termux-x11` updates (`xserver 21.1.26`, `libX11 1.8.14`, Android 7 `@CriticalNative` fix, input/keyboard/tap fixes, renderer-in-X-server-process architecture, separate cursor `SurfaceView`, inverted scroll preference) to `modern-termux` and `modern-termux-advanced`.
- Preserve every repo's unique features and safety guarantees (`ScreenIdlePolicy`, `ScreenSaverSuspend`, `serverWatchdog`-free lifecycle, Play Protect cleanliness, Turkish localization, `modern-termux-advanced` Mali/AHB/EXA GPU patches, `modern-termux-tv` X11-free TV UI & `<2GB` RAM protections).
- Keep `master` untouched across all three repos: perform all work in isolated `.worktrees/` branches (`feat/upstream-sync`), push `feat/upstream-sync` to GitHub `origin`, and verify via GitHub Actions (`run_tests.yml` and `release_build.yml` via `workflow_dispatch`).

### Non-Goals
- Merging `feat/upstream-sync` into `master` or publishing a GitHub Release tag immediately (the user will test CI builds first and merge days later).
- Porting Android 5 (`API < 24`) fallback commits (`0a60d2d`) since all three repos have `minSdkVersion=24` (`apt-android-7`).
- Importing upstream GitHub Actions workflows, sponsor logos (`3df69d1`), or upstream `README` links (`e50ac9e`).
- Adding X11 back to `modern-termux-tv`.
- Upgrading Gradle wrapper in `modern-termux` / `modern-termux-advanced` (`9.2.1`) unless build requires it (`modern-termux-tv` is already on `9.9.1` + AGP `9.5.1`).

### Anti-Goals
- Breaking `modern-termux-advanced`'s custom Mali R8G8B8A8 swizzle (`d07587b`), AHB EXA CPU fallback (`0a35d82`, `92896ed`, `465f5fa`), or Present `clipList` (`48e595e`) by blindly applying upstream `a3c9c20` ("remove GPU copy offload").
- Re-introducing `BIND_ACCESSIBILITY_SERVICE`, `serverWatchdog` 200ms polling, or Play Protect heuristic triggers (`chmod 0700` script generators).
- Introducing OOM crashes on `<2GB` RAM Android TV devices when Sixel / iTerm2 bitmaps are rendered in `modern-termux-tv`.
- Pushing directly to `master` or modifying `~/work/*` main checkouts outside isolated worktrees.

---

## 3. Constraints

1. **Isolated Worktrees & Branching:** Use `.worktrees/` (already ignored in `.gitignore` of all three repos) with branch `feat/upstream-sync` (and a secondary branch `feat/upstream-sync-app` in `modern-termux-advanced` for parallel split work, merged into `feat/upstream-sync` before pushing).
2. **Version Bumping (`AGENTS.md`):** Bump `versionCode` (+1) and `versionName` (`0.125.0` across all three repos: `134` for `modern-termux` and `modern-termux-advanced`, `128` for `modern-termux-tv`) consistently across `app/build.gradle`, `terminal-emulator/build.gradle`, `terminal-view/build.gradle`, and `termux-shared/build.gradle` so `TermuxInstaller` bootstrap/MOTD checks and CI APK artifacts remain valid.
3. **Attribution (`NOTICE` & `x11/NOTICE`):** Update `NOTICE` (and `x11/NOTICE` where applicable) to document the newly synced upstream date/commits per GPLv3 §5(a).
4. **CI Verification:** Since `run_tests.yml` and `release_build.yml` only trigger automatically on `master`/`main`, trigger them manually on `feat/upstream-sync` using `gh workflow run ... --ref feat/upstream-sync` and watch completion with `gh run watch`.
5. **Local Verification Before Push:** Run unit tests / C++ tests locally where tooling permits (or document exact local vs CI verification boundaries honestly).
6. **Submodule Integrity:** For `xserver` (`21.1.26`) and `libx11` (`1.8.14`) updates, update submodule gitlinks cleanly to the exact commit SHAs referenced by `termux/termux-x11` `3753efd` and `a31042e` so `actions/checkout` with `submodules: recursive` checks out cleanly in CI.

---

## 4. Approaches Considered & Selected Architecture

### Considered Approaches
1. **Selected — Parallel 3-Agent + Lead Split ("Office Setup"):**
   - **Agent 1 (`modern-termux`):** Ports both `termux-app` and `termux-x11` (including `a3c9c20` GPU copy offload removal, `bf6663e`..`488dbbd` renderer-in-server, `cdfd3f9` separate cursor `SurfaceView`, `3753efd` `xserver 21.1.26`) in `~/work/modern-termux/.worktrees/feat-upstream-sync`.
   - **Agent 2 (`modern-termux-advanced` — Half 1):** Ports `termux-app` commits into `~/work/modern-termux-advanced/.worktrees/feat-upstream-sync-app` (`feat/upstream-sync-app`).
   - **Lead Agent / Main Session (`modern-termux-advanced` — Half 2 + Integration):** Ports `termux-x11` updates into `~/work/modern-termux-advanced/.worktrees/feat-upstream-sync` (`feat/upstream-sync`) **while preserving** Mali RGBA swizzle (`renderer_format.h`), AHB/EXA safety (`dri3_safety.h`), and Present GPU copy clipping (`48e595e`) — excluding `a3c9c20` — then merges `feat/upstream-sync-app`, bumps version (`0.125.0` / `134`), and verifies.
   - **Agent 3 (`modern-termux-tv`):** Ports `termux-app` commits (plus `8ec4dcd` `APPLICATION_PREFERENCES` routing if absent) into `~/work/modern-termux-tv/.worktrees/feat-upstream-sync` (`feat/upstream-sync`), excluding all X11 commits and adding low-RAM bitmap/Sixel memory safeguards integrated with `LowRamPolicy`.
2. **Rejected — Sequential Single-Agent Execution:** Slower and loses the user's explicit request to parallelize across 3 repo agents while sharing half of `modern-termux-advanced` with the lead agent.
3. **Rejected — Direct `git merge` from upstream remotes:** Impossible without massive artificial conflicts due to fresh repo history (`2026-10-20`) and `lorie/` -> `x11/` directory rename.

---

## 5. Exact Upstream Commit Scope

### A. `termux/termux-app` Commits (Applied to All 3 Repos)
Fetch source: `refs/remotes/upstream-app/master` (fetch into `modern-termux-advanced` and `modern-termux-tv` as needed, or cherry-pick via patches).

**Include:**
- `ef4775b`: Increase `TerminalSession` read buffer (`4KB` -> `64KB`)
- `30ebb2d`: Fix inverted `kP` / `kN` termcap values in `KeyHandler.java`
- `401bbe5`: Guard null `notificationBigText` in `NotificationUtils.java`
- `3b66f87`: Security fix in `RunCommandService` (`allow-external-app` check before file result config)
- `74ab512`: Preserve scroll position across session switch / restart / IME toggle (`TerminalEmulator`, `TerminalView`)
- `47c473f`: Clear `LineWrap` flag in `TerminalBuffer.blockSet`
- `1021b58`: Cast cursor dimensions to `float` in `TerminalRenderer`
- `5532cc4`: Use `int` instead of `short` for space indexing
- `8b72226`: Pass `Base64.DEFAULT` for clipboard OSC 52 decoding + typo fix
- `9adb09a`: Add `com.termux.terminal.AndroidUtils.getSystemProperties` (used by Sixel/image sizing/props)
- `fd2ab4f`: Standardize OSC/DCS/APC handling + fast path + buffer clearing
- `3b27013`: Terminal support for Bitmaps, Sixel (`DCS q`), and iTerm Image (`OSC 1337`)
- `d8d6b02`: Increase OSC 52 clipboard limit to `100KB`
- `4584488`: Disable terminal margin adjustment in multi/floating window (`TermuxActivityRootView`)
- `b4a2c32`: Fix `NullPointerException` in `TermuxOpenReceiver$ContentProvider.getType()`
- `084d709`: Fix `ReleaseStringUTFChars` argument in `termux.c` (cleanliness)

**Exclude:**
- `e50ac9e` (README links), `3df69d1` (Warp sponsor logo), `0a60d2d` (Android 5 `drawText`), `7116605` / `b72e020` / `8629e63` (upstream CI workflows).
- `3f0dec3` (`extractNativeLibs` already removed in all 3 repos; verify no regression).

**Repo-specific notes for `modern-termux-tv`:**
- Verified `8ec4dcd` (`APPLICATION_PREFERENCES` routing to `SettingsActivity`) is missing in `modern-termux-tv` and is included in this sync.
- Add a low-RAM memory cap on `TerminalBitmap` / Sixel / iTerm2 image decoding/caching when running on low-RAM devices (e.g., leveraging `LowRamPolicy` or capped max image bytes/dimensions with unit tests) so `<2GB` TV devices cannot OOM on large images.

### X11 Surface Handoff & Stall Protection Without `serverWatchdog` (`modern-termux` & `modern-termux-advanced`)
*Call made following Builder Check (open for user override):*
- Both repos intentionally removed the 200 ms `serverWatchdog` polling loop (`47dfd85`) and never show `no_server_signal`. However, `bf6663e`/`b742ee8`/`cdfd3f9` move `Renderer` into the `CmdEntryPoint` X server process and make `LorieView`'s `SurfaceHolder.Callback` send `windowChanged` / `cursorWindowChanged` over synchronous Binder calls guarded by `serverStalled()` / `surfacePending`.
- Without a flush trigger, `surfacePending` would stay stuck and leave a black screen after a stall; without `serverStalled()`, a stalled server could ANR the UI thread during `pthread_cond_wait` in `setWindow`.
- **Decision:** Keep `serverStalled()` / `surfacePending` and `488dbbd` (immediate heartbeat at server generation start), keep the 200 ms `serverWatchdog` loop and `no_server_signal` overlay **removed**, and trigger a **one-shot flush** from `activity.cpp`'s `EVENT_HEARTBEAT` (only when `surfacePending` is true, posting `sendSurface()`/`sendCursorSurface()` to the UI thread).

---

### B. `termux/termux-x11` Commits (Applied to `modern-termux` and `modern-termux-advanced`, Excluded from `modern-termux-tv`)
Fetch source: `refs/remotes/upstream-x11/master` (`lorie/` paths mapped to `x11/`).

**Include in `modern-termux` (Full upstream X11 sync, adapted to in-app lifecycle):**
- `29d4460`, `e8e9847`, `6dfb8b4` (Present clipList, renderer sync point, `-check-drawing` fix)
- `a3c9c20`, `69bb7e3`, `d5ef3c4`, `f1d9834` (Remove GPU copy offload, refactor `InitOutput.c` DRI3/root pixmap/GLX config generation; remove `fbconfigs.h` / `workqueue.cpp` if obsolete)
- `e3a1657`: Restore `@CriticalNative` compatibility on Android 7 (`minSdk 24`)
- `a31042e` & `3753efd`: Update `libX11` (`1.8.14`) and `xserver` (`21.1.26`) submodules + regenerate `xserver.patch`
- `bf6663e`, `0ed1fe7`, `982823f`, `ea5ee14`, `b742ee8`, `10f4c63`, `089a049`, `488dbbd`: Move renderer into X server process, plain mutexes, legacy row damage upload, capability test refactor, fresh surface handoff, immediate heartbeat (preserving `serverWatchdog`-free `onActivityResumed`/`Paused` + `ScreenIdlePolicy` / `ScreenSaverSuspend`)
- `cdfd3f9`: Separate cursor `SurfaceView` placed by `MainActivity`
- `1bb8d6f`, `11c76bc`, `90acb76`, `7349dfa`, `ed43f97`: `KEYCODE_F13..F24`, alphabetic keyboard + touchpad key filter fix, 30ms tap click release delay, release pressed keys on disconnect, invert scroll direction option (+ Turkish/English string entries)

**Include in `modern-termux-advanced` (Adapted to preserve custom GPU patches):**
- **EXCLUDE `a3c9c20`** (keep GPU copy offload & Present GPU copy pipeline!)
- **PRESERVE** `renderer_format.h` (Mali RGBA swizzle `d07587b`), `dri3_safety.h` (GPU-only AHB flip + CPU fallback + EXA `sys_ptr` sync + NULL `vaddr` check `0a35d82`/`92896ed`/`465f5fa`), `48e595e` (`xserver.patch` Present GPU copy `clipList`), and `dd35dff` (optional Vulkan features + `hardwareAcceleration` pref).
- **ADAPT & PORT** `6dfb8b4`, `69bb7e3`/`d5ef3c4`/`f1d9834` (only non-GPU-removal parts), `e3a1657`, `a31042e`/`3753efd` (`xserver 21.1.26` + `libX11 1.8.14` with `48e595e` Present GPU copy hunks cleanly rebased onto `21.1.26`), `bf6663e`..`488dbbd` (adapting renderer-in-X-server process & plain mutexes without dropping `renderer_format.h` RGBA swizzle or `dri3_safety.h` / `ScreenSaverSuspend`), `cdfd3f9` (separate cursor `SurfaceView`), and all input/scroll fixes (`1bb8d6f`, `11c76bc`, `90acb76`, `7349dfa`, `ed43f97`).

**Exclude from both X11 repos:**
- `1d36506` (README), `2d5e732` (CI `gradle/actions`), `3bf4324` (nightly CI), `c5d0920` (standalone `shell-loader` `classes.dex` — verified absent; both repos use in-app `CmdEntryPoint` directly), `dc56088` / `a00942e` (Gradle wrapper `9.9.1`).

---

## 6. Execution, Testing & Verification Flow

1. **Test-Driven Verification:** For each modified/ported behavior (especially `RunCommandService` security ordering, `KeyHandler` `kP`/`kN`, `TermuxOpenReceiver.getType` null safety, `TerminalEmulator` Sixel/`OSC 1337`/scroll position, `modern-termux-tv` low-RAM image guard, and `modern-termux-advanced` `dri3_safety_test.cpp` / `renderer_format_test.cpp` / `TermuxX11IntegrationTest`), add or update unit tests before/alongside implementation.
2. **Branch & Push:** Commit cleanly on `feat/upstream-sync` in each repo, then push `feat/upstream-sync` to `origin`. Never touch `master`.
3. **CI Validation:** Trigger `run_tests.yml` and `release_build.yml` via `gh workflow run <workflow> --ref feat/upstream-sync` on all three repos and monitor with `gh run watch` to verify all unit tests, C++/NDK native builds, and release APK signing succeed.

---

## 7. Left to Builders (Implementation Details)

- Exact line-level conflict resolution inside `InitOutput.c`, `renderer.cpp`, `activity.cpp`, `MainActivity.java`, `LorieView.java`, and `xserver.patch`.
- Exact pixel/byte threshold constants for `modern-termux-tv` Sixel/iTerm2 low-RAM protection (aligned with `LowRamPolicy` conventions).
- Turkish (`values-tr/strings.xml`) translations for new upstream X11 preference strings (e.g., invert scroll direction).
