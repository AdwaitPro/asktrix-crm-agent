plugins {
    id("asktrix.android.feature")
}

android {
    namespace = "com.asktrix.agent.feature.settings"
}

dependencies {
    implementation(projects.core.data)
    implementation(projects.core.datastore)
    implementation(projects.core.mdm)
    implementation(projects.core.security)
}
