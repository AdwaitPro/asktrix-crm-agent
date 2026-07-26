plugins {
    id("asktrix.android.feature")
}

android {
    namespace = "com.asktrix.agent.feature.attendance"
}

dependencies {
    implementation(projects.core.network)
    implementation(projects.core.database)
    implementation(projects.core.sync)
    implementation(projects.core.location)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.coil.compose)
}
