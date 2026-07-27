plugins {
    id("asktrix.android.library")
    id("asktrix.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.asktrix.agent.core.data"
}

/*
 * Added to the module graph in docs/adr/0005-core-data-module.md.
 *
 * The original plan had feature modules talk to :core:network and :core:database directly, but
 * :feature:dashboard and :feature:client both need the same offline-first client repository, and
 * duplicating it — or making one feature depend on another — would be worse. This module owns the
 * single-source-of-truth repositories and the domain model.
 */
dependencies {
    api(projects.core.common)
    api(projects.core.database)
    api(projects.core.network)
    // api: features observe ConnectivityObserver and outbox state directly.
    api(projects.core.sync)
    api(projects.core.location)
    api(projects.core.security)
}
