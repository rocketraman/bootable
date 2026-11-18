package com.github.rocketraman.bootable.telemetry.otel

import io.opentelemetry.common.ComponentLoader
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.common.export.HttpSender
import io.opentelemetry.sdk.common.export.HttpSenderConfig
import io.opentelemetry.sdk.common.export.HttpSenderProvider
import io.opentelemetry.sdk.common.export.HttpResponse
import io.opentelemetry.sdk.common.export.MessageWriter
import java.util.function.Consumer

/**
 * Loads components as [delegate] does, but for the OTLP exporters' [HttpSenderProvider]: the one the exporters would
 * choose, as a [LazyHttpSenderProvider].
 *
 * The exporter builders build their HTTP sender with the exporter, and the JDK's sender builds an `HttpClient`, which
 * sets up TLS, reading and parsing the JDK's trust store, even for an `http` endpoint: about 20 ms of the application's
 * start, on its main thread. With this loader, the sender is built on the first export instead, on the span
 * processor's or the metric reader's own thread.
 */
internal class LazySenderComponentLoader(private val delegate: ComponentLoader) : ComponentLoader {
  override fun <T> load(spiClass: Class<T>): Iterable<T> {
    if (spiClass != HttpSenderProvider::class.java) return delegate.load(spiClass)
    // the exporters' own choice: the only provider, or the one configured by class name, or else the first
    val providers = delegate.load(HttpSenderProvider::class.java).toList()
    val configured = configuredSenderProvider()
    val provider = when {
      providers.size <= 1 || configured == null -> providers.firstOrNull()
      else -> providers.firstOrNull { it.javaClass.name == configured }
    }
    // with none, or none that matches, the exporter reports it as it would without this loader
    return if (provider == null) delegate.load(spiClass) else listOf(spiClass.cast(LazyHttpSenderProvider(provider)))
  }

  override fun toString() = "LazySenderComponentLoader{$delegate}"

  private companion object {
    const val SENDER_PROVIDER_PROPERTY = "io.opentelemetry.sdk.common.export.HttpSenderProvider"

    fun configuredSenderProvider(): String? =
      (System.getProperty(SENDER_PROVIDER_PROPERTY)
        ?: System.getenv(SENDER_PROVIDER_PROPERTY.uppercase().replace('.', '_')))?.takeIf { it.isNotBlank() }
  }
}

/** Creates [LazyHttpSender]s, of the senders that [delegate] creates. */
internal class LazyHttpSenderProvider(private val delegate: HttpSenderProvider) : HttpSenderProvider {
  override fun createSender(httpSenderConfig: HttpSenderConfig): HttpSender =
    LazyHttpSender { delegate.createSender(httpSenderConfig) }
}

/** An [HttpSender] that creates its [delegate][create] on its first send, so one that never sends is never created. */
internal class LazyHttpSender(create: () -> HttpSender) : HttpSender {
  private val delegate = lazy(create)

  override fun send(messageWriter: MessageWriter, onResponse: Consumer<HttpResponse>, onError: Consumer<Throwable>) {
    delegate.value.send(messageWriter, onResponse, onError)
  }

  override fun shutdown(): CompletableResultCode =
    if (delegate.isInitialized()) delegate.value.shutdown() else CompletableResultCode.ofSuccess()
}
