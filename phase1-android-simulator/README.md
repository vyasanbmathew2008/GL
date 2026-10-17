# Phase 1 — Android Security Test Simulator (Offline)

An Android Kotlin app for controlled antivirus research. It generates temporary synthetic JPEGs and records simulated upload events locally. It does not access the photo gallery, request network permission, contact Telegram, or transmit images.

## Layout
- `android/SpywareBehaviorSimulator/`: Android Studio project
- No local mock server, external API integration, real credentials, antivirus app, or Phase 2 implementation is included.

## Run Android
Open `android/SpywareBehaviorSimulator` in Android Studio, use JDK 17 and Android SDK 35, sync Gradle, then build and install the debug APK. A warning appears before the dashboard. Press **Start Simulation** to explicitly begin. Choose 1–20 synthetic images; each generated file is deleted after its event is logged.

## Safety notes
The app has no `INTERNET` permission and no gallery/media permissions. Events are simulated and are not sent anywhere. Do not commit bot tokens or other secrets to source control.
