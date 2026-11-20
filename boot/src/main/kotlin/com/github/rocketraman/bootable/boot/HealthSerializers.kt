package com.github.rocketraman.bootable.boot

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** A healthy Boolean as `UP` or `DOWN`, as health endpoints conventionally report it. */
internal object UpDownSerializer : KSerializer<Boolean> {
  override val descriptor: SerialDescriptor =
    PrimitiveSerialDescriptor("com.github.rocketraman.bootable.boot.UpDown", PrimitiveKind.STRING)

  override fun serialize(encoder: Encoder, value: Boolean) = encoder.encodeString(if (value) UP else DOWN)

  override fun deserialize(decoder: Decoder): Boolean = when (val value = decoder.decodeString()) {
    UP -> true
    DOWN -> false
    else -> throw SerializationException("Expected $UP or $DOWN, not $value")
  }

  private const val UP = "UP"
  private const val DOWN = "DOWN"
}

// A check's result is told apart by its subtype, Healthy or Unhealthy, which a field-level serializer can't map: this
// surrogate gives both the same shape, a status and, when unhealthy, a reason, with no class discriminator to
// configure, in any format. A healthy check's reason is a default, so a format leaves it out unless it encodes
// defaults.
@Serializable
@SerialName("com.github.rocketraman.bootable.boot.HealthStatus")
private class HealthStatusSurrogate(
  @SerialName("status") @Serializable(with = UpDownSerializer::class) val healthy: Boolean,
  val reason: String? = null,
)

/** [HealthStatus] as `{"status": "UP"}` or `{"status": "DOWN", "reason": "…"}`. */
internal object HealthStatusSerializer : KSerializer<HealthStatus> {
  override val descriptor: SerialDescriptor = HealthStatusSurrogate.serializer().descriptor

  override fun serialize(encoder: Encoder, value: HealthStatus) = encoder.encodeSerializableValue(
    HealthStatusSurrogate.serializer(),
    HealthStatusSurrogate(value.healthy, (value as? HealthStatus.Unhealthy)?.reason),
  )

  override fun deserialize(decoder: Decoder): HealthStatus {
    val surrogate = decoder.decodeSerializableValue(HealthStatusSurrogate.serializer())
    return if (surrogate.healthy) HealthStatus.Healthy else HealthStatus.Unhealthy(surrogate.reason ?: "")
  }
}
