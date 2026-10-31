plugins { id("com.android.application") }

android {
    namespace = "org.example.cashtokensportfolio"
    compileSdk = 35

    // GitHub Actions installs the repository's persistent signing keystore at
    // ~/.android/debug.keystore before Gradle runs. Use it explicitly for every
    // build type so debug and release APKs share the same signing identity.
    signingConfigs {
        create("cashTrack") {
            storeFile = file("${System.getProperty("user.home")}/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    defaultConfig {
        applicationId = "org.example.cashtokensportfolio"
        minSdk = 26
        targetSdk = 35
        // GitHub Actions run numbers increase with each workflow run, allowing
        // newer APKs to install over older ones. Local builds default to 2.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 2
        versionName = "1.0.7"
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("cashTrack")
        }
        getByName("release") {
            signingConfig = signingConfigs.getByName("cashTrack")
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation("androidx.work:work-runtime:2.10.0")
    implementation("com.caverock:androidsvg-aar:1.4")
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
