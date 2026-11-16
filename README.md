# 4X4 Android Dialer — experimental source

Target device: Samsung Galaxy Tab A9+ 5G SM-X216B.

This is **source code**, not a ready APK. Build with Android Studio (JDK 17, Android SDK 35). Open project directory, allow Gradle sync, then Build > Build APK(s). Android Studio may offer to install SDK components. No Gradle wrapper binary is bundled.

Pair 4X4 in Android Bluetooth settings, grant Nearby Devices permission, refresh paired devices, select the 4X4, connect, and inspect the log. You can attempt dialing only after connection succeeds. RFCOMM connection to an HFP AG is not guaranteed on stock Android: Android's system Bluetooth stack may own the HFP Client role, reject the connection, or require privileged permissions. HFP audio/SCO is **not implemented**. Do not mistake an RFCOMM connection or ATD success for working audio.

No proprietary CallCenter libraries used. This prototype does not install drivers, root the tablet, or change system Bluetooth settings. Use only with your own phone/SIM and test calls with consent. 
