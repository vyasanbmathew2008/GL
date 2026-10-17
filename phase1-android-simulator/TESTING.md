# Local test configuration (example only)

The public repository intentionally contains no working Telegram bot token or chat ID.

For a controlled test, use non-sensitive test images and a bot/chat you own. Keep real credentials in local, untracked configuration or environment variables; never commit them to GitHub.

Example placeholders (not valid credentials):

```properties
TELEGRAM_BOT_TOKEN=123456789:REPLACE_WITH_YOUR_TEST_BOT_TOKEN
TELEGRAM_CHAT_ID=123456789
```

The current Android simulator is offline-only: it does not read the device photo gallery or send files to Telegram. Its Start button begins the local simulation using generated test data. Do not treat the placeholder values above as working credentials.
