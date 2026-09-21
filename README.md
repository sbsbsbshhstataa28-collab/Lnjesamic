# 🔥 TOXIC Voice Relay — MIC → DSP → Telegram VC

**Architecture:** Android phone microphone → WebSocket → Python/Telethon/PyTgCalls backend → target Telegram voice chat.

The uploaded `toxic_relay_final-20.py` is the DSP reference. Numeric UI controls are 0–400 and Ghost Mute is removed.

## Backend
1. Python 3.11+ VPS.
2. `pip install -r backend_requirements.txt`
3. Copy `backend.env.example` to `.env` and set API_ID/API_HASH/CONTROL_TOKEN.
4. First run without SESSION_STRING to let Telethon perform interactive login, then the session file is saved. Keep it private.
5. `python relay_server.py`

Open TCP port 8765 (or reverse proxy it with TLS). For internet use, prefer `wss://` and a strong token.

## Android
GitHub Actions builds the APK. Enter `ws://HOST:8765` (or `wss://...`), token, and target VC ID. Connect → Join → Start Relay.

The backend is the Telegram user-account transport; the Android app is the microphone/DSP controller and streams raw 48 kHz stereo PCM.
