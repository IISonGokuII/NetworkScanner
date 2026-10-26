plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// In GitHub Actions steigt die Versionsnummer mit jedem Build – so lassen sich Updates über die alte Version installieren.
val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

// Optional eigener, privater Schlüssel (als GitHub-Secret). Ohne Secret wird der Schlüssel aus dem Repo verwendet.
val releaseKeystore = System.getenv("SYNAPSE_KEYSTORE_PATH")?.let { file(it) }?.takeIf { it.exists() }

android {
    namespace = "com.mycompany.synapse"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mycompany.synapse"
        minSdk = 29
        targetSdk = 34
        versionCode = 1 + buildNumber
        versionName = "2.0.$buildNumber"
    }

    // Fester Schlüssel im Repo: Jede neue APK aus GitHub Actions lässt sich über die
    // alte installieren, ohne dass der Trainingsfortschritt verloren geht.
    signingConfigs {
        getByName("debug") {
            storeFile = file("synapse-debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (releaseKeystore != null) {
            create("private") {
                storeFile = releaseKeystore
                storePassword = System.getenv("SYNAPSE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("SYNAPSE_KEY_ALIAS")
                keyPassword = System.getenv("SYNAPSE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("private") ?: signingConfigs.getByName("debug")
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
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
