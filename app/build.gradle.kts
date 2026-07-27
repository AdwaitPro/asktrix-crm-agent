import java.util.Properties

plugins {
    id("asktrix.android.application")
    id("asktrix.android.hilt")
}

/**
 * Reads a build secret from `local.properties` (gitignored) with an environment-variable fallback for
 * CI. Never hardcode a value here — see docs/adr/0001-sdk-levels.md and CLAUDE.md.
 */
fun secret(key: String, default: String): String {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        val props = Properties().apply { file.inputStream().use { load(it) } }
        props.getProperty(key)?.takeIf { it.isNotBlank() }?.let { return it }
    }
    return System.getenv(key)?.takeIf { it.isNotBlank() } ?: default
}

android {
    namespace = "com.asktrix.agent"

    defaultConfig {
        applicationId = "com.asktrix.agent"
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        buildConfig = true
    }

    // Three variants, each with its own CRM base URL. The dev default points at the local mock
    // server so the app is fully runnable with no CRM and no paid services (see
    // docs/ZERO_COST_SETUP.md). 10.0.2.2 is the host machine as seen from the Android emulator.
    buildTypes {
        debug {
            applicationIdSuffix = ".dev"
            isMinifyEnabled = false
            buildConfigField(
                "String",
                "CRM_BASE_URL",
                "\"${secret("CRM_BASE_URL_DEV", "http://10.0.2.2:4010/")}\"",
            )
            buildConfigField("boolean", "USE_MOCK_TELEPHONY", "true")
            // FLAG_SECURE blocks screenshots (§14-§20), which also blocks QA capture and
            // Compose screenshot tests. Debug builds may opt out by setting
            // `asktrix.allowScreenshots=true` in local.properties. It defaults to FALSE, so a
            // debug build is locked down unless a developer deliberately unlocks it, and the
            // release branch below has no such switch at all.
            buildConfigField(
                "boolean",
                "ALLOW_SCREENSHOTS",
                secret("asktrix.allowScreenshots", "false"),
            )
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            buildConfigField(
                "String",
                "CRM_BASE_URL",
                "\"${secret("CRM_BASE_URL_PROD", "https://crm.asktrix.invalid/")}\"",
            )
            buildConfigField("boolean", "USE_MOCK_TELEPHONY", "false")
            // Never configurable in release. Screenshot blocking is a requirement, not a preference.
            buildConfigField("boolean", "ALLOW_SCREENSHOTS", "false")
        }
    }
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.designsystem)
    implementation(projects.core.network)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(projects.core.security)
    implementation(projects.core.sync)
    implementation(projects.core.telephony)
    implementation(projects.core.location)
    implementation(projects.core.mdm)

    implementation(projects.feature.auth)
    implementation(projects.feature.dashboard)
    implementation(projects.feature.client)
    implementation(projects.feature.calls)
    implementation(projects.feature.attendance)
    implementation(projects.feature.settings)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.ext.compiler)
}
