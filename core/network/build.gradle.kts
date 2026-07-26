plugins {
    id("asktrix.android.library")
    id("asktrix.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.asktrix.agent.core.network"
}

dependencies {
    implementation(projects.core.common)

    api(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    api(libs.okhttp)
    implementation(libs.okhttp.logging)
    api(libs.kotlinx.serialization.json)

    testImplementation(libs.okhttp.mockwebserver)
}
