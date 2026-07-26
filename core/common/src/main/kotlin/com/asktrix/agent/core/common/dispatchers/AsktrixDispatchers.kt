package com.asktrix.agent.core.common.dispatchers

import javax.inject.Qualifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

/**
 * Dispatchers are injected, never referenced as `Dispatchers.IO` inside a class. That is what makes
 * every suspending function in this codebase testable with a deterministic scheduler, and it is
 * enforced by Detekt's `InjectDispatcher` rule.
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class Dispatcher(val kind: AsktrixDispatcher)

enum class AsktrixDispatcher {
    /** Disk and network work. */
    IO,

    /** CPU-bound work: crypto, parsing, mapping. */
    DEFAULT,

    /** Main thread. UI only. */
    MAIN,
}

/** An application-lifetime scope, for work that must outlive any single screen. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope

interface DispatcherProvider {
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
    val main: CoroutineDispatcher
}

/** Convenience for classes that need the app scope and its dispatchers together. */
data class AsktrixScope(
    val scope: CoroutineScope,
    val dispatchers: DispatcherProvider,
)
