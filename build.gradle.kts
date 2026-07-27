plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.detekt)
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    source.setFrom(
        files(
            "app/src",
            "core/common/src",
            "core/data/src",
            "core/designsystem/src",
            "core/network/src",
            "core/database/src",
            "core/datastore/src",
            "core/security/src",
            "core/sync/src",
            "core/telephony/src",
            "core/location/src",
            "core/mdm/src",
            "feature/auth/src",
            "feature/dashboard/src",
            "feature/client/src",
            "feature/calls/src",
            "feature/attendance/src",
            "feature/settings/src",
        ),
    )
}

// Deletes every module's build directory, not just the root's. The single-directory version is a
// common and misleading bug: `clean assembleDebug` appears to pass in seconds while actually
// reusing stale module outputs.
tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
    subprojects.forEach { delete(it.layout.buildDirectory) }
}
