plugins {
    id("asktrix.android.library")
    id("asktrix.android.hilt")
}

android {
    namespace = "com.asktrix.agent.core.mdm"
}

// Per docs/adr/0004-device-management.md this app is NOT the Device Policy Controller. This module
// consumes EMM managed configurations and reports compliance. It contains no DeviceAdminReceiver and
// no DevicePolicyManager owner calls.
dependencies {
    implementation(projects.core.common)
    implementation(libs.androidx.core.ktx)
}
