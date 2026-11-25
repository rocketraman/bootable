package com.github.rocketraman.bootable.logging.log4j2

import org.apache.logging.log4j.message.MapMessage

/**
 * A log message with a human-readable [message], and typed key/value fields that a log backend can index.
 *
 * Bootable's JSON layouts write each field as a top-level field of the event, alongside `message`, which holds
 * only the [message].
 * A number or a boolean stays a JSON number or boolean; any other value is written as its `toString()`.
 * The text layouts write the fields after the text, as `{key=value, ...}`.
 *
 * A field whose value is `null` is left out.
 * Name the fields after the backend's schema, such as the
 * [Elastic Common Schema](https://www.elastic.co/guide/en/ecs/current/ecs-field-reference.html)'s
 * `http.request.method` or `event.duration`, so that it reads them without mapping.
 * A field must not have the same name as an existing output field, such as `message` or `@timestamp`.
 * The event would then have two fields with that name.
 * Bootable's logging documentation lists the fields each output type writes.
 *
 * Log one through Log4j's API, like any other message:
 *
 * ```
 * log.info(StructuredMessage("order placed", "order.id" to order.id, "order.total" to order.total))
 * ```
 *
 * Or subclass it for an event of a type of your own, to take the fields from that type:
 *
 * ```
 * class OrderPlaced(order: Order) : StructuredMessage(
 *   "order ${order.id} placed",
 *   "order.id" to order.id,
 *   "order.total" to order.total,
 *   "customer.id" to order.customerId,
 * )
 *
 * log.info(OrderPlaced(order))
 * ```
 *
 * Log4j's own [StructuredDataMessage][org.apache.logging.log4j.message.StructuredDataMessage] is for syslog's
 * RFC 5424 structured data, as Log4j's `Rfc5424Layout` writes it.
 * It needs a structured data id and an event type, limits keys to 32 printable ASCII characters, and holds only
 * strings.
 * Its message is the structured data in syslog's format, `type [id key="value" ...] text`, so a JSON layout's
 * `message` holds that rather than the text.
 * For JSON output, use this instead: its message is the text alone, and its fields keep their types.
 */
open class StructuredMessage(
  val message: String,
  fields: Map<String, Any?>,
) : MapMessage<StructuredMessage, Any>(fields.size) {

  constructor(message: String, vararg fields: Pair<String, Any?>) : this(message, fields.toMap())

  /** The fields, without those whose value was `null`. */
  val fields: Map<String, Any> = buildMap { fields.forEach { (key, value) -> if (value != null) put(key, value) } }

  init {
    this.fields.forEach { (key, value) -> with(key, value) }
  }

  override fun getFormattedMessage(): String = message

  // JsonTemplateLayout's message resolver, and PatternLayout's %m, format a StringBuilderFormattable message, which a
  // MapMessage is, with formatTo rather than getFormattedMessage: both must give the text alone. The JSON layouts' map
  // resolver writes the fields from the MapMessage's entries, and the text layouts' %fields converter from [fields].
  override fun formatTo(buffer: StringBuilder) {
    buffer.append(message)
  }

  override fun newInstance(map: Map<String, Any>): StructuredMessage = StructuredMessage(message, map)

  override fun toString(): String = "StructuredMessage(message=$message, fields=$fields)"
}
