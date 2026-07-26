plugins {
    id("asktrix.android.feature")
}

android {
    namespace = "com.asktrix.agent.feature.auth"
}

dependencies {
    implementation(projects.core.security)
    implementation(projects.core.datastore)
    implementation(projects.core.network)
    implementation(libs.androidx.biometric)
}
