package com.github.rocketraman.bootable.logging.log4j2.serialization

import com.github.rocketraman.bootable.logging.log4j2.StructuredMessage
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.serializer

/**
 * A [StructuredMessage] with the fields of a `@Serializable` [value].
 *
 * The value is encoded with [json], and each of its properties becomes a field.
 * A nested object's properties become fields too, named with a dot, such as `customer.id` for an `Order`'s
 * `customer`'s `id`.
 * With a [prefix], each field's name starts with it, such as `order.id` for a prefix of `order`.
 * A number or a boolean stays a number or a boolean, a list stays a list, and a `null` is left out.
 *
 * ```
 * @Serializable
 * data class Order(val id: String, val total: Long, val customer: Customer)
 *
 * log.info(structuredMessageOf("order ${order.id} placed", order, prefix = "order"))
 * ```
 *
 * A [value] that isn't encoded as an object, such as a number or a list, is a single field, named [prefix], which it
 * then needs.
 */
inline fun <reified T> structuredMessageOf(
  message: String,
  value: T,
  prefix: String? = null,
  json: Json = Json,
): StructuredMessage = structuredMessageOf(message, value, serializer<T>(), prefix, json)

/** A [StructuredMessage] with the fields of [value], encoded with [serializer]; see the reified overload. */
fun <T> structuredMessageOf(
  message: String,
  value: T,
  serializer: SerializationStrategy<T>,
  prefix: String? = null,
  json: Json = Json,
): StructuredMessage {
  val element = json.encodeToJsonElement(serializer, value)
  val fields = LinkedHashMap<String, Any?>()
  if (element is JsonObject) {
    flatten(prefix, element, fields)
  } else {
    requireNotNull(prefix) { "a value that isn't encoded as a JSON object needs a prefix to name its field" }
    fields[prefix] = element.toValue()
  }
  return StructuredMessage(message, fields)
}

private fun flatten(prefix: String?, obj: JsonObject, into: MutableMap<String, Any?>) {
  for ((key, element) in obj) {
    val name = if (prefix == null) key else "$prefix.$key"
    if (element is JsonObject) flatten(name, element, into) else into[name] = element.toValue()
  }
}

private fun JsonElement.toValue(): Any? = when (this) {
  JsonNull -> null
  is JsonPrimitive -> if (isString) content else booleanOrNull ?: longOrNull ?: doubleOrNull ?: content
  is JsonArray -> map { it.toValue() }
  is JsonObject -> mapValues { (_, element) -> element.toValue() }
}
