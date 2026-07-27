plugins {
    id("asktrix.android.feature")
}

android {
    namespace = "com.asktrix.agent.feature.client"
}

dependencies {
    implementation(projects.core.data)
    implementation(projects.core.telephony)
}

dependencies {
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.junit4)
}
