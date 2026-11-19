package com.github.rocketraman.bootable.boot

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

// The serial forms are surrogates, rather than the classes' own shapes, so that what a consumer reads is a status of
// UP or DOWN, as health endpoints conventionally report, rather than a Boolean, and a check's result is the same
// object whether it is healthy or not, with no class discriminator to configure. A healthy check's reason is a
// default, so a format leaves it out unless it encodes defaults.

@Serializable
private enum class SerialStatus { UP, DOWN }

private fun status(healthy: Boolean) = if (healthy) SerialStatus.UP else SerialStatus.DOWN

@Serializable
@SerialName("com.github.rocketraman.bootable.boot.HealthStatus")
private class HealthStatusSurrogate(val status: SerialStatus, val reason: String? = null)

/** [HealthStatus] as `{"status": "UP"}` or `{"status": "DOWN", "reason": "…"}`. */
internal object HealthStatusSerializer : KSerializer<HealthStatus> {
  override val descriptor: SerialDescriptor = HealthStatusSurrogate.serializer().descriptor

  override fun serialize(encoder: Encoder, value: HealthStatus) = encoder.encodeSerializableValue(
    HealthStatusSurrogate.serializer(),
    HealthStatusSurrogate(status(value.healthy), (value as? HealthStatus.Unhealthy)?.reason),
  )

  override fun deserialize(decoder: Decoder): HealthStatus {
    val surrogate = decoder.decodeSerializableValue(HealthStatusSurrogate.serializer())
    return when (surrogate.status) {
      SerialStatus.UP -> HealthStatus.Healthy
      SerialStatus.DOWN -> HealthStatus.Unhealthy(surrogate.reason ?: "")
    }
  }
}

@Serializable
@SerialName("com.github.rocketraman.bootable.boot.HealthReport")
private class HealthReportSurrogate(
  val status: SerialStatus,
  val state: LifecycleState,
  // no default, so it is always there, even when empty
  val checks: Map<String, @Serializable(with = HealthStatusSerializer::class) HealthStatus>,
)

/** [HealthReport] as `{"status": "UP", "state": "Running", "checks": {"jdbc": {"status": "UP"}}}`. */
internal object HealthReportSerializer : KSerializer<HealthReport> {
  override val descriptor: SerialDescriptor = HealthReportSurrogate.serializer().descriptor

  override fun serialize(encoder: Encoder, value: HealthReport) = encoder.encodeSerializableValue(
    HealthReportSurrogate.serializer(),
    HealthReportSurrogate(status(value.healthy), value.state, value.checks),
  )

  override fun deserialize(decoder: Decoder): HealthReport {
    val surrogate = decoder.decodeSerializableValue(HealthReportSurrogate.serializer())
    return HealthReport(surrogate.status == SerialStatus.UP, surrogate.state, surrogate.checks)
  }
}
