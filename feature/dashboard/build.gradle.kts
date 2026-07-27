plugins {
    id("asktrix.android.feature")
}

android {
    namespace = "com.asktrix.agent.feature.dashboard"
}

dependencies {
    implementation(projects.core.data)
}
