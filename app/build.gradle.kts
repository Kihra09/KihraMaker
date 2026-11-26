plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.kihra.maker"
    compileSdk = 34
    ndkVersion = "23.2.8568313"

    defaultConfig {
        applicationId = "com.kihra.maker"
        minSdk = 26
        targetSdk = 33
        versionCode = 2
        versionName = "0.2.0"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Debug key: installable straight away. Swap for your own key to publish.
            signingConfig = signingConfigs.getByName("debug")
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
