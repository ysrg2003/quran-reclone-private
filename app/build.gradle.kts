plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.quranoffline"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.quranoffline"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // Private release key lives outside the repo; path and password come from the build environment only.
        System.getenv("RECLONE_KS")?.let { ks ->
            create("reclone") {
                storeFile = file(ks)
                storePassword = System.getenv("RECLONE_KS_PASS")
                keyAlias = "reclone"
                keyPassword = System.getenv("RECLONE_KS_PASS")
            }
        }
    }

    buildTypes {
        release {
            // Private test build: non-debuggable (R8 too heavy for the build machine) and signed with the debug key so it
            // installs over the earlier debug-signed test build with the same app id.
            signingConfig = if (System.getenv("RECLONE_KS") != null) signingConfigs.getByName("reclone") else signingConfigs.getByName("debug")
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("online") {
            dimension = "distribution"
            applicationIdSuffix = ".apiaudioreclone"
            versionNameSuffix = "-original-api-audio-reclone-private"
            buildConfigField("boolean", "OFFLINE_DISTRIBUTION", "false")
        }
        create("offline") {
            dimension = "distribution"
            applicationIdSuffix = ".offline"
            versionNameSuffix = "-offline"
            buildConfigField("boolean", "OFFLINE_DISTRIBUTION", "true")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        noCompress += listOf("mp3", "m4a")
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
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
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    testImplementation("junit:junit:4.13.2")
}
