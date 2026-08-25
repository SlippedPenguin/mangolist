import com.apollographql.apollo.gradle.api.ApolloExtension
import java.util.Properties

/*
 * AniList OAuth credentials are read from `local.properties` (gitignored) so
 * secrets never leak. Add to your local.properties:
 *   anilist.client.id=<your-client-id-from-anilist-co-api-v2-oauth>
 *   anilist.client.secret=<your-client-secret>
 *   anilist.redirect.uri=com.slippedpenguin.mangolist://callback
 *
 * If unset, the build still succeeds — the URLs it generates just won't work
 * until you register the app at https://anilist.co/api/v2/oauth.
 */
val anilistLocalProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

/*
 * Release signing credentials. Read from `keystore.properties` (gitignored)
 * or environment variables — whichever is present — so the same script works
 * locally and in CI. Add to keystore.properties:
 *   MANGOLIST_STORE_FILE=release.keystore
 *   MANGOLIST_STORE_PASSWORD=...
 *   MANGOLIST_KEY_ALIAS=mangolist
 *   MANGOLIST_KEY_PASSWORD=...
 * If none are set, release builds fall back to the debug key so a fresh clone
 * can always produce an installable sideload APK.
 */
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(key: String): String? =
    System.getenv(key)?.takeIf { it.isNotBlank() } ?: keystoreProps.getProperty(key)
val hasReleaseKeystore = signingValue("MANGOLIST_STORE_FILE") != null

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
        // Overridable from the CLI so release.yml can derive both values from
        // the pushed tag:  gradle assembleRelease -PversionCode=N -PversionName=X.Y.Z
        versionCode = (findProperty("versionCode") as String?)?.toInt() ?: 38
        versionName = (findProperty("versionName") as String?) ?: "1.5.8"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        // AniList OAuth — values flow from local.properties into BuildConfig so
        // the rest of the code can reference BuildConfig.ANILIST_CLIENT_ID and
        // BuildConfig.ANILIST_REDIRECT_URI without runtime secrets wiring. The
        // .trim().trim('"') chain tolerates a user quoting the value in
        // local.properties — a typical IntelliJ / Android Studio pass.
        buildConfigField(
            "String",
            "ANILIST_CLIENT_ID",
            "\"${anilistLocalProps.getProperty("anilist.client.id", "").trim().trim('"')}\"",
        )
        buildConfigField(
            "String",
            "ANILIST_CLIENT_SECRET",
            "\"${anilistLocalProps.getProperty("anilist.client.secret", "").trim().trim('"')}\"",
        )
        buildConfigField(
            "String",
            "ANILIST_REDIRECT_URI",
            "\"${anilistLocalProps.getProperty("anilist.redirect.uri", "com.slippedpenguin.mangolist").trim().trim('"')}\"",
        )
    }

    signingConfigs {
        create("release") {
            // Only populated when real keystore credentials exist; otherwise
            // left unset and the release buildType falls back to debug below.
            if (hasReleaseKeystore) {
                storeFile = rootProject.file(signingValue("MANGOLIST_STORE_FILE")!!)
                storePassword = signingValue("MANGOLIST_STORE_PASSWORD")
                keyAlias = signingValue("MANGOLIST_KEY_ALIAS")
                keyPassword = signingValue("MANGOLIST_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Real keystore when configured (CI / local keystore.properties);
            // debug-signed fallback keeps first-clone builds installable.
            signingConfig = signingConfigs.getByName(
                if (hasReleaseKeystore) "release" else "debug"
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Apollo Kotlin 4.x:
//   * `queries.graphql` defines the operations (lives next to this directory).
//   * `schema.graphqls` is auto-fetched from AniList at build time by the
//     `downloadAnilistApolloSchemaFromIntrospection` Gradle task (invoked from
//     .github/workflows/release.yml BEFORE assembleRelease). Apollo then
//     generates Kotlin types under com.slippedpenguin.mangolist.graphql that
//     the screens consume via the AniListClient wrapper.
apollo {
    service("anilist") {
        packageName.set("com.slippedpenguin.mangolist.graphql")
        generateKotlinModels.set(true)
        // Tell Apollo Kotlin 4.x which file codegen should READ as the schema.
        // The download task below writes to this same path; without explicit
        // registration here, codegen errors with "No schema found" because
        // Apollo validates at config / task-execution time BEFORE the
        // download task has had a chance to populate the file.
        schemaFiles.from(files("$projectDir/src/main/graphql/schema.graphqls"))
        introspection {
            endpointUrl.set("https://graphql.anilist.co")
            schemaFile.set(file("$projectDir/src/main/graphql/schema.graphqls"))
        }
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

    // Chrome Custom Tabs — opens the AniList OAuth authorize URL in the
    // user's existing browser, and lets our MainActivity catch the redirect
    // via the deep-link intent-filter (configured in a follow-up commit).
    implementation(libs.androidx.browser)

    // Background work — auto-push dirty local edits to AniList
    implementation(libs.androidx.work.runtime.ktx)

    // Local JVM unit tests (EloEngine, sync merge logic, score mapping)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

// Wire Apollo codegen to depend on the introspection download so codegen
// always sees a fresh AniList schema. Without this, codegen runs against a
// stale or absent schema.graphqls because Apollo's download task is
// independent of the assemble graph by default.
afterEvaluate {
    tasks.named("generateAnilistApolloSources").configure {
        dependsOn("downloadAnilistApolloSchemaFromIntrospection")
    }
}
