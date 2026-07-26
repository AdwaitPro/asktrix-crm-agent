plugins {
    id("asktrix.android.compose")
    id("asktrix.android.hilt")
}

dependencies {
    "implementation"(project(":core:common"))
    "implementation"(project(":core:designsystem"))

    "implementation"(lib("androidx-core-ktx"))
    "implementation"(lib("androidx-lifecycle-runtime-ktx"))
    "implementation"(lib("androidx-navigation-compose"))
    "implementation"(lib("hilt-navigation-compose"))
}
