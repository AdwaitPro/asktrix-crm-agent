import java.util.Properties

plugins {
    id("asktrix.android.application")
    id("asktrix.android.hilt")
}

/*
 * FCM (§24) is optional at build time.
 *
 * google-services.json is gitignored, so a fresh clone or a CI runner without it must still build.
 * The plugin is applied only when the file exists, and BuildConfig.FCM_ENABLED tells the app whether
 * to register a push token or fall back to sync-on-open.
 */
val googleServicesConfig = file("google-services.json")
val fcmEnabled = googleServicesConfig.exists()
if (fcmEnabled) {
    apply(plugin = "com.google.gms.google-services")
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

    /*
     * Release signing reads from local.properties or CI secrets and is never committed.
     *
     * When the keystore is absent the signing config is simply not created, so a release build
     * fails loudly at signing rather than silently producing a debug-signed APK that looks
     * shippable. That failure mode matters: a debug-signed release is the kind of thing that
     * reaches a fleet before anyone notices.
     */
    val keystorePath = secret("ASKTRIX_KEYSTORE_PATH", "")
    val hasKeystore = keystorePath.isNotBlank() && file(keystorePath).exists()

    signingConfigs {
        if (hasKeystore) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = secret("ASKTRIX_KEYSTORE_PASSWORD", "")
                keyAlias = secret("ASKTRIX_KEY_ALIAS", "")
                keyPassword = secret("ASKTRIX_KEY_PASSWORD", "")
            }
        }
    }

    defaultConfig {
        applicationId = "com.asktrix.agent"
        versionCode = 3
        versionName = "0.1.3"
    }

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        buildConfigField("boolean", "FCM_ENABLED", fcmEnabled.toString())
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
            if (hasKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
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

    if (fcmEnabled) {
        implementation(platform(libs.firebase.bom))
        implementation(libs.firebase.messaging)
    }

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
