plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.arcis"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.arcis"
        minSdk = 30
        targetSdk = 36
        // Release builds get these from the git tag (see .github/workflows/release.yml).
        versionCode = (findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = (findProperty("versionName") as String?) ?: "0.1.0"
    }

    // Release signing: the keystore and passwords come from environment variables (GitHub
    // secrets in CI, see docs/releasing.md); never from the repository.
    val keystore = System.getenv("ARCIS_KEYSTORE")
    signingConfigs {
        create("release") {
            if (keystore != null) {
                storeFile = file(keystore)
                storePassword = System.getenv("ARCIS_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ARCIS_KEY_ALIAS")
                keyPassword = System.getenv("ARCIS_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystore != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.zxing.android.embedded)
    testImplementation(libs.junit)
}
