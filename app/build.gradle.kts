plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.azenithtech.azenithai"   // HARUS sama dengan PKG di service.sh / action.sh module
    compileSdk = 34

    defaultConfig {
        applicationId = "com.azenithtech.azenithai"
        minSdk = 28            // sama dengan syarat module (Android 9+)
        targetSdk = 34
        versionCode = 290
        versionName = "2.9.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Di-install lewat `pm install` oleh module (root), jadi cukup ditandatangani debug key
            // supaya `assembleRelease` langsung menghasilkan APK yang bisa dipasang.
            // Mau key sendiri? ganti signingConfig di sini.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

// Tanpa dependency tambahan sengaja: app hanya WebView + shell root.
