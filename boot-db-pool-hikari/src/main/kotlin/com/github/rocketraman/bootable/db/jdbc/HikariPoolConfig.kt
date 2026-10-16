package com.github.rocketraman.bootable.db.jdbc

import kotlin.time.Duration

/**
 * HikariCP pool settings. Every field is optional; an unset field keeps HikariCP's default, except [maximumPoolSize],
 * which defaults to the driver's [JdbcConfig.maxConnectionsHint] when there is one. Setting [maximumPoolSize] above
 * the hint is allowed, but logs a warning.
 *
 * Decoded from the `db.pool` config section:
 *
 * ```
 * db {
 *   jdbc { ... }
 *   pool {
 *     maximumPoolSize = 20
 *     leakDetectionThreshold = 30s
 *   }
 * }
 * ```
 *
 * For a HikariCP setting not listed here, use a [HikariConfigCustomizer].
 */
data class HikariPoolConfig(
  /** The most connections the pool holds, idle and in use. */
  val maximumPoolSize: Int? = null,
  /** The fewest idle connections the pool tries to keep open. */
  val minimumIdle: Int? = null,
  /** How long to wait for a connection from the pool before failing. */
  val connectionTimeout: Duration? = null,
  /** How long a connection may sit idle before the pool closes it. */
  val idleTimeout: Duration? = null,
  /** How long a connection may live before the pool replaces it. */
  val maxLifetime: Duration? = null,
  /** The pool's name in logs and JMX. */
  val poolName: String? = null,
  /** How often an idle connection is pinged to keep it from being closed by the database or network. */
  val keepaliveTime: Duration? = null,
  /** How long to wait for a connection to be validated as alive. */
  val validationTimeout: Duration? = null,
  /** How long a connection may be out of the pool before a possible leak is logged. */
  val leakDetectionThreshold: Duration? = null,
  /** SQL run on every new connection before it enters the pool. */
  val connectionInitSql: String? = null,
  /** Whether to register the pool's JMX management beans. */
  val registerMbeans: Boolean? = null,
)
