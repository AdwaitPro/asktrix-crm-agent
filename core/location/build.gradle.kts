plugins {
    id("asktrix.android.library")
    id("asktrix.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.asktrix.agent.core.location"
}

dependencies {
    api(projects.core.common)
    implementation(projects.core.sync)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)
    api(libs.play.services.location)
    implementation(libs.androidx.work.runtime.ktx)
}
