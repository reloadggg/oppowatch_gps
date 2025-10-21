plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.kartlap.se"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.kartlap.se"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}

// Copy compiled APKs into the root-level Releases directory after build
val releasesDir = rootProject.layout.projectDirectory.dir("Releases").asFile
val appVersionName = android.defaultConfig.versionName ?: "unspecified"

// Debug APK -> Releases
tasks.register<org.gradle.api.tasks.Copy>("copyDebugApkToReleases") {
    dependsOn("assembleDebug")
    from(layout.buildDirectory.dir("outputs/apk/debug"))
    include("*.apk")
    into(releasesDir)
    // Rename to include app name, version and build type
    rename { _ -> "KartLapSE-v${appVersionName}-debug.apk" }
}

tasks.named("assembleDebug").configure {
    finalizedBy("copyDebugApkToReleases")
}

// Release APK -> Releases (if you have signing configured)
tasks.register<org.gradle.api.tasks.Copy>("copyReleaseApkToReleases") {
    dependsOn("assembleRelease")
    from(layout.buildDirectory.dir("outputs/apk/release"))
    include("*.apk")
    into(releasesDir)
    rename { _ -> "KartLapSE-v${appVersionName}-release.apk" }
}

tasks.named("assembleRelease").configure {
    finalizedBy("copyReleaseApkToReleases")
}
