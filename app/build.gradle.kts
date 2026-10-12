plugins { id("com.android.application") }

android {
    namespace = "org.example.cashtokensportfolio"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.example.cashtokensportfolio"
        minSdk = 26
        targetSdk = 35
        // GitHub Actions run numbers increase with each workflow run, allowing
        // newer APKs to install over older ones. Local builds default to 2.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 2
        versionName = "1.0"
    }
}

dependencies {
    implementation("com.caverock:androidsvg-aar:1.4")
}
