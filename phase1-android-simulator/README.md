# Phase 1 — Android Image Sharing Test App

An Android Kotlin app with a mandatory disclosure screen and a Telegram image-transfer workflow scoped to a dedicated test folder.

## What it does
- Shows a warning explaining that images in the app's dedicated test folder will be transmitted to Telegram.
- Requires the user to acknowledge the warning before opening the dashboard.
- Lets the user enter a Telegram bot token and destination chat ID at runtime.
- On **Start sending test-folder images**, sends every supported image currently in the app-specific `TelegramTestQueue` folder.
- Supports JPG, JPEG, PNG, WEBP, GIF, and BMP files, and reports success or failure.
- Does not scan the device gallery or other folders, and does not store the bot token in source code or intentionally persist it.

## Add test images
Install and launch the app once to create the folder, then place only images you intend to send in the app-specific external pictures folder:

```text
Android/data/com.example.spysim/files/Pictures/TelegramTestQueue/
```

On an emulator, you can copy files into the app's external files directory with Android Debug Bridge after launching the app. The full path is also shown in the app. If the folder is empty, the app sends nothing and shows a status message.

## Run Android
Open `android/SpywareBehaviorSimulator` in Android Studio, use JDK 17 and Android SDK 35, sync Gradle, then build and install the debug APK. Use a test bot and a chat you control. Acknowledge the warning, enter the token and chat ID, and press **Start sending test-folder images**.

## Security notes
The app requires Internet permission to make Telegram API requests. Treat a bot token like a password: never commit it to GitHub, logs, or screenshots. If a token has been exposed, revoke it through BotFather and create a replacement. Only put images in the test folder that you own or have permission to share.

## Network testing
Telegram API requests use HTTPS. You can observe connection metadata with network tools; inspecting HTTPS request bodies requires a test setup where the app/device trusts the inspection proxy certificate. Do not disable TLS validation in production.
