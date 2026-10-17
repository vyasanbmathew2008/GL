# Phase 1 — Android Security Test Simulator

A single Android Kotlin app and a local FastAPI mock server for controlled antivirus research. This is a simulation only: it generates synthetic JPEGs, uses fixed placeholder credentials, and never calls or forwards data to Telegram.

## Layout
- `android/SpywareBehaviorSimulator/`: Android Studio project
- `local-mock-server/`: local mock Telegram-shaped API
- No antivirus app or Phase 2 implementation is included.

## Run the mock server
Python 3.10+:
```bash
cd local-mock-server
python -m venv .venv
# Linux/macOS: source .venv/bin/activate
# Windows: .venv\Scripts\activate
python -m pip install -r requirements.txt
uvicorn server:app --host 0.0.0.0 --port 8000
```
Health check: `http://127.0.0.1:8000/health`.

Bind only on an isolated test network and firewall port 8000 to trusted lab devices. Do not expose the server to the public internet.

## Run Android
Open `android/SpywareBehaviorSimulator` in Android Studio, use JDK 17 and Android SDK 35, sync Gradle, then build and install the debug APK. The launch warning appears before test activity. Press **Start Simulation** to explicitly begin. On an emulator, the host computer is usually `http://10.0.2.2:8000`; on a physical device use the host's private LAN IPv4 address.

## Test modes
Set `MOCK_RESPONSE_MODE=success`, `server_error`, or `rate_limit` before starting Uvicorn. Server metadata is appended to `events.jsonl`; image bytes and credentials are not persisted. The endpoint only accepts the fixed test token `TEST_BOT_TOKEN` and chat ID `TEST_CHAT_ID`.

## Safety notes
No gallery permissions, background persistence, stealth, or real Telegram integration. HTTP is enabled only for lab traffic. Keep devices and server on an isolated network.