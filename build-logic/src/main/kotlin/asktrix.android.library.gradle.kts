plugins {
    id("com.android.library")
}

android {
    compileSdk = AsktrixBuild.COMPILE_SDK

    defaultConfig {
        minSdk = AsktrixBuild.MIN_SDK
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
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
    "implementation"(lib("kotlinx-coroutines-android"))
    "testImplementation"(lib("junit4"))
    "testImplementation"(lib("mockk"))
    "testImplementation"(lib("turbine"))
    "testImplementation"(lib("kotlinx-coroutines-test"))
}
