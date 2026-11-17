# K-Dial Diagnostics v3

Android source project, not a compiled APK. Includes Bluetooth SDP UUIDs, bonded devices, public profile proxy connection states, Android audio device routing, communication device, mic RMS test (no audio saved), local tone test, optional SCO routing attempt, RFCOMM HFP/HSP tests, and safe AT commands.

**Limitations:** Android ordinary apps cannot force HFP Client or SCO audio to an external phone. SCO test only uses Android public audio routing and does not establish HFP by itself. AT commands require RFCOMM connection. Never run a live call test without confirming the intended number. Microphone test does not save or upload audio.

Build in Android Studio; no APK has been built or device tested here.
