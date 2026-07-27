plugins {
    id("asktrix.android.compose")
}

android {
    namespace = "com.asktrix.agent.core.designsystem"
}

dependencies {
    api(projects.core.common)
    implementation(libs.androidx.core.ktx)
    // api, not implementation: feature modules build their UI from these icons.
    api(libs.androidx.compose.material.icons.extended)
}
