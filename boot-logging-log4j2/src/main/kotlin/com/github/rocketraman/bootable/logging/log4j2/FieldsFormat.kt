@file:JvmName("FieldsFormat")

package com.github.rocketraman.bootable.logging.log4j2

import org.apache.logging.log4j.core.LogEvent

/**
 * Appends the fields of [event]'s message to [buffer] as `{key=value, ...}`, for the `%fields` pattern converter,
 * [FieldsPatternConverter].
 * Appends nothing if the message isn't a [StructuredMessage], or has no fields.
 */
internal fun formatFields(event: LogEvent, buffer: StringBuilder) {
  val fields = (event.message as? StructuredMessage)?.fields
  if (fields.isNullOrEmpty()) return
  fields.entries.joinTo(buffer, prefix = "{", postfix = "}") { (key, value) -> "$key=$value" }
}
