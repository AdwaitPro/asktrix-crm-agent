plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    compileSdk = AsktrixBuild.COMPILE_SDK

    defaultConfig {
        minSdk = AsktrixBuild.MIN_SDK
        targetSdk = AsktrixBuild.TARGET_SDK
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/LICENSE*",
            "/META-INF/NOTICE*",
        )
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

tasks.withType<Test>().configureEach {
    // Gradle 9 fails a test task that discovers nothing. Modules still being built out legitimately
    // have no tests yet, and that must not mask a real failure elsewhere in the gate. Coverage
    // thresholds — not this flag — are what enforce that shipped code is tested.
    failOnNoDiscoveredTests.set(false)

    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

dependencies {
    val bom = lib("androidx-compose-bom")
    "implementation"(platform(bom))
    "androidTestImplementation"(platform(bom))

    "implementation"(lib("androidx-compose-ui"))
    "implementation"(lib("androidx-compose-ui-graphics"))
    "implementation"(lib("androidx-compose-ui-tooling-preview"))
    "implementation"(lib("androidx-compose-material3"))
    "implementation"(lib("androidx-lifecycle-runtime-compose"))
    "implementation"(lib("androidx-lifecycle-viewmodel-compose"))
    "implementation"(lib("kotlinx-coroutines-android"))

    "debugImplementation"(lib("androidx-compose-ui-tooling"))
    "debugImplementation"(lib("androidx-compose-ui-test-manifest"))

    "testImplementation"(lib("junit4"))
    "testImplementation"(lib("mockk"))
    "testImplementation"(lib("turbine"))
    "testImplementation"(lib("kotlinx-coroutines-test"))
    "androidTestImplementation"(lib("androidx-compose-ui-test-junit4"))
}
