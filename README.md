# Obeli

Obeli is an Android application for running open-source GGUF language models locally on device.

## Open in Android Studio

1. Install Android Studio and its Android SDK.
2. Open this repository's root folder (the folder containing `settings.gradle`).
3. Allow Gradle sync to finish.
4. Install Android SDK Platform 35, Build Tools 35.0.0, NDK 27.2.12479018, and CMake 3.22.1 from SDK Manager.
5. Select the `app` configuration and run it on an ARM64 Android device.

The project is a standard Gradle/Android application and does not require Sketchware.

## Build from command line

With Gradle 8.13, JDK 17, and the Android SDK configured:

```bash
gradle assembleDebug
```

The native inference layer is built from `app/src/main/cpp`.
