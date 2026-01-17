plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.app.sixtyseven"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.app.sixtyseven"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // API endpoint
        buildConfigField("String", "API_BASE_URL", "\"https://gesticulative-tartily-julietta.ngrok-free.dev\"")
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
        // Allow newer Kotlin metadata
        freeCompilerArgs += listOf("-Xskip-metadata-version-check")
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    // Meta WDA SDK from GitHub Packages
    implementation(libs.meta.wda.core)
    implementation(libs.meta.wda.camera)
    
    // Android core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.coordinatorlayout)
    implementation(libs.lifecycle.runtime)
    implementation(libs.exifinterface)
    
    // Networking - for sending media to your friend's server
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    
    // Coroutines
    implementation(libs.coroutines)
    
    // Image loading
    implementation(libs.coil)
}
