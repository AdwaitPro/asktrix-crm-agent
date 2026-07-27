plugins {
    id("asktrix.android.feature")
}

android {
    namespace = "com.asktrix.agent.feature.calls"
}

dependencies {
    implementation(projects.core.data)
    implementation(projects.core.telephony)
}
