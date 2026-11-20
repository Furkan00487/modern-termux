# Upstream Sync Implementation Plan (`modern-termux` Ecosystem)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Selectively port and adapt all approved upstream `termux/termux-app` and `termux/termux-x11` commits onto isolated `feat/upstream-sync` worktree branches across `modern-termux`, `modern-termux-advanced`, and `modern-termux-tv`, push those branches to GitHub `origin`, and validate via GitHub Actions `workflow_dispatch`.

**Architecture:** Three isolated repo workstreams execute in parallel: Agent 1 handles all `termux-app` + `termux-x11` updates in `modern-termux`; Agent 2 handles `termux-app` updates in `modern-termux-advanced` (`feat/upstream-sync-app`); the Lead Agent handles `termux-x11` updates in `modern-termux-advanced` (`feat/upstream-sync`) while preserving Mali RGBA swizzle (`renderer_format.h`), AHB/EXA safety (`dri3_safety.h`), and Present GPU copy `clipList` (`48e595e`), then merges `feat/upstream-sync-app`; Agent 3 handles `termux-app` updates + low-RAM bitmap/Sixel safeguards in `modern-termux-tv` (no X11).

**Tech Stack:** Android Gradle (`AGP 8.x`/`9.5.1`), Java/Kotlin, C/C++ (NDK/CMake), JUnit/Robolectric, Git worktrees, GitHub CLI (`gh`), GitHub Actions (`run_tests.yml`, `release_build.yml`).

