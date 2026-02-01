#!/bin/bash
set -e
set +H

URL_SDK="https://dl.google.com/android/repository/commandlinetools-linux-14742923_latest.zip"
ANDROID_HOME="$HOME/.android"

mkdir -p "$ANDROID_HOME/cmdline-tools"
cd "$ANDROID_HOME"

# Download and extract command line tools
wget -q "$URL_SDK" -O sdk.zip
unzip -q sdk.zip
rm sdk.zip

# Move to the correct directory structure (cmdline-tools/latest/)
mv cmdline-tools latest
mkdir -p cmdline-tools
mv latest cmdline-tools/

# Set up environment variables in shell rc files
{
    echo ''
    echo '# Android SDK'
    echo 'export ANDROID_HOME="$HOME/.android"'
    echo 'export ANDROID_SDK_ROOT="$ANDROID_HOME"'
    echo 'export PATH="$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools"'
} >> "$HOME/.bashrc"

{
    echo ''
    echo '# Android SDK'
    echo 'export ANDROID_HOME="$HOME/.android"'
    echo 'export ANDROID_SDK_ROOT="$ANDROID_HOME"'
    echo 'export PATH="$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools"'
} >> "$HOME/.zshrc"

# Export for current session to allow sdkmanager to run
export ANDROID_HOME="$HOME/.android"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$PATH:$ANDROID_HOME/cmdline-tools/latest/bin"

# Accept licenses
yes | sdkmanager --licenses > /dev/null 2>&1 || true

# Install SDK components required by Mobile Unit Client
sdkmanager "platforms;android-35" "build-tools;35.0.0" "platform-tools"
