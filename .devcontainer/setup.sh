#!/usr/bin/env bash
set -e
SDK=/usr/local/android-sdk
sudo apt-get update && sudo apt-get install -y unzip cmake ninja-build
sudo mkdir -p $SDK/cmdline-tools && sudo chown -R "$(id -u):$(id -g)" $SDK
curl -fsSL -o /tmp/cmdtools.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip -q /tmp/cmdtools.zip -d $SDK/cmdline-tools
mv $SDK/cmdline-tools/cmdline-tools $SDK/cmdline-tools/latest
rm /tmp/cmdtools.zip
yes | $SDK/cmdline-tools/latest/bin/sdkmanager --licenses >/dev/null || true
$SDK/cmdline-tools/latest/bin/sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0" "ndk;29.0.14206865"
