import java.util.Properties

// Read local.properties for secrets — never commit that file
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) load(file.inputStream())
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

android {
    namespace   = "com.familyhome.app"
    compileSdk  = 35

    defaultConfig {
        applicationId           = "com.familyhome.app"
        minSdk                  = 26
        targetSdk               = 35
        versionCode             = 1
        versionName             = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Anthropic API key injected at build time from local.properties
        buildConfigField(
            "String",
            "ANTHROPIC_API_KEY",
            "\"${localProperties.getProperty("ANTHROPIC_API_KEY", "")}\""
        )

        // Sync server default port
        buildConfigField("int", "SYNC_SERVER_PORT", "8765")
    }

    buildFeatures {
        compose     = true
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
            // Ktor bundles some duplicate service files
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/io.netty.versions.properties"
        }
    }
}

dependencies {
    // ---------- AndroidX core ----------
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    // ---------- Compose ----------
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // ---------- Hilt ----------
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // ---------- Room ----------
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // ---------- DataStore ----------
    implementation(libs.datastore.preferences)

    // ---------- Ktor server (sync host) ----------
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.content.negotiation)

    // ---------- Ktor client (sync client) ----------
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.logging)

    // ---------- Shared Ktor serialization ----------
    implementation(libs.ktor.serialization.kotlinx.json)

    // ---------- Retrofit (Claude API) ----------
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp.logging)

    // ---------- Serialization ----------
    implementation(libs.kotlinx.serialization.json)

    // ---------- Coroutines ----------
    implementation(libs.kotlinx.coroutines.android)

    // ---------- Coil ----------
    implementation(libs.coil.compose)

    // ---------- Tests ----------
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
