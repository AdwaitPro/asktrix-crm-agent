plugins {
    `kotlin-dsl`
}

group = "com.asktrix.buildlogic"

// Matches the JVM Gradle itself runs on, keeping compileJava and compileKotlin consistent.
java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

// Precompiled script plugins (the `*.gradle.kts` files in src/main/kotlin) are used instead of
// `Plugin<Project>` classes deliberately: AGP 9 removed the type parameters from CommonExtension and
// relocated parts of the DSL, so the typed-extension approach breaks. Precompiled scripts use the
// same generated accessors as a normal module build file, which are stable across that change.
dependencies {
    implementation(libs.android.gradle.plugin)
    implementation(libs.compose.compiler.gradle.plugin)
    implementation(libs.ksp.gradle.plugin)
    implementation(libs.hilt.gradle.plugin)
}
