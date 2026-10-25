# Modern Termux

[![Release](https://img.shields.io/github/v/release/Furkan00487/modern-termux?label=release&color=00E5FF)](https://github.com/Furkan00487/modern-termux/releases/latest)
[![Release Build](https://github.com/Furkan00487/modern-termux/actions/workflows/release_build.yml/badge.svg)](https://github.com/Furkan00487/modern-termux/actions/workflows/release_build.yml)
[![Unit tests](https://github.com/Furkan00487/modern-termux/actions/workflows/run_tests.yml/badge.svg)](https://github.com/Furkan00487/modern-termux/actions/workflows/run_tests.yml)
[![License: GPL v3](https://img.shields.io/badge/license-GPL--3.0-blue.svg)](LICENSE)

**Modern Termux** is an enhanced fork of the [Termux](https://github.com/termux/termux-app) Android terminal emulator and Linux environment. It ships with built-in X11 graphical support, event-driven lifecycle management for low battery usage, and signed release builds.

> This project is a fork of [termux/termux-app](https://github.com/termux/termux-app). The terminal emulation, shell environment and package ecosystem originate from the upstream Termux project and its contributors.

## Contents

- [Features](#features)
- [Installation](#installation)
- [Verifying a Download](#verifying-a-download)
- [Upgrading and Uninstalling](#upgrading-and-uninstalling)
- [Building from Source](#building-from-source)
- [Debugging](#debugging)
- [Contributing](#contributing)
- [Upstream Resources](#upstream-resources)
- [License](#license)

## Features

- **Integrated X11 display server.** Termux:X11 is built into the app, so a graphical session needs no separate APK. Start it with the bundled `termux-x11` command.
- **Dynamic status bar icon.** The persistent notification icon switches to the X11 icon (`X_`) while the X11 screen is in the foreground and returns to the terminal icon (`>_`) when you leave it.
- **Battery-friendly lifecycle handling.** The former 200 ms `serverWatchdog` polling loop was removed. Notification updates are now event-driven and fire only when the X11 screen is entered or left, so the CPU is not woken while the app sits idle.
- **Signed releases.** Every release APK is signed in GitHub Actions with a dedicated private release key (APK Signature Scheme v2 and v3). No public test key is used.
- **Verified downloads.** Each release publishes exactly one APK and one SHA-256 checksum file.
- **Turkish welcome banner.** The login message (MOTD) is localized.
- **Neon cyan theme.** The primary accent color is `#00E5FF`.

## Installation

Modern Termux requires **Android 7.0 or later** and currently publishes an **`arm64-v8a`** build.

1. Open the [latest release](https://github.com/Furkan00487/modern-termux/releases/latest).
2. Download `termux-app_<version>-apt-android-7-github-release_arm64-v8a.apk` and its `..._sha256sums` file.
3. [Verify the download](#verifying-a-download), then install the APK.

### Compatibility with other Termux builds

Modern Termux is signed with its own key, so it is **not** compatible with APKs signed by other sources such as F-Droid, the upstream GitHub builds or Google Play. Android refuses to install an app over one signed with a different key (for example `INSTALL_FAILED_UPDATE_INCOMPATIBLE` or `signatures do not match previously installed version`). To switch, uninstall every existing Termux app and plugin first, then install Modern Termux. Consider [backing up Termux](https://wiki.termux.com/wiki/Backing_up_Termux) before you do.

Termux plugins such as Termux:API, Termux:Boot and Termux:Widget share the `com.termux` user ID with the main app and must be signed with the same key. Plugins from other sources will not work alongside Modern Termux.

## Verifying a Download

Check the SHA-256 checksum from the folder that contains both files:

```bash
sha256sum -c termux-app_<version>-apt-android-7-github-release_sha256sums
```

Check the APK signature with `apksigner` (from the Android SDK build tools or the Termux `apksigner` package):

```bash
apksigner verify --verbose termux-app_<version>-apt-android-7-github-release_arm64-v8a.apk
```

The output should report `Verified using v2 scheme (APK Signature Scheme v2): true` and the same for v3.

## Upgrading and Uninstalling

Installing a newer release over an existing Modern Termux installation upgrades it in place and keeps your data. On the first launch after an upgrade (a higher `versionCode`), the app refreshes the bundled `termux-x11` helper scripts and the welcome banner.

To uninstall, remove Modern Termux and any Termux plugin apps from `Android Settings` -> `Apps`.

## Building from Source

```bash
git clone --recursive https://github.com/Furkan00487/modern-termux.git
cd modern-termux
./gradlew testDebugUnitTest      # run the unit tests
./gradlew downloadBootstraps assembleRelease
```

The repository includes native X11 code as git submodules, so clone with `--recursive`. Release signing is configured through the `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD` and `KEYSTORE_ALIAS` environment variables, which are provided by GitHub Secrets in CI. Without them `assembleRelease` produces an unsigned APK, which Android will not install until you sign it with your own key.

Releases are produced by the [Release Build](.github/workflows/release_build.yml) workflow.

## Debugging

Modern Termux uses a localized Turkish UI by default. To capture detailed diagnostic logs for troubleshooting:

1. **Open Settings:**
   - Swipe from the left edge of the screen to open the navigation drawer and tap the gear icon ⚙️ (**Ayarlar**), or
   - Long-press anywhere on the terminal screen and select **Ayarlar** (**Settings**).
2. **Navigate to Log Level:**
   - Tap **Modern Termux** (or **Termux**).
   - Tap **Hata Ayıklama** (**Debugging**).
   - Under **Günlük Kaydı (Logging)**, select **Günlük Düzeyi** (**Log Level**).
3. **Change Log Level:**
   - Select **Ayrıntılı (Verbose)** (or **Hata Ayıklama (Debug)**).
   - **Normal** is the default. Always revert back to **Normal** after capturing logs, as verbose logging increases execution overhead and writes detailed session data to `logcat`.

You can dump or filter `logcat` directly from Termux:

```bash
logcat -d | grep -i "com.termux"
```

Or from a connected computer via ADB:

```bash
adb logcat -s "termux:*"
```

When you report a problem in the [issue tracker](https://github.com/Furkan00487/modern-termux/issues), please include your Modern Termux version, Android version, device model, and the relevant `logcat` output.

## Contributing

- Keep changes focused and run `./gradlew testDebugUnitTest` before opening a pull request.
- Use [Conventional Commits](https://www.conventionalcommits.org) for commit messages, for example `feat: add dynamic notification icon` or `fix: guard null service instance`.
- Bump `versionCode` and `versionName` in `app/build.gradle`, and the library versions in `terminal-emulator`, `terminal-view` and `termux-shared`, with every release. `versionName` must follow [Semantic Versioning 2.0.0](https://semver.org/spec/v2.0.0.html) in the form `major.minor.patch`.
- Shared constants and utilities belong in the `termux-shared` library rather than being hardcoded. Respect the licenses of any external code you add.

## Upstream Resources

Modern Termux keeps the upstream package name, so the Termux documentation applies to most usage questions.

- [Termux Wiki](https://wiki.termux.com/wiki/)
- [Termux App Wiki](https://github.com/termux/termux-app/wiki)
- [Package Management](https://wiki.termux.com/wiki/Package_Management)
- [Termux File System Layout](https://github.com/termux/termux-packages/wiki/Termux-file-system-layout)
- [Differences From Linux](https://wiki.termux.com/wiki/Differences_from_Linux)
- [Running Commands in Termux From Other Apps via `RUN_COMMAND` intent](https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent)
- [Termux Packages](https://github.com/termux/termux-packages), the source of the bootstrap archives and installable packages

Note that the upstream wiki, community channels and issue trackers are for the upstream Termux project. Report Modern Termux problems in this repository's [issue tracker](https://github.com/Furkan00487/modern-termux/issues).

## License

This project is licensed under the [GNU General Public License v3.0](LICENSE).

### Exceptions

- Code from [Terminal Emulator for Android](https://github.com/jackpal/Android-Terminal-Emulator) is released under the [Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0) license. See the [`terminal-view`](terminal-view) and [`terminal-emulator`](terminal-emulator) libraries.
- See [`termux-shared/LICENSE.md`](termux-shared/LICENSE.md) for exceptions that apply to the `termux-shared` library.
