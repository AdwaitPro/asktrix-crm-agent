plugins {
    id("asktrix.android.library")
    id("asktrix.android.hilt")
}

android {
    namespace = "com.asktrix.agent.core.sync"
}

dependencies {
    api(projects.core.common)
    api(projects.core.database)
    api(projects.core.network)

    api(libs.androidx.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.ext.compiler)

    testImplementation(libs.androidx.work.testing)
}
