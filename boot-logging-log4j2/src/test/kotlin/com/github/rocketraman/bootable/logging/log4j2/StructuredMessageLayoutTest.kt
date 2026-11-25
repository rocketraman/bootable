package com.github.rocketraman.bootable.logging.log4j2

import de.infix.testBalloon.framework.core.testSuite
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.core.Layout
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.config.ConfigurationSource
import org.apache.logging.log4j.core.config.xml.XmlConfiguration
import org.apache.logging.log4j.core.impl.Log4jLogEvent
import org.apache.logging.log4j.util.SortedArrayStringMap

/** The layout of the `Console` appender in one of Bootable's base configs, as `loggingInit` would load it. */
private fun consoleLayout(resource: String): Layout<*> {
  val source = ConfigurationSource.fromResource(resource, StructuredMessage::class.java.classLoader)
  val config = XmlConfiguration(null, source)
  config.initialize()
  config.start()
  return config.getAppender<org.apache.logging.log4j.core.Appender>("Console").layout
}

private fun event(
  message: org.apache.logging.log4j.message.Message,
  context: Map<String, String> = emptyMap(),
  thrown: Throwable? = null,
): LogEvent = Log4jLogEvent.newBuilder()
  .setLoggerName("com.example.Orders")
  .setLevel(Level.INFO)
  .setMessage(message)
  .setContextData(SortedArrayStringMap(context))
  .setThrown(thrown)
  .build()

private fun Layout<*>.render(event: LogEvent): String = String(toByteArray(event), Charsets.UTF_8)

private fun Layout<*>.renderJson(event: LogEvent): JsonObject = Json.parseToJsonElement(render(event)).jsonObject

private val orderPlaced = StructuredMessage(
  "order 42 placed",
  "order.id" to "42",
  "order.total" to 1250L,
  "order.gift" to true,
  "order.note" to null,
)

private val otelContext = mapOf(
  "trace_id" to "4bf92f3577b34da6a3ce929d0e0e4736",
  "span_id" to "00f067aa0ba902b7",
  "trace_flags" to "01",
  "tenant" to "acme",
)

val StructuredMessageLayoutTest by testSuite {
  test("the message is the text alone, and the fields are its map entries without nulls") {
    assert(orderPlaced.formattedMessage == "order 42 placed")
    assert(orderPlaced.fields == mapOf("order.id" to "42", "order.total" to 1250L, "order.gift" to true))
    assert(orderPlaced.get("order.total") == "1250")
    assert(!orderPlaced.containsKey("order.note"))
  }

  test("GCLOUD writes the fields as typed top-level fields, beside Cloud Logging's trace and span") {
    val json = consoleLayout("log4j2-base-json-gcloud.xml").renderJson(event(orderPlaced, otelContext))
    assert(json["message"] == JsonPrimitive("order 42 placed"))
    assert(json["order.id"] == JsonPrimitive("42"))
    assert(json["order.total"] == JsonPrimitive(1250))
    assert(json["order.gift"] == JsonPrimitive(true))
    assert("fields" !in json)
    assert(json["logging.googleapis.com/trace"] == JsonPrimitive("4bf92f3577b34da6a3ce929d0e0e4736"))
    assert(json["logging.googleapis.com/spanId"] == JsonPrimitive("00f067aa0ba902b7"))
  }

  test("JSON writes the typed fields and the logging context at top level, and the trace and span as ECS's") {
    val json = consoleLayout("log4j2-base-json.xml").renderJson(event(orderPlaced, otelContext))
    assert(json["ecs.version"] == JsonPrimitive("1.2.0"))
    assert(json["log.level"] == JsonPrimitive("INFO"))
    assert(json["log.logger"] == JsonPrimitive("com.example.Orders"))
    assert(json["message"] == JsonPrimitive("order 42 placed"))
    assert(json["order.id"] == JsonPrimitive("42"))
    assert(json["order.total"] == JsonPrimitive(1250))
    assert(json["order.gift"] == JsonPrimitive(true))
    assert(json["trace.id"] == JsonPrimitive("4bf92f3577b34da6a3ce929d0e0e4736"))
    assert(json["span.id"] == JsonPrimitive("00f067aa0ba902b7"))
    assert(json["tenant"] == JsonPrimitive("acme"))
    assert("trace_id" !in json && "span_id" !in json && "trace_flags" !in json)
    assert("fields" !in json && "labels" !in json)
  }
}
