plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.pocketpet"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pocketpet"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                arguments += "-DANDROID_STL=c++_shared"
            }
        }

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "MODEL_BASE_URL", "\"https://github.com/pocketpet/models/releases/download/v1/\"")
        }
        debug {
            isDebuggable = true
            buildConfigField("String", "MODEL_BASE_URL", "\"https://github.com/pocketpet/models/releases/download/v1/\"")
        }
    }

    flavorDimensions += "modelSize"
    productFlavors {
        create("lite") {
            dimension = "modelSize"
            buildConfigField("String", "LLM_MODEL_FILE", "\"qwen3.5-0.8b-q4.mnn\"")
            buildConfigField("Long", "MIN_RAM_BYTES", "4L * 1024 * 1024 * 1024")
        }
        create("standard") {
            dimension = "modelSize"
            buildConfigField("String", "LLM_MODEL_FILE", "\"qwen3.5-2b-q4.mnn\"")
            buildConfigField("Long", "MIN_RAM_BYTES", "6L * 1024 * 1024 * 1024")
        }
        create("pro") {
            dimension = "modelSize"
            buildConfigField("String", "LLM_MODEL_FILE", "\"qwen3.5-4b-q4.mnn\"")
            buildConfigField("Long", "MIN_RAM_BYTES", "8L * 1024 * 1024 * 1024")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons.extended)

    implementation(libs.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.onnxruntime.android)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.datastore.preferences)
    implementation(libs.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.junit.android)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}
