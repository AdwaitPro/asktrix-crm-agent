plugins {
    id("asktrix.android.library")
    id("asktrix.android.hilt")
}

android {
    namespace = "com.asktrix.agent.core.security"
}

dependencies {
    implementation(projects.core.common)
    implementation(libs.androidx.core.ktx)

    // Play Integrity verdicts are verified server-side; the client only requests the token.
    implementation(libs.play.integrity)
}
