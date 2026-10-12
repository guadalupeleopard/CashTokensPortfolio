plugins { id("com.android.application") }
android { namespace = "org.example.cashtokensportfolio"; compileSdk = 35
 defaultConfig { applicationId = "org.example.cashtokensportfolio"; minSdk = 26; targetSdk = 35; versionCode = 1; versionName = "1.0" }
}
dependencies { implementation("com.caverock:androidsvg-aar:1.4") }
