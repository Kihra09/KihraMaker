plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.kihra.maker"
    compileSdk = 34
    ndkVersion = "23.2.8568313"

    defaultConfig {
        applicationId = "kihra.kihramaker.app"
        minSdk = 26
        targetSdk = 33
        // Every GitHub build gets a higher versionCode, so a new APK installs over the old one.
        val run = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = 100 + run
        versionName = "0.3.$run"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    // One fixed key for every build (kihramaker.jks in this folder). Android only installs an update
    // over an existing app when both APKs are signed with the same key.
    signingConfigs {
        create("kihra") {
            storeFile = file("kihramaker.jks")
            storePassword = "kihramaker"
            keyAlias = "kihramaker"
            keyPassword = "kihramaker"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("kihra")
        }
    }

    // The native engine (mkxp-z + SDL2 + Ruby). app/jni is created by scripts/build-engine.sh.
    externalNativeBuild {
        ndkBuild {
            path = file("jni/Android.mk")
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
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
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.documentfile:documentfile:1.0.1")
    // Used by the on-screen gamepad layout
    implementation("com.intuit.sdp:sdp-android:1.1.0")

    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
}
