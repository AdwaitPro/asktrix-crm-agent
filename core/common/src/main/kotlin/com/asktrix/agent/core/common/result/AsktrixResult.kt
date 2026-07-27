package com.asktrix.agent.core.common.result

/**
 * The single result type crossing every layer boundary.
 *
 * Exceptions are never used for expected failures - a network timeout, a 401, a validation rejection
 * and an offline device are all ordinary outcomes this app must handle, not exceptional ones. Making
 * them part of the return type means the compiler enforces handling them.
 */
sealed interface AsktrixResult<out T> {

    data class Success<out T>(val data: T) : AsktrixResult<T>

    data class Failure(val error: AsktrixError) : AsktrixResult<Nothing>

    val isSuccess: Boolean get() = this is Success
}

/** Returns the value, or `null` on failure. Use when a failure is genuinely not actionable. */
fun <T> AsktrixResult<T>.getOrNull(): T? = when (this) {
    is AsktrixResult.Success -> data
    is AsktrixResult.Failure -> null
}

/** Returns the value, or [fallback] on failure. */
fun <T> AsktrixResult<T>.getOrDefault(fallback: T): T = getOrNull() ?: fallback

inline fun <T, R> AsktrixResult<T>.map(transform: (T) -> R): AsktrixResult<R> = when (this) {
    is AsktrixResult.Success -> AsktrixResult.Success(transform(data))
    is AsktrixResult.Failure -> this
}

inline fun <T, R> AsktrixResult<T>.flatMap(
    transform: (T) -> AsktrixResult<R>,
): AsktrixResult<R> = when (this) {
    is AsktrixResult.Success -> transform(data)
    is AsktrixResult.Failure -> this
}

inline fun <T> AsktrixResult<T>.onSuccess(action: (T) -> Unit): AsktrixResult<T> {
    if (this is AsktrixResult.Success) action(data)
    return this
}

inline fun <T> AsktrixResult<T>.onFailure(action: (AsktrixError) -> Unit): AsktrixResult<T> {
    if (this is AsktrixResult.Failure) action(error)
    return this
}

fun <T> T.asSuccess(): AsktrixResult<T> = AsktrixResult.Success(this)

fun AsktrixError.asFailure(): AsktrixResult<Nothing> = AsktrixResult.Failure(this)
