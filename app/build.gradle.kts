plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "org.trfl.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "org.trfl.app"; minSdk = 26; targetSdk = 35; versionCode = 71; versionName = "0.5.45"
        buildConfigField("boolean", "SFW_ONLY", "false")
        buildConfigField("String", "COMMIT", "\"${providers.gradleProperty("trflCommit").getOrElse("development")}\"")
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // Local phone distribution: keep the existing debug certificate so updates
            // preserve installed data. This is not a Play Store signing configuration.
            signingConfig = signingConfigs.getByName("debug")
        }
        create("play") {
            initWith(getByName("release"))
            applicationIdSuffix = ".play"
            buildConfigField("boolean", "SFW_ONLY", "true")
            // Store signing must be supplied securely before publishing.
            signingConfig = null
            matchingFallbacks += listOf("release")
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
