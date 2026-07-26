pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "asktrix-agent"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")

include(":core:common")
include(":core:designsystem")
include(":core:network")
include(":core:database")
include(":core:datastore")
include(":core:security")
include(":core:sync")
include(":core:telephony")
include(":core:location")
include(":core:mdm")

include(":feature:auth")
include(":feature:dashboard")
include(":feature:client")
include(":feature:calls")
include(":feature:attendance")
include(":feature:settings")
