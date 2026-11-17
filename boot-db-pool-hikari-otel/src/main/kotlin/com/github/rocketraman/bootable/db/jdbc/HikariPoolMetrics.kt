package com.github.rocketraman.bootable.db.jdbc

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.instrumentation.hikaricp.v3_0.HikariTelemetry

/**
 * A [HikariConfigCustomizer] that records HikariCP's pool metrics, such as `db.client.connections.usage` and
 * `db.client.connections.wait_time`, through [openTelemetry], with OpenTelemetry's HikariCP instrumentation. Each
 * metric carries the pool's name, as `pool.name`.
 *
 * Add it to the pool's customizers:
 *
 * ```
 * HikariConnectionPoolFactory(poolConfig, hikariPoolMetrics(telemetry.openTelemetry))
 * ```
 *
 * It sets the pool's `metricsTrackerFactory`. A tracker factory already set by a customizer that ran before it still
 * receives every event, through OpenTelemetry's; one set by a customizer that runs after it replaces it.
 */
fun hikariPoolMetrics(openTelemetry: OpenTelemetry): HikariConfigCustomizer {
  val telemetry = HikariTelemetry.create(openTelemetry)
  return HikariConfigCustomizer { config ->
    config.metricsTrackerFactory = config.metricsTrackerFactory
      ?.let { telemetry.createMetricsTrackerFactory(it) }
      ?: telemetry.createMetricsTrackerFactory()
  }
}
