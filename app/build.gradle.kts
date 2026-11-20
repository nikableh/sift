plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "moe.nikableh.sift"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "moe.nikableh.sift"
        minSdk = 30
        targetSdk = 37
        versionCode = 11
        versionName = "0.11"
    }
    signingConfigs {
        // The release workflow sets these from repository secrets, see docs/RELEASING.md
        val keystore = providers.environmentVariable("SIFT_KEYSTORE_FILE").orNull
        if (keystore != null) {
            create("release") {
                storeFile = file(keystore)
                storePassword = providers.environmentVariable("SIFT_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("SIFT_KEY_ALIAS").get()
                // A PKCS12 keystore has one password for the store and the key
                keyPassword = storePassword
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // Without the production key, keep the debug key so a release build installs over
            // earlier builds made on this PC
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    dependenciesInfo {
        // F-Droid rejects this block in APKs because only Google can decrypt it. Play reads it
        // from the bundle, so that one keeps it
        includeInApk = false
    }
    buildFeatures {
        compose = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
        )
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    // Material 3 Expressive components ship in 1.5, the BOM still pins 1.4
    implementation("androidx.compose.material3:material3:1.5.0-rc01")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("io.coil-kt.coil3:coil-compose:3.6.3")
    implementation("io.coil-kt.coil3:coil-video:3.6.3")
    implementation("androidx.media3:media3-exoplayer:1.11.1")
}
