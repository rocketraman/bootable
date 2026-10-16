package com.github.rocketraman.bootable.db.jdbc

import kotlin.time.Duration

/**
 * Connection pool settings. Every field is optional; unset fields keep HikariCP's defaults (or, for
 * [maximumPoolSize], the driver's [JdbcConfig.defaultMaximumPoolSize]).
 *
 * [properties] passes any other HikariCP setting by its bean property name, e.g. `leakDetectionThreshold`.
 * The typed fields win when both are set.
 */
data class PoolConfig(
  val poolName: String? = null,
  val maximumPoolSize: Int? = null,
  val minimumIdle: Int? = null,
  val connectionTimeout: Duration? = null,
  val idleTimeout: Duration? = null,
  val maxLifetime: Duration? = null,
  val properties: Map<String, String> = emptyMap(),
)
