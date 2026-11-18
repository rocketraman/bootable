package com.github.rocketraman.bootable.client.http.ktor

import io.ktor.client.plugins.*
import io.ktor.http.*
import java.io.IOException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Whether a response with this status is a transient failure, which the same request may get past if it is sent
 * again: a 5xx, since the server failed, a 408 (Request Timeout), or a 429 (Too Many Requests), which mean "not now"
 * rather than "not ever". Any other 4xx is permanent: the request is wrong, and sending it unchanged can't fix it.
 * Not a failure at all, a 1xx, 2xx or 3xx isn't transient either.
 */
fun HttpStatusCode.isTransient(): Boolean =
  value >= 500 || this == HttpStatusCode.RequestTimeout || this == HttpStatusCode.TooManyRequests

/**
 * Whether a request that failed with this exception, rather than with a response, may succeed if it is sent again:
 * a timeout, or an I/O failure such as a refused or reset connection. A failed TLS handshake isn't: an untrusted
 * certificate stays untrusted. Nor is a cancellation, or any other exception, which is likely a bug.
 */
fun Throwable.isTransient(): Boolean = when (this) {
  // Ktor's request timeout is a CancellationException in some engines, but one worth retrying
  is HttpRequestTimeoutException -> true
  is CancellationException -> false
  is SSLHandshakeException, is SSLPeerUnverifiedException -> false
  is IOException -> true
  else -> false
}

/** Retries this client's transient failures, as [retry] says: see [KtorClientRetryConfig]. */
fun HttpRequestRetryConfig.retry(retry: KtorClientRetryConfig) {
  val methods = retry.methods.map { HttpMethod.parse(it.uppercase()) }.toSet()
  retryIf(retry.maxRetries) { request, response -> request.method in methods && response.status.isTransient() }
  retryOnExceptionIf(retry.maxRetries) { request, cause -> request.method in methods && cause.isTransient() }
  exponentialDelay(
    base = 2.0,
    baseDelayMs = retry.initialDelay.inWholeMilliseconds,
    maxDelayMs = retry.maxDelay.inWholeMilliseconds,
    randomizationMs = retry.jitter.inWholeMilliseconds,
    respectRetryAfterHeader = retry.respectRetryAfter,
  )
}
