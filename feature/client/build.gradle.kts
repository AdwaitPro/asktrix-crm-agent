plugins {
    id("asktrix.android.feature")
}

android {
    namespace = "com.asktrix.agent.feature.client"
}

dependencies {
    implementation(projects.core.network)
    implementation(projects.core.database)
    implementation(projects.core.sync)
    implementation(projects.core.telephony)
}