**Spec:** [docs/superpowers/specs/2026-11-20-upstream-sync-design.md](file:///data/data/com.termux/files/home/work/modern-termux/docs/superpowers/specs/2026-11-20-upstream-sync-design.md)

## Global Constraints
- Work strictly inside `.worktrees/<branch>` in each repo; never commit to or push `master`.
- Version bumps required across all 4 Gradle files (`app/build.gradle`, `terminal-emulator/build.gradle`, `terminal-view/build.gradle`, `termux-shared/build.gradle`):
  - `modern-termux`: `versionCode 134`, `versionName "0.125.0"`
  - `modern-termux-advanced`: `versionCode 134`, `versionName "0.125.0"`
  - `modern-termux-tv`: `versionCode 128`, `versionName "0.125.0"`
- Minimum SDK is `minSdkVersion=24` (`apt-android-7`); exclude Android 5 fallback `0a60d2d`, upstream CI workflows, Warp sponsor logo `3df69d1`, README `e50ac9e`, `shell-loader` `c5d0920`, and Gradle wrapper `dc56088`/`a00942e`.
- Preserve `modern-termux` & `modern-termux-advanced` X11 customizations: no 200 ms `serverWatchdog` loop, no `no_server_signal` overlay, keep `ScreenIdlePolicy` / `ScreenSaverSuspend` wake-lock, `BIND_ACCESSIBILITY_SERVICE` removal, Play Protect cleanliness, Turkish translations, and `EVENT_HEARTBEAT` one-shot `surfacePending` flush (with `488dbbd`).
- Update `xserver` gitlink to `7dbd8d0dd1929c2011009fd1770c8aa470df97fb` (`21.1.26`) and `libx11` gitlink to `b8d5a7046a98a244ee8ca3c9c07fd617c800dde2` (`1.8.14`) via `git update-index --cacheinfo`.
- Do not merge to `master` or create GitHub releases; push `feat/upstream-sync` to `origin` and trigger `run_tests.yml` + `release_build.yml` with `gh workflow run ... --ref feat/upstream-sync`.

## Review Focus
- `RunCommandService` receives `EXTRA_RESULT_DIRECTORY` when `allow-external-apps` is `false`: must reject before parsing/populating file result paths so no error file is written.
- `TermuxOpenReceiver$ContentProvider.getType(uri)` receives a `Uri` whose `getLastPathSegment()` is `null`: must return `null` instead of throwing `NullPointerException`.
- `TerminalBitmap` / Sixel / iTerm2 image decoding on `<2GB` RAM TV devices (`modern-termux-tv`): oversized image payload must be capped/rejected cleanly without OOM.
- `LorieView` / `MainActivity` when `serverStalled()` is `true` during `surfaceCreated` (without `serverWatchdog`): `surfacePending` must flush via one-shot `EVENT_HEARTBEAT` callback once the X server starts emitting heartbeats.
- `modern-termux-advanced` `InitOutput.c` / `renderer.cpp` / `xserver.patch` after `xserver 21.1.26` + `bf6663e` + `cdfd3f9`: `renderer_format.h` RGBA swizzle, `dri3_safety.h` NULL `vaddr` / EXA `sys_ptr` fallback, and Present GPU copy `clipList` must remain active and pass `dri3_safety_test.cpp` / `renderer_format_test.cpp`.

---

### Task 1: Workspace Isolation & Upstream Fetch Across All Three Repos

**Files:**
- Worktrees:
  - `~/work/modern-termux/.worktrees/feat-upstream-sync` (`feat/upstream-sync`)
  - `~/work/modern-termux-advanced/.worktrees/feat-upstream-sync-app` (`feat/upstream-sync-app`)
  - `~/work/modern-termux-advanced/.worktrees/feat-upstream-sync` (`feat/upstream-sync`)
  - `~/work/modern-termux-tv/.worktrees/feat-upstream-sync` (`feat/upstream-sync`)

**Interfaces:**
- Produces: fetched `refs/remotes/upstream-app/master` (`8629e63`) and `refs/remotes/upstream-x11/master` (`a00942e`) in each repo's `.git`, plus clean isolated worktrees branched from `master`.

- [ ] **Step 1: Verify `.worktrees/` is ignored in all three repos**

Run:
```bash
for r in modern-termux modern-termux-advanced modern-termux-tv; do
  git -C ~/work/$r check-ignore -q .worktrees && echo "$r: ignored"
done
```
Expected: `<repo>: ignored` printed for all three repos.

- [ ] **Step 2: Fetch `upstream-app/master` and `upstream-x11/master` into `modern-termux-advanced` and `upstream-app/master` into `modern-termux-tv`**

Run:
```bash
git -C ~/work/modern-termux-advanced fetch --no-tags --depth=50 --update-shallow https://github.com/termux/termux-app.git master:refs/remotes/upstream-app/master
git -C ~/work/modern-termux-advanced fetch --no-tags --depth=50 --update-shallow https://github.com/termux/termux-x11.git master:refs/remotes/upstream-x11/master
git -C ~/work/modern-termux-tv fetch --no-tags --depth=50 --update-shallow https://github.com/termux/termux-app.git master:refs/remotes/upstream-app/master
```
Expected: All three fetches exit 0 and `git show-ref | grep upstream` shows the expected SHAs (`8629e63`, `a00942e`).

- [ ] **Step 3: Create the four isolated worktrees from `master`**

Run:
```bash
git -C ~/work/modern-termux worktree add .worktrees/feat-upstream-sync -b feat/upstream-sync master
git -C ~/work/modern-termux-advanced worktree add .worktrees/feat-upstream-sync-app -b feat/upstream-sync-app master
git -C ~/work/modern-termux-advanced worktree add .worktrees/feat-upstream-sync -b feat/upstream-sync master
git -C ~/work/modern-termux-tv worktree add .worktrees/feat-upstream-sync -b feat/upstream-sync master
```
Expected: Four worktrees created cleanly on their respective branches.

---

### Task 2 (Agent 1): Port `termux-app` Updates in `modern-termux`

**Files:**
- Modify: `app/src/main/java/com/termux/app/RunCommandService.java`
- Modify: `app/src/main/java/com/termux/app/TermuxOpenReceiver.java`
- Modify: `app/src/main/java/com/termux/app/terminal/TermuxActivityRootView.java`
- Modify: `terminal-emulator/src/main/java/com/termux/terminal/{KeyHandler.java,TerminalBuffer.java,TerminalEmulator.java,TerminalRow.java,TerminalSession.java,TextStyle.java}`
- Create: `terminal-emulator/src/main/java/com/termux/terminal/{AndroidUtils.java,ITermImage.java,TerminalBitmap.java,TerminalSixel.java}`
- Modify: `terminal-emulator/src/main/jni/termux.c`
- Modify: `terminal-view/src/main/java/com/termux/view/{TerminalRenderer.java,TerminalView.java}`
- Modify: `termux-shared/src/main/java/com/termux/shared/notification/NotificationUtils.java`
- Test: `app/src/test/java/com/termux/app/UpstreamSyncAppFixesTest.java`, `terminal-emulator/src/test/java/com/termux/terminal/UpstreamTerminalFeaturesTest.java`

**Interfaces:**
- Consumes: Task 1 worktree `~/work/modern-termux/.worktrees/feat-upstream-sync` and `refs/remotes/upstream-app/master`.
- Produces: Clean `termux-app` commits on `feat/upstream-sync` before X11 commits are applied.

- [ ] **Step 1: Write failing unit tests covering Review Focus items and core `termux-app` fixes**

Add tests in `UpstreamSyncAppFixesTest.java` and `UpstreamTerminalFeaturesTest.java` asserting:
- `RunCommandService` policy check happens before `EXTRA_RESULT_DIRECTORY` file config is stored/used when `allow-external-apps` is violated.
- `TermuxOpenReceiver.ContentProvider.getType(Uri.parse("content://com.termux.files/"))` returns `null` without NPE.
- `KeyHandler` maps `kP` -> `KEYCODE_PAGE_UP` and `kN` -> `KEYCODE_PAGE_DOWN`.
- `TerminalSession.mProcessToTerminalIOQueue` capacity is `64 * 1024`.
- `TerminalEmulator` caches `getTopRow()` / `setTopRow(int)` and processes Sixel (`DCS q`) & iTerm2 (`OSC 1337`) bitmaps and `100KB` OSC 52 buffer.

- [ ] **Step 2: Run tests to verify RED**

Run (if Robolectric/JUnit works locally, or verify compilation failure): `./gradlew :terminal-emulator:testDebugUnitTest :app:testDebugUnitTest`
Expected: FAIL (missing `getTopRow`, `TerminalSixel`, `64 * 1024` capacity, inverted `kP`/`kN`, etc.).

- [ ] **Step 3: Cherry-pick approved `termux-app` commits onto `feat/upstream-sync`**

Run:
```bash
git cherry-pick ef4775b 30ebb2d 401bbe5 3b66f87 74ab512 47c473f 1021b58 5532cc4 8b72226 9adb09a fd2ab4f 3b27013 d8d6b02 4584488 b4a2c32 084d709
```
Resolve any conflict with `modern-termux` customizations cleanly (keeping custom paste safety & Turkish strings).

- [ ] **Step 4: Run unit tests to verify GREEN**

Run: `./gradlew :terminal-emulator:testDebugUnitTest :terminal-view:testDebugUnitTest :app:testDebugUnitTest`
Expected: PASS (0 failures), and commit test additions if separate.

---

### Task 3 (Agent 1): Port `termux-x11` Updates & One-Shot Heartbeat Flush in `modern-termux`

**Files:**
- Modify: `x11/src/main/aidl/com/termux/x11/ICmdEntryInterface.aidl`
- Modify: `x11/proguard-rules.pro`
- Modify: `x11/src/main/cpp/lorie/{InitOutput.c,InputXKB.c,activity.cpp,cmdentrypoint.cpp,lorie.h,renderer.cpp,buffer.c,buffer.h}`
- Create/Delete: `x11/src/main/cpp/lorie/glxconfigs.h` (replaces `fbconfigs.h` in `f1d9834`)
- Modify: `x11/src/main/cpp/patches/{xserver.patch,x11.patch}`
- Update submodules (gitlinks): `x11/src/main/cpp/libx11` (`b8d5a7046a98a244ee8ca3c9c07fd617c800dde2`), `x11/src/main/cpp/xserver` (`7dbd8d0dd1929c2011009fd1770c8aa470df97fb`)
- Modify: `x11/src/main/java/com/termux/x11/{CmdEntryPoint.java,LorieView.java,MainActivity.java}`
- Modify: `x11/src/main/java/com/termux/x11/input/{InputEventSender.java,TouchInputHandler.java}`
- Modify: `x11/src/main/res/xml/preferences.xml`, `x11/src/main/res/values/strings.xml`, `x11/src/main/res/values-tr/strings.xml`
- Modify: `app/build.gradle`, `terminal-emulator/build.gradle`, `terminal-view/build.gradle`, `termux-shared/build.gradle`, `NOTICE`, `x11/NOTICE`
- Test: `app/src/test/java/com/termux/app/TermuxX11IntegrationTest.java`

**Interfaces:**
- Consumes: Task 2 state on `modern-termux` `feat/upstream-sync` and `refs/remotes/upstream-x11/master`.
- Produces: Complete `feat/upstream-sync` branch at `v0.125.0` (`versionCode 134`) pushed to `origin`.

- [ ] **Step 1: Write failing integration/unit tests for X11 prefs, `EVENT_HEARTBEAT` one-shot flush, and `ScreenIdlePolicy` preservation**

Assert in `TermuxX11IntegrationTest.java` (or X11 unit test):
- `preferences.xml` includes invert scroll preference and both `values/strings.xml` and `values-tr/strings.xml` define its title/summary keys.
- `MainActivity` keeps `serverWatchdog` removed, `no_server_signal` unused, and exposes/triggers one-shot surface flush on heartbeat while preserving `ScreenIdlePolicy` & `setAppSleepInhibited` / `onX11ScreenSaverReset`.
- `x11/src/main/AndroidManifest.xml` still omits `BIND_ACCESSIBILITY_SERVICE`.

- [ ] **Step 2: Run tests to verify RED**

Run: `./gradlew :app:testDebugUnitTest --tests "*TermuxX11*"`
Expected: FAIL on missing invert-scroll preference / one-shot heartbeat flush hook.

- [ ] **Step 3: Port `termux-x11` commits with `lorie/` -> `x11/` mapping + submodule gitlink updates**

Apply commits in order from `29d4460` through `ed43f97` (excluding `1d36506`, `2d5e732`, `dc56088`, `3bf4324`, `c5d0920`, `a00942e`) via:
`git format-patch -1 --stdout <sha> | sed 's|a/lorie/|a/x11/|g; s|b/lorie/|b/x11/|g' | git am -3`
Update gitlinks with `git update-index --cacheinfo 160000,...` for `libx11` (`b8d5a7046a98a244ee8ca3c9c07fd617c800dde2`) and `xserver` (`7dbd8d0dd1929c2011009fd1770c8aa470df97fb`).
Adapt `MainActivity.java`, `LorieView.java`, `activity.cpp`, `InitOutput.c` so `ScreenIdlePolicy` / `ScreenSaverSuspend` wake-lock and `EVENT_HEARTBEAT` one-shot `surfacePending` flush work without `serverWatchdog`.

- [ ] **Step 4: Bump version to `0.125.0` (`versionCode 134`) and update `NOTICE` & `x11/NOTICE`**

Update `app/build.gradle`, `terminal-emulator/build.gradle`, `terminal-view/build.gradle`, `termux-shared/build.gradle`, `NOTICE`, `x11/NOTICE`.

- [ ] **Step 5: Verify tests locally & push `feat/upstream-sync` to `origin`**

Run: `./gradlew testDebugUnitTest && git push -u origin feat/upstream-sync`
Expected: Local unit tests pass and branch pushed cleanly (`master` untouched).

---

### Task 4 (Agent 2): Port `termux-app` Updates in `modern-termux-advanced` (`feat/upstream-sync-app`)

**Files:**
- Same `termux-app` files as Task 2 inside `~/work/modern-termux-advanced/.worktrees/feat-upstream-sync-app`.
- Test: `app/src/test/java/com/termux/app/UpstreamSyncAppFixesTest.java`, `terminal-emulator/src/test/java/com/termux/terminal/UpstreamTerminalFeaturesTest.java`.

**Interfaces:**
- Consumes: Task 1 worktree `.worktrees/feat-upstream-sync-app` and `refs/remotes/upstream-app/master`.
- Produces: Clean `feat/upstream-sync-app` branch ready for Lead Agent to merge into `feat/upstream-sync`. Does NOT touch `x11/` or `versionCode`/`versionName` (Lead Agent handles version bump after merging X11).

- [ ] **Step 1: Write failing unit tests for `RunCommandService`, `TermuxOpenReceiver.getType`, `KeyHandler`, `64KB` buffer, `getTopRow()`, Sixel/OSC 1337**
- [ ] **Step 2: Run unit tests to verify RED**
- [ ] **Step 3: Cherry-pick `ef4775b 30ebb2d 401bbe5 3b66f87 74ab512 47c473f 1021b58 5532cc4 8b72226 9adb09a fd2ab4f 3b27013 d8d6b02 4584488 b4a2c32 084d709` cleanly**
- [ ] **Step 4: Run unit tests to verify GREEN and commit cleanly on `feat/upstream-sync-app`**

---

### Task 5 (Lead Agent): Port `termux-x11` + Custom Mali/AHB/EXA GPU Preservation in `modern-termux-advanced`

**Files:**
- Modify: `x11/src/main/aidl/com/termux/x11/ICmdEntryInterface.aidl`
- Modify: `x11/proguard-rules.pro`
- Modify: `x11/src/main/cpp/lorie/{InitOutput.c,InputXKB.c,activity.cpp,cmdentrypoint.cpp,lorie.h,renderer.cpp,buffer.c,buffer.h}`
- Create/Delete: `x11/src/main/cpp/lorie/glxconfigs.h` (replaces `fbconfigs.h` in `f1d9834`)
- Preserve & verify: `x11/src/main/cpp/lorie/{renderer_format.h,dri3_safety.h}`, `x11/src/test/cpp/{renderer_format_test.cpp,dri3_safety_test.cpp}`
- Modify: `x11/src/main/cpp/patches/{xserver.patch,x11.patch}`
- Update submodules (gitlinks): `x11/src/main/cpp/libx11` (`b8d5a7046a98a244ee8ca3c9c07fd617c800dde2`), `x11/src/main/cpp/xserver` (`7dbd8d0dd1929c2011009fd1770c8aa470df97fb`)
- Modify: `x11/src/main/java/com/termux/x11/{CmdEntryPoint.java,LorieView.java,MainActivity.java}`
- Modify: `x11/src/main/java/com/termux/x11/input/{InputEventSender.java,TouchInputHandler.java}`
- Modify: `x11/src/main/res/xml/preferences.xml`, `x11/src/main/res/values/strings.xml`, `x11/src/main/res/values-tr/strings.xml`
- Modify: `app/build.gradle`, `terminal-emulator/build.gradle`, `terminal-view/build.gradle`, `termux-shared/build.gradle`, `NOTICE`, `x11/NOTICE`
- Test: `app/src/test/java/com/termux/app/TermuxX11IntegrationTest.java`, `x11/src/test/cpp/{dri3_safety_test.cpp,renderer_format_test.cpp}`

**Interfaces:**
- Consumes: Task 1 `.worktrees/feat-upstream-sync`, Task 4 `feat/upstream-sync-app`, and `refs/remotes/upstream-x11/master`.
- Produces: Combined `feat/upstream-sync` at `v0.125.0` (`versionCode 134`) pushed to `origin`.

- [ ] **Step 1: Write/extend failing tests for `TermuxX11IntegrationTest` (invert scroll, `EVENT_HEARTBEAT` one-shot flush, preserved Mali swizzle + `dri3_safety.h` + `clipList`) and verify C++ test baseline**

Run C++ tests:
```bash
clang++ -std=c++17 -Ix11/src/main/cpp/lorie x11/src/test/cpp/dri3_safety_test.cpp -o /tmp/dri3_test && /tmp/dri3_test && rm -f /tmp/dri3_test
clang++ -std=c++17 -Ix11/src/main/cpp/lorie x11/src/test/cpp/renderer_format_test.cpp -o /tmp/rf_test && /tmp/rf_test && rm -f /tmp/rf_test
```
*(Or execute in memory / temp binary cleaned up immediately.)*

- [ ] **Step 2: Apply non-GPU-removal `termux-x11` updates (`6dfb8b4`, `69bb7e3`/`d5ef3c4`/`f1d9834` parts compatible with `dri3_safety.h`, `e3a1657`, `a31042e`, `3753efd`)**

Explicitly **skip `a3c9c20`**. Rebase `xserver.patch` onto `xserver 21.1.26` (`7dbd8d0dd1929c2011009fd1770c8aa470df97fb`) while retaining `48e595e`'s Present GPU copy & `window->clipList` hunks. Update `libx11` and `xserver` gitlinks.

- [ ] **Step 3: Port `bf6663e`..`488dbbd`, `cdfd3f9`, and input fixes (`1bb8d6f`, `11c76bc`, `90acb76`, `7349dfa`, `ed43f97`) preserving Mali RGBA swizzle, `dri3_safety.h`, `ScreenSaverSuspend`, and `EVENT_HEARTBEAT` one-shot flush**

Ensure `renderer.cpp` inside the X server process retains `renderer_format.h` RGBA swizzle and `InitOutput.c` / `buffer.c` retain `dri3_safety.h` checks and `fbconfigs`/EGL handling cleanly.

- [ ] **Step 4: Merge Task 4 (`feat/upstream-sync-app`) into `feat/upstream-sync`, bump version to `0.125.0` (`134`), update `NOTICE` & `x11/NOTICE`**

Run: `git merge --no-ff feat/upstream-sync-app`

- [ ] **Step 5: Run full local unit + C++ tests, then push `feat/upstream-sync` to `origin`**

---

### Task 6 (Agent 3): Port `termux-app` + `APPLICATION_PREFERENCES` + Low-RAM Sixel/Image Guard in `modern-termux-tv`

**Files:**
- Modify: `app/src/main/AndroidManifest.xml` (for `8ec4dcd` `APPLICATION_PREFERENCES` -> `SettingsActivity` if absent)
- Modify: `app/src/main/java/com/termux/app/{RunCommandService.java,TermuxOpenReceiver.java,terminal/TermuxActivityRootView.java}`
- Modify: `terminal-emulator/src/main/java/com/termux/terminal/{KeyHandler.java,TerminalBuffer.java,TerminalEmulator.java,TerminalRow.java,TerminalSession.java,TextStyle.java}`
- Create: `terminal-emulator/src/main/java/com/termux/terminal/{AndroidUtils.java,ITermImage.java,TerminalBitmap.java,TerminalSixel.java}`
- Modify: `terminal-emulator/src/main/jni/termux.c`
- Modify: `terminal-view/src/main/java/com/termux/view/{TerminalRenderer.java,TerminalView.java}`
- Modify: `termux-shared/src/main/java/com/termux/shared/notification/NotificationUtils.java`
- Modify: `app/build.gradle`, `terminal-emulator/build.gradle`, `terminal-view/build.gradle`, `termux-shared/build.gradle`, `NOTICE`
- Test: `app/src/test/java/com/termux/app/LowRamImageGuardTest.java`, `terminal-emulator/src/test/java/com/termux/terminal/UpstreamTerminalFeaturesTest.java`

**Interfaces:**
- Consumes: Task 1 `.worktrees/feat-upstream-sync` in `modern-termux-tv` and `refs/remotes/upstream-app/master`.
- Produces: Clean `feat/upstream-sync` at `v0.125.0` (`versionCode 128`, NO `:x11`) pushed to `origin`.

- [ ] **Step 1: Write failing tests for TV-specific requirements + `termux-app` fixes**

Assert:
- `settings.gradle` still excludes `:x11` and no `x11/` directory exists.
- `LowRamPolicy` / image guard caps oversized Sixel / `OSC 1337` bitmap allocations on `<2GB` RAM TV devices.
- `RunCommandService` policy check order, `TermuxOpenReceiver.getType` null safety, `KeyHandler` `kP`/`kN`, `64KB` buffer, `getTopRow()`, `APPLICATION_PREFERENCES`.

- [ ] **Step 2: Run tests to verify RED**
- [ ] **Step 3: Cherry-pick `ef4775b 30ebb2d 401bbe5 3b66f87 74ab512 47c473f 1021b58 5532cc4 8b72226 9adb09a fd2ab4f 3b27013 d8d6b02 4584488 b4a2c32 084d709`, port `8ec4dcd` manifest intent-filter, and add Low-RAM bitmap/Sixel guard**
- [ ] **Step 4: Bump version to `0.125.0` (`versionCode 128`), update `NOTICE`, verify unit tests GREEN, push `feat/upstream-sync` to `origin`**

---

### Task 7 (Lead Agent): Trigger & Monitor GitHub Actions (`run_tests.yml` & `release_build.yml`) Across All 3 Repos

**Files:**
- None (CI verification across `Furkan00487/modern-termux`, `Furkan00487/modern-termux-advanced`, `Furkan00487/modern-termux-tv` on `feat/upstream-sync`).

- [ ] **Step 1: Verify `feat/upstream-sync` is pushed on all 3 repos and `master` was not modified**

Run:
```bash
for r in modern-termux modern-termux-advanced modern-termux-tv; do
  git -C ~/work/$r/.worktrees/feat-upstream-sync status --short
  git -C ~/work/$r rev-parse master origin/master
done
```
Expected: Clean worktrees, `master == origin/master`.

- [ ] **Step 2: Trigger `run_tests.yml` and `release_build.yml` via `gh workflow run --ref feat/upstream-sync` on all 3 repos**

Run:
```bash
for repo in Furkan00487/modern-termux Furkan00487/modern-termux-advanced Furkan00487/modern-termux-tv; do
  gh workflow run run_tests.yml --repo "$repo" --ref feat/upstream-sync
  gh workflow run release_build.yml --repo "$repo" --ref feat/upstream-sync
done
```

- [ ] **Step 3: Watch CI runs with `gh run watch` until completion and report exact run URLs & conclusions**

Expected: All 6 workflow runs (`run_tests.yml` x3 + `release_build.yml` x3) finish with `success`. If any fails, diagnose root cause, fix on `feat/upstream-sync`, push, and re-run CI.
