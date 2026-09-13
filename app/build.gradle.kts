plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.hudmapapp"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.hudmapapp"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    dependencies {
        implementation(platform(libs.androidx.compose.bom))
        implementation(libs.androidx.activity.compose)
        implementation(libs.androidx.compose.material3)
        implementation(libs.androidx.compose.ui)
        implementation(libs.androidx.compose.ui.graphics)
        implementation(libs.androidx.compose.ui.tooling.preview)
        implementation(libs.androidx.core.ktx)
        implementation(libs.androidx.lifecycle.runtime.ktx)
        implementation(libs.androidx.navigation.compose)

        testImplementation(libs.junit)

        androidTestImplementation(platform(libs.androidx.compose.bom))
        androidTestImplementation(libs.androidx.junit)
        androidTestImplementation(libs.androidx.espresso.core)
        androidTestImplementation(libs.androidx.compose.ui.test.junit4)

        debugImplementation(libs.androidx.compose.ui.test.manifest)
        debugImplementation(libs.androidx.compose.ui.tooling)
    }
}