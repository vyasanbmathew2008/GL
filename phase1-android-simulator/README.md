# Phase 1 — Android Image Sharing Test App

An Android Kotlin app with a mandatory disclosure screen and a Telegram image-sharing test workflow.

## What it does
- Shows a warning explaining that selected images are transmitted to Telegram.
- Requires the user to press **Start** before opening the app dashboard.
- Lets the user enter a Telegram bot token and destination chat ID at runtime.
- Uses Android's document picker so the user explicitly chooses the images to send.
- Sends only those selected images using Telegram's `sendPhoto` API and reports success or failure.
- Does not scan the gallery or automatically upload all device images.
- Does not store the bot token in source code or intentionally persist it; enter a test bot token at runtime.

## Run Android
Open `android/SpywareBehaviorSimulator` in Android Studio, use JDK 17 and Android SDK 35, sync Gradle, then build and install the debug APK. Use a test bot and a chat you control. Press **Start**, enter the token and chat ID, choose the images, and press **Send selected images**.

## Security notes
The app requires Internet permission to make Telegram API requests. Treat a bot token like a password: never commit it to GitHub, logs, or screenshots. If a token has been exposed, revoke it through BotFather and create a replacement. Only select images you own or have permission to share.

## Network testing
Telegram API requests use HTTPS. You can observe connection metadata with network tools; inspecting HTTPS request bodies requires a test setup where the app/device trusts the inspection proxy certificate. Do not disable TLS validation in production.
