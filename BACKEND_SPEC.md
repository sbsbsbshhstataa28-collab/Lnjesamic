# TOXIC Voice Relay backend contract

SOURCE is the Android phone microphone, NOT a Telegram source VC.
Pipeline:
Phone mic (48 kHz stereo PCM) -> DSP -> Telegram user account -> target Telegram VC.

DSP reference is `toxic_relay_final-20.py`:
AGC -> gain/volume/loudness -> bass/treble/presence/clarity filters -> gate -> compressor -> echo -> delay -> reverb -> stereo widen -> soft limit.
Controls are exposed as 0..400 in the TOXIC UI. Ghost Mute is removed.

IMPORTANT:
The Android app currently captures the microphone and provides the complete controller UI. Actual Telegram user-account MTProto/tgcalls publishing must be supplied by the native Telegram/tgcalls transport layer or a connected relay backend. Do not claim this APK alone publishes to a VC until that transport is implemented and tested.
