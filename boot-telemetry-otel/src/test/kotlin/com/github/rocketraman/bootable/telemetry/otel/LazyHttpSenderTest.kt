package com.github.rocketraman.bootable.telemetry.otel

import de.infix.testBalloon.framework.core.testSuite
import io.opentelemetry.common.ComponentLoader
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.common.export.HttpSender
import io.opentelemetry.sdk.common.export.HttpResponse
import io.opentelemetry.sdk.common.export.HttpSenderProvider
import io.opentelemetry.sdk.common.export.MessageWriter
import java.io.OutputStream
import java.util.function.Consumer

private class CountingSender : HttpSender {
  var sends = 0
  var shutdowns = 0

  override fun send(
    messageWriter: MessageWriter,
    onResponse: Consumer<HttpResponse>,
    onError: Consumer<Throwable>,
  ) {
    sends++
  }

  override fun shutdown(): CompletableResultCode = CompletableResultCode.ofSuccess().also { shutdowns++ }
}

private val emptyMessage = object : MessageWriter {
  override fun writeMessage(output: OutputStream) = Unit
  override fun getContentLength() = 0
}

val LazyHttpSenderTest by testSuite {
  test("creates its sender on the first send, and only once") {
    var created = 0
    val delegate = CountingSender()
    val sender = LazyHttpSender { created++; delegate }
    assert(created == 0)

    repeat(3) { sender.send(emptyMessage, {}, {}) }
    assert(created == 1)
    assert(delegate.sends == 3)

    sender.shutdown()
    assert(delegate.shutdowns == 1)
  }

  test("shuts down without creating a sender that never sent") {
    var created = 0
    val sender = LazyHttpSender { created++; CountingSender() }
    assert(sender.shutdown().isSuccess)
    assert(created == 0)
  }

  test("loads the sender provider that the exporters would resolve, lazily, and other components as before") {
    val delegate = ComponentLoader.forClassLoader(LazyHttpSender::class.java.classLoader)
    val loader = LazySenderComponentLoader(delegate)

    val providers = loader.load(HttpSenderProvider::class.java).toList()
    assert(providers.size == 1)
    assert(providers.single() is LazyHttpSenderProvider)

    assert(loader.load(Runnable::class.java).toList() == delegate.load(Runnable::class.java).toList())
  }
}
