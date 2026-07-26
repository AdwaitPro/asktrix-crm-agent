plugins {
    id("asktrix.android.library")
    id("asktrix.android.hilt")
}

android {
    namespace = "com.asktrix.agent.core.datastore"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.security)
    api(libs.androidx.datastore.preferences)
}
