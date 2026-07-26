plugins {
    id("asktrix.android.feature")
}

android {
    namespace = "com.asktrix.agent.feature.settings"
}

dependencies {
    implementation(projects.core.datastore)
    implementation(projects.core.mdm)
}
