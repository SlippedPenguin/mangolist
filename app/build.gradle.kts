import com.apollographql.apollo.gradle.api.ApolloExtension

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.apollo)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.slippedpenguin.mangolist"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.slippedpenguin.mangolist"
        minSdk = 26      // Covers ~95% of active devices; required by Coil 3 / Material You deps
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            // v1 is personal-use sideload: keep minify off, leave the debug-signed APK flow
            // (a proper release keystore is a v1.x concern, not the first scaffold).
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Debug-signed for now → configures a temp .jks in CI; revisit for v1.x.
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

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Apollo: scans app/src/main/graphql/ for *.graphql query files.
// schema.graphqls is auto-fetched on the first build via introspection
// (endpoint: https://graphql.anilist.co). Subsequent builds use the cached schema.
apollo {
    service("anilist") {
        packageName.set("com.slippedpenguin.mangolist.graphql")
        generateKotlinModels.set(true)
    }
}

dependencies {
    // Core / lifecycle
    // NOTE: we intentionally don't pull androidx-lifecycle-viewmodel-ktx
    // separately — Compose's lifecycle-viewmodel-compose already brings in the
    // ViewModel APIs and `androidx.lifecycle:lifecycle-viewmodel` is on the
    // classpath via dependencies.androidx-lifecycle-runtime-ktx transitively.
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose (via BOM)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Image loading
    implementation(libs.material)
    implementation(libs.coil.compose)

    // GraphQL
    implementation(libs.apollo.runtime)

    // Local DB (Room + KSP for code generation)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Token storage (small key/value, perfect for the AniList OAuth token)
    implementation(libs.androidx.datastore.preferences)

    // JSON for the cached entries blob
    implementation(libs.kotlinx.serialization.json)
}
