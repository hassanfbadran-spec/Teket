plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.clinic.tickets"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.clinic.tickets"
        minSdk = 24
        targetSdk = 33
        versionCode = 2
        versionName = "1.1"
    }

    signingConfigs {
        create("fixed") {
            storeFile = file("signing.keystore")
            storePassword = "clinic123"
            keyAlias = "clinic"
            keyPassword = "clinic123"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("fixed")
        }
        release {
            isMinifyEnabled = false
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

dependencies {
    // مكتبة طابعة Sunmi الرسمية
    implementation("com.sunmi:printerlibrary:1.0.18")
}
