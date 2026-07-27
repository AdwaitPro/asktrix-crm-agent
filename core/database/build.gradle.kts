plugins {
    id("asktrix.android.library")
    id("asktrix.android.hilt")
}

android {
    namespace = "com.asktrix.agent.core.database"
}

// Export the Room schema so migrations can be diffed and migration tests can run against it.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    api(projects.core.common)
    implementation(projects.core.security)

    api(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // SQLCipher: the local cache is encrypted at rest (§3, §23).
    implementation(libs.sqlcipher)

    testImplementation(libs.androidx.room.testing)
}
