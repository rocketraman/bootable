package com.github.rocketraman.bootable.logging.log4j2.serialization

import com.github.rocketraman.bootable.logging.log4j2.StructuredMessage
import de.infix.testBalloon.framework.core.testSuite
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.core.Appender
import org.apache.logging.log4j.core.config.ConfigurationSource
import org.apache.logging.log4j.core.config.xml.XmlConfiguration
import org.apache.logging.log4j.core.impl.Log4jLogEvent

@Serializable
private data class Customer(val id: String, val vip: Boolean)

@Serializable
private data class Order(
  val id: String,
  val total: Long,
  val discount: Double,
  val customer: Customer,
  val items: List<String>,
  val note: String? = null,
)

private val order = Order("42", 1250, 0.1, Customer("c-7", vip = true), listOf("book", "pen"))

/** [message] as the `JSON` output type writes it. */
private fun renderJson(message: StructuredMessage) = run {
  val source = ConfigurationSource.fromResource("log4j2-base-json.xml", StructuredMessage::class.java.classLoader)
  val config = XmlConfiguration(null, source).apply { initialize(); start() }
  val layout = config.getAppender<Appender>("Console").layout
  val event = Log4jLogEvent.newBuilder().setLoggerName("com.example.Orders").setLevel(Level.INFO)
    .setMessage(message).build()
  Json.parseToJsonElement(String(layout.toByteArray(event), Charsets.UTF_8)).jsonObject
}

val StructuredMessageOfTest by testSuite {
  test("each property is a typed field, a nested object's named with a dot, and a null is left out") {
    val message = structuredMessageOf("order 42 placed", order)
    assert(message.message == "order 42 placed")
    assert(
      message.fields == mapOf(
        "id" to "42",
        "total" to 1250L,
        "discount" to 0.1,
        "customer.id" to "c-7",
        "customer.vip" to true,
        "items" to listOf("book", "pen"),
      ),
    )
  }

  test("a prefix starts each field's name") {
    val message = structuredMessageOf("order 42 placed", order, prefix = "order")
    assert(message.fields.keys.toList() == listOf(
      "order.id", "order.total", "order.discount", "order.customer.id", "order.customer.vip", "order.items",
    ))
  }

  test("a value that isn't an object is one field, named by the prefix, which it needs") {
    assert(structuredMessageOf("retrying", 3, prefix = "retry.count").fields == mapOf("retry.count" to 3L))
    val error = runCatching { structuredMessageOf("retrying", 3) }.exceptionOrNull()
    assert(error is IllegalArgumentException)
  }

  test("JSON writes the fields with their types, and a list as an array") {
    val json = renderJson(structuredMessageOf("order 42 placed", order, prefix = "order"))
    assert(json["message"] == JsonPrimitive("order 42 placed"))
    assert(json["order.total"] == JsonPrimitive(1250))
    assert(json["order.discount"] == JsonPrimitive(0.1))
    assert(json["order.customer.vip"] == JsonPrimitive(true))
    assert(json["order.items"] == JsonArray(listOf(JsonPrimitive("book"), JsonPrimitive("pen"))))
  }
}
