plugins {
    id("asktrix.android.library")
    id("asktrix.android.hilt")
}

android {
    namespace = "com.asktrix.agent.core.common"
}

dependencies {
    implementation(libs.androidx.core.ktx)
}
