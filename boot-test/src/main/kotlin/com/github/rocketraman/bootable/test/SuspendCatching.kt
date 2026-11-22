package com.github.rocketraman.bootable.test

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Like [runCatching], for a block that suspends: it catches what [block] throws, but not the cancellation of the
 * coroutine that calls it, which `runCatching` would swallow, so a test that is cancelled stops, rather than carrying
 * on. A [CancellationException] that isn't the caller's own, such as a `withTimeout` inside [block] timing out, is
 * caught like any other failure.
 */
@Suppress("TooGenericExceptionCaught")
suspend inline fun <T> runSuspendCatching(block: () -> T): Result<T> = try {
  Result.success(block())
} catch (e: Throwable) {
  if (e is CancellationException) currentCoroutineContext().ensureActive()
  Result.failure(e)
}
