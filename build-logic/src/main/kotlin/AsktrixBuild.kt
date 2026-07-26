import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider

/** Values shared by every module. A bump happens here, once. */
object AsktrixBuild {
    const val COMPILE_SDK = 37
    const val TARGET_SDK = 36
    const val MIN_SDK = 29
}

/**
 * Precompiled script plugins cannot use the generated `libs` accessor, so the version catalog is
 * resolved explicitly. [lib] throws with the offending alias rather than a bare
 * `NoSuchElementException`, which turns a typo into a one-line diagnosis.
 */
internal val Project.asktrixLibs: VersionCatalog
    get() = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")

internal fun Project.lib(alias: String): Provider<MinimalExternalModuleDependency> =
    asktrixLibs.findLibrary(alias).orElseThrow {
        IllegalStateException("Version catalog 'libs' has no library alias '$alias'")
    }
