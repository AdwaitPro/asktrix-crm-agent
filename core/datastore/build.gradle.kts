plugins {
    id("asktrix.android.library")
    id("asktrix.android.hilt")
}

android {
    namespace = "com.asktrix.agent.core.datastore"
}

dependencies {
    api(projects.core.common)
    implementation(projects.core.security)
    api(libs.androidx.datastore.preferences)
}
