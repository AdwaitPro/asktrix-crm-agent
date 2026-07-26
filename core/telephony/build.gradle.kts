plugins {
    id("asktrix.android.library")
    id("asktrix.android.hilt")
}

android {
    namespace = "com.asktrix.agent.core.telephony"
}

// Per docs/adr/0002-telephony-architecture.md this module holds NO audio capture and NO recording
// pipeline. Calls are bridged by the CPaaS server-side; the device only requests and observes them.
dependencies {
    implementation(projects.core.common)
    implementation(projects.core.network)
}
