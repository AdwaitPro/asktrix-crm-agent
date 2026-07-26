plugins {
    id("asktrix.android.feature")
}

android {
    namespace = "com.asktrix.agent.feature.calls"
}

dependencies {
    implementation(projects.core.network)
    implementation(projects.core.database)
    implementation(projects.core.telephony)
}
