# Zault

Zault is an Android gallery for photos and videos stored on the device. It includes albums, Favorites, Trash, and an encrypted vault with biometric protection. The app works locally; its gallery and vault features do not require a Gemini API key.

For implementation details and verification notes, see [IMPLEMENTATION.md](IMPLEMENTATION.md).

## Build and run

1. Install Android Studio and the Android SDK.
2. Open this directory as an Android project and let Gradle sync.
3. Select the `app` configuration and run it on an emulator or connected Android device.

To build from a terminal with a configured JDK and SDK:

```text
./gradlew :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Signing and local files

Build outputs, local SDKs, environment files, service configuration, and signing keys are excluded from Git. Release signing reads `KEYSTORE_PATH`, `STORE_PASSWORD`, and `KEY_PASSWORD` from the local environment. Keep the signing key used for an installed copy of the app if you need to update that installation without removing its data.

This project originated in [Google AI Studio](https://ai.studio/apps/2bd5cae7-2c51-4229-8b1e-bf6ec010dae4).
