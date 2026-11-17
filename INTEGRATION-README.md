# KosherDial v5 – Android HTML integration

- UI: `app/src/main/assets/index.html` (supplied HTML)
- Android native bridge: `MainActivity.java` (`KDialAndroid`)
- Pair the 4X4 phone in Android Bluetooth settings before starting.
- Tap **התחבר** in the top native toolbar; dial/hangup from the HTML UI.
- Contacts/tasks/calls are stored locally in Android WebView localStorage, not synced to phone.
- AT commands are sent over HFP RFCOMM. Successful send is **not** proof of established call.
- SCO call audio is **not implemented**. Audio through the tablet remains unverified.
- Build using GitHub Actions workflow. This package has not been compiled or device-tested here.
- HTML originally used Windows WebView2; bridge replaced with Android JavascriptInterface.
