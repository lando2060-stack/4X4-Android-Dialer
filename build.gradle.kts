plugins { id("com.android.application") }
android { namespace = "com.example.fourxfourdialer"; compileSdk = 35
 defaultConfig { applicationId = "com.example.fourxfourdialer"; minSdk = 26; targetSdk = 32; versionCode = 1; versionName = "0.1" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
