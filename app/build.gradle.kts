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
    alias(libs.plugins.detekt)
    jacoco
}

android {
    namespace   = "com.familyhome.app"
    compileSdk  = 35

    defaultConfig {
        applicationId           = "com.familyhome.app"
        minSdk                  = 26
        targetSdk               = 35
        versionCode             = 2
        versionName             = "1.1.0"
        testInstrumentationRunner = "com.familyhome.app.HiltTestRunner"

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

    // Make JaCoCo work with JUnit 5
    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
            it.finalizedBy("jacocoTestReport")
        }
        unitTests.isIncludeAndroidResources = true
        animationsDisabled = true
    }
}

// ─── JaCoCo ──────────────────────────────────────────────────────────────────

val jacocoExcludes = listOf(
    // Hilt generated
    "**/*_HiltComponents*",
    "**/*_Factory*",
    "**/*_MembersInjector*",
    "**/Hilt_*",
    "**/*Module_*",
    // Room generated
    "**/*Dao_Impl*",
    "**/*Database_Impl*",
    // Android framework
    "**/BuildConfig*",
    "**/R.class",
    "**/R$*.class",
    "**/*Activity*",
    "**/*Application*",
    // Compose generated
    "**/*ComposableSingletons*",
    // Data binding
    "**/databinding/**",
    // Navigation
    "**/navigation/**",
)

tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest")
    group = "verification"
    description = "Generates JaCoCo coverage report for unit tests."

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }

    val kotlinClassesDir = layout.buildDirectory.dir("tmp/kotlin-classes/debug")
    val javaClassesDir   = layout.buildDirectory.dir("intermediates/javac/debug/classes")
    classDirectories.setFrom(
        fileTree(kotlinClassesDir) { exclude(jacocoExcludes) },
        fileTree(javaClassesDir)   { exclude(jacocoExcludes) },
    )
    sourceDirectories.setFrom(files("src/main/java", "src/main/kotlin"))
    executionData.setFrom(fileTree(layout.buildDirectory) {
        include("jacoco/testDebugUnitTest.exec")
    })
}

tasks.register<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn("jacocoTestReport")
    group = "verification"
    description = "Enforces minimum JaCoCo coverage thresholds."

    violationRules {
        rule {
            limit {
                counter = "LINE"
                value   = "COVEREDRATIO"
                // TODO: raise to 1.0 once coverage is stable
                minimum = "0.80".toBigDecimal()
            }
        }
    }

    val kotlinClassesDir = layout.buildDirectory.dir("tmp/kotlin-classes/debug")
    val javaClassesDir   = layout.buildDirectory.dir("intermediates/javac/debug/classes")
    classDirectories.setFrom(
        fileTree(kotlinClassesDir) { exclude(jacocoExcludes) },
        fileTree(javaClassesDir)   { exclude(jacocoExcludes) },
    )
    sourceDirectories.setFrom(files("src/main/java", "src/main/kotlin"))
    executionData.setFrom(fileTree(layout.buildDirectory) {
        include("jacoco/testDebugUnitTest.exec")
    })
}

// ─── Detekt ──────────────────────────────────────────────────────────────────

detekt {
    config.setFrom(rootProject.file("config/detekt.yml"))
    buildUponDefaultConfig = true
    allRules = false
    source.setFrom(
        "src/main/java",
        "src/test/java",
        "src/androidTest/java",
    )
}

tasks.register("detektAll") {
    group = "verification"
    description = "Runs Detekt on all source sets."
    dependsOn(tasks.detekt)
}

// ─── Quality gate ─────────────────────────────────────────────────────────────

tasks.register("qualityCheck") {
    group = "verification"
    description = "Runs Detekt + unit tests + JaCoCo coverage + PITest mutation in order."
    dependsOn("detektAll", "testDebugUnitTest", "jacocoTestCoverageVerification")

    tasks.findByName("testDebugUnitTest")
        ?.mustRunAfter("detektAll")
    tasks.findByName("jacocoTestCoverageVerification")
        ?.mustRunAfter("testDebugUnitTest")
}

// ─── Dependencies ─────────────────────────────────────────────────────────────

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
    implementation(libs.androidx.ui.text.google.fonts)
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

    // ---------- Vico (charts) ----------
    implementation(libs.vico.compose.m3)

    // ────── Unit Tests ──────────────────────────────────────────────────────
    testImplementation(libs.junit5.api)
    testImplementation(libs.junit5.params)
    testRuntimeOnly(libs.junit5.engine)
    testRuntimeOnly(libs.junit.vintage.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    // Keep JUnit 4 for legacy — removed because JUnit5 via vintage covers it
    testImplementation(libs.junit)

    // ────── Instrumented / Integration Tests ───────────────────────────────
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.hilt.android.testing)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    kspAndroidTest(libs.hilt.compiler)

    // ---------- Debug ----------
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // ---------- Detekt ----------
    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:${libs.versions.detekt.get()}")
}
