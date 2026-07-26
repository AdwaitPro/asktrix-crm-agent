plugins {
    id("asktrix.android.compose")
}

android {
    namespace = "com.asktrix.agent.core.designsystem"
}

dependencies {
    implementation(projects.core.common)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.compose.material.icons.extended)
}
