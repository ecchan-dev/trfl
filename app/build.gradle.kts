plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "org.trfl.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "org.trfl.app"; minSdk = 26; targetSdk = 35; versionCode = 104; versionName = "1.0.3"
        buildConfigField("boolean", "SFW_ONLY", "false")
        buildConfigField("String", "COMMIT", "\"${providers.gradleProperty("trflCommit").getOrElse("development")}\"")
    }
    // Release signing is supplied outside the repository. Set TRFL_RELEASE_STORE_FILE,
    // TRFL_RELEASE_STORE_PASSWORD, TRFL_RELEASE_KEY_ALIAS and TRFL_RELEASE_KEY_PASSWORD.
    signingConfigs {
        create("trflRelease") {
            System.getenv("TRFL_RELEASE_STORE_FILE")?.let { storeFile = file(it) }
            storePassword = System.getenv("TRFL_RELEASE_STORE_PASSWORD")
            keyAlias = System.getenv("TRFL_RELEASE_KEY_ALIAS")
            keyPassword = System.getenv("TRFL_RELEASE_KEY_PASSWORD")
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // Use the release key when provided; otherwise fall back to the debug key (local builds only).
            signingConfig = if (System.getenv("TRFL_RELEASE_STORE_FILE") != null) signingConfigs.getByName("trflRelease") else signingConfigs.getByName("debug")
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("junit:junit:4.13.2")
}
