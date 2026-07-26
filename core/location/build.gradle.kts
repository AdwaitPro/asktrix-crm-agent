plugins {
    id("asktrix.android.library")
    id("asktrix.android.hilt")
}

android {
    namespace = "com.asktrix.agent.core.location"
}

dependencies {
    implementation(projects.core.common)
    implementation(libs.androidx.core.ktx)
    api(libs.play.services.location)
    implementation(libs.androidx.work.runtime.ktx)
}
