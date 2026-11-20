package com.github.rocketraman.bootable.client.http.ktor

import com.github.rocketraman.bootable.config.Masked
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The application's HTTP clients: a [default] one, and any number of [named] ones, each its own `HttpClient` with
 * its own connection pool. A named client takes every setting it leaves unset from [default], so it only states
 * what differs, such as a base URL, or a client certificate of its own.
 *
 * Decoded from the `http.client` config section:
 *
 * ```
 * http {
 *   client {
 *     default {
 *       connectTimeout = 5s
 *       requestTimeout = 30s
 *       retry { maxRetries = 3 }
 *     }
 *     named {
 *       github {
 *         baseUrl = "https://api.github.com/"
 *         headers { Accept = "application/vnd.github+json" }
 *       }
 *       partner {
 *         requestTimeout = 300s
 *         tls {
 *           keyStore { base64 = ${PARTNER_KEYSTORE}, password = ${PARTNER_KEYSTORE_PASSWORD} }
 *         }
 *       }
 *     }
 *   }
 * }
 * ```
 */
data class KtorClientsConfig(
  /** The default client's settings, and the defaults of every named client. */
  val default: KtorClientConfig = KtorClientConfig(),
  /** The named clients, by name. Each takes what it leaves unset from [default]. */
  val named: Map<String, KtorClientConfig> = emptyMap(),
) {
  /**
   * These settings, with [contributed] named clients added: where the application configures one of them too, its
   * settings win, and the contributed ones fill in what it leaves unset. See [KtorClientDefaults].
   */
  fun withContributed(contributed: Map<String, KtorClientConfig>): KtorClientsConfig = copy(
    named = (named.keys + contributed.keys).associateWith { name ->
      val own = named[name]
      val module = contributed[name]
      when {
        own != null && module != null -> own.orElse(module)
        else -> own ?: module!!
      }
    },
  )

  /** The settings of the client [name], with what it leaves unset taken from [default]. */
  fun resolve(name: String): KtorClientConfig =
    if (name == DEFAULT) default
    else checkNotNull(named[name]) { "No HTTP client named $name; the named clients are ${named.keys}" }.orElse(default)

  companion object {
    /** The name of the [default] client, as a [KtorClientCustomizer] sees it. A named client can't be called this. */
    const val DEFAULT = "default"
  }
}

/**
 * One HTTP client's settings. Every field is optional: unset, a named client takes the default client's setting, and
 * the default client keeps Ktor's, or its engine's.
 *
 * For a Ktor setting not listed here, such as a plugin, use a [KtorClientCustomizer].
 */
data class KtorClientConfig(
  /**
   * The URL relative request URLs resolve against, through Ktor's `DefaultRequest`. End it with a `/` to keep its
   * last path segment: with `https://api.example.com/v1/`, a request for `users` goes to
   * `https://api.example.com/v1/users`, but with `https://api.example.com/v1`, to `https://api.example.com/users`.
   */
  val baseUrl: String? = null,
  /** How long to wait for a connection to be established. */
  val connectTimeout: Duration? = null,
  /** How long a whole request may take, from sending it to receiving the response's body. */
  val requestTimeout: Duration? = null,
  /** The longest the connection may sit idle, waiting for data, in either direction. */
  val socketTimeout: Duration? = null,
  /**
   * Headers sent with every request, unless the request sets its own. A named client's are added to the default
   * client's, and replace any of the same name. Masked, since they often carry a token.
   */
  val headers: Map<String, Masked> = emptyMap(),
  /** Retries of failed requests, through Ktor's `HttpRequestRetry`. Unset, a request is never retried. */
  val retry: KtorClientRetryConfig? = null,
  /**
   * The TLS material: a client certificate to present, for mutual TLS, and the certificates to trust. Unset, the
   * client presents none, and trusts the platform's certificate authorities. A named client takes [tls] whole, so an
   * empty `tls {}` turns off a client certificate the default client has.
   */
  val tls: KtorClientTlsConfig? = null,
) {
  /** These settings, with what they leave unset taken from [defaults]. */
  fun orElse(defaults: KtorClientConfig) = KtorClientConfig(
    baseUrl = baseUrl ?: defaults.baseUrl,
    connectTimeout = connectTimeout ?: defaults.connectTimeout,
    requestTimeout = requestTimeout ?: defaults.requestTimeout,
    socketTimeout = socketTimeout ?: defaults.socketTimeout,
    headers = defaults.headers + headers,
    retry = retry ?: defaults.retry,
    tls = tls ?: defaults.tls,
  )
}

/**
 * Retries of failed requests. A failure is retried only if it is transient (see [isTransient]): a 5xx, 408 or 429
 * response, a timeout, or a failed connection. Any other 4xx is permanent, since the same request would fail the same
 * way again. Only the [methods] are retried, so a request that may have changed something isn't sent twice.
 *
 * The delay between attempts grows exponentially, from [initialDelay], doubling up to [maxDelay], with up to
 * [jitter] added at random. If [respectRetryAfter] is set and the response has a `Retry-After` header, the client
 * waits for the longer of that delay and the header's, which [maxDelay] doesn't cap.
 */
data class KtorClientRetryConfig(
  /** How many times to retry a request, after its first attempt. 0 turns retries off. */
  val maxRetries: Int = 3,
  /** The HTTP methods to retry: by default, the idempotent ones. */
  val methods: Set<String> = setOf("GET", "HEAD", "OPTIONS", "PUT", "DELETE"),
  /** The delay before the first retry. */
  val initialDelay: Duration = 500.milliseconds,
  /** The longest the backoff grows to; a `Retry-After` header can ask for a longer wait (see [respectRetryAfter]). */
  val maxDelay: Duration = 30.seconds,
  /** Up to this much is added to each delay at random, so clients that failed together don't retry together. */
  val jitter: Duration = 500.milliseconds,
  /**
   * Whether to wait at least as long as a response's `Retry-After` header asks, even beyond [maxDelay]. Only a
   * header in seconds is honoured; one with a date is ignored.
   */
  val respectRetryAfter: Boolean = true,
)

/**
 * TLS material: the client certificate to present, for mutual TLS, and the certificates to trust.
 */
data class KtorClientTlsConfig(
  /**
   * The key store holding the private key and certificate chain to present as the client's certificate. Unset, the
   * client presents none.
   */
  val keyStore: KeyStoreConfig? = null,
  /**
   * The alias of the key entry to present, when [keyStore] holds more than one. Unset, the key store's only key
   * entry.
   */
  val keyAlias: String? = null,
  /** The password of the key entry, when it differs from the key store's. */
  val keyPassword: Masked? = null,
  /**
   * The certificates to trust: a server's certificate, or the authority that issued it. Unset, the platform's
   * certificate authorities.
   */
  val trustStore: KeyStoreConfig? = null,
)

/**
 * A key store, given inline as base64, so it can come from an environment variable or a secret, or as a file. Set
 * one of [base64] and [path].
 *
 * A trust store can also be PEM, with [type] `PEM`: one or more certificates, such as a CA bundle, or a platform's
 * mounted `ca.crt`. A key store, which holds a private key, can't.
 */
data class KeyStoreConfig(
  /** The key store's bytes, base64-encoded. */
  val base64: Masked? = null,
  /** The key store's file. */
  val path: String? = null,
  /** The key store's password. */
  val password: Masked? = null,
  /** The key store's format: `PKCS12`, `JKS`, or, for a trust store, `PEM`. Without a password, PEM needs none. */
  val type: String = "PKCS12",
)
