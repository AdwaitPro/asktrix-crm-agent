plugins {
    id("asktrix.android.feature")
}

android {
    namespace = "com.asktrix.agent.feature.attendance"
}

dependencies {
    implementation(projects.core.data)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.coil.compose)
}
