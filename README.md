# 1demoapp — NVIDIA NIM Android chat app

A lightweight native Android chat app with NVIDIA NIM support, voice input and spoken responses. API keys are entered inside the app and stored encrypted using Android Keystore; no secret is embedded in the repository or APK.

## Download APK

Open [GitHub Actions](../../actions/workflows/android-apk.yml), select a successful **Build Android APK** run, and download its `1demoapp-debug-apk` artifact ZIP. Extract `app-debug.apk` on your phone and install it.

## Connect NVIDIA NIM

In the installed app tap **API / Settings** and paste your NVIDIA NIM key from https://build.nvidia.com/ . Use the model ID from your NVIDIA account. The app makes HTTPS calls to `https://integrate.api.nvidia.com/v1/chat/completions`.

## What works

Native Android chat, message history within the current session, configurable model, encrypted-on-device key, speech-to-text using installed Android speech service, text-to-speech, and APK build using GitHub Actions.

**Limitations:** This is a first working app build, not a device-controlling autonomous agent, and it has no screen-sharing/floating overlay or cloud computer yet. NVIDIA API access depends on NVIDIA account quotas and enabled models. Do not put API keys in source files, GitHub commits or APK build variables.

## Build

Uses Gradle 8.9, Android Gradle Plugin 8.7.3, JDK 17. The workflow builds a debug APK (signed using the Android debug key) automatically after every push to `main`, or manually using the Actions tab.
