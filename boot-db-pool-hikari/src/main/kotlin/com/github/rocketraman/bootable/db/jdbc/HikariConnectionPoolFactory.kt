package com.github.rocketraman.bootable.db.jdbc

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.apache.logging.log4j.kotlin.logger
import javax.sql.DataSource

/**
 * Adjusts the [HikariConfig] after the typed settings are applied, for HikariCP features [HikariPoolConfig] doesn't
 * cover, such as a metrics tracker or health-check registry.
 */
fun interface HikariConfigCustomizer {
  fun customize(config: HikariConfig)
}

/**
 * A [ConnectionPoolFactory] backed by HikariCP.
 *
 * ```
 * JdbcDataSourceService(sqliteConfig, HikariConnectionPoolFactory(hikariPoolConfig))
 * ```
 */
class HikariConnectionPoolFactory(
  private val poolConfig: HikariPoolConfig = HikariPoolConfig(),
  private val customizer: HikariConfigCustomizer = HikariConfigCustomizer { },
) : ConnectionPoolFactory {
  override fun create(config: JdbcConfig): HikariConnectionPool {
    // HikariDataSource(HikariConfig) would start the pool, and open a connection, right away. The no-arg
    // constructor plus copyStateTo defers that to the first getConnection(), as ConnectionPool requires.
    val dataSource = HikariDataSource()
    hikariConfig(config, poolConfig) { log.warn { it } }.also(customizer::customize).copyStateTo(dataSource)
    return HikariConnectionPool(dataSource)
  }

  companion object {
    private val log = logger()
  }
}

/** A HikariCP [ConnectionPool]. [hikariDataSource] gives access to HikariCP's own API, e.g. its pool statistics. */
class HikariConnectionPool(val hikariDataSource: HikariDataSource) : ConnectionPool {
  override val dataSource: DataSource get() = hikariDataSource

  override fun close() = hikariDataSource.close()
}

internal fun hikariConfig(
  config: JdbcConfig,
  pool: HikariPoolConfig,
  warn: (String) -> Unit,
): HikariConfig = HikariConfig().apply {
  jdbcUrl = config.jdbcUrl
  config.driverClassName?.let { driverClassName = it }
  config.username?.let { username = it }
  config.password?.let { password = it }
  config.dataSourceProperties.forEach { (key, value) -> addDataSourceProperty(key, value) }

  // the configured size wins, but going above what the driver says the database copes with is likely a mistake
  val hint = config.maxConnectionsHint
  if (pool.maximumPoolSize != null && hint != null && pool.maximumPoolSize > hint) {
    warn(
      "Pool maximumPoolSize ${pool.maximumPoolSize} exceeds the database's maxConnectionsHint of $hint; " +
        "connections beyond the hint may contend for locks or fail"
    )
  }
  (pool.maximumPoolSize ?: hint)?.let { maximumPoolSize = it }
  pool.minimumIdle?.let { minimumIdle = it }
  pool.connectionTimeout?.let { connectionTimeout = it.inWholeMilliseconds }
  pool.idleTimeout?.let { idleTimeout = it.inWholeMilliseconds }
  pool.maxLifetime?.let { maxLifetime = it.inWholeMilliseconds }

  pool.poolName?.let { poolName = it }
  pool.keepaliveTime?.let { keepaliveTime = it.inWholeMilliseconds }
  pool.validationTimeout?.let { validationTimeout = it.inWholeMilliseconds }
  pool.leakDetectionThreshold?.let { leakDetectionThreshold = it.inWholeMilliseconds }
  pool.connectionInitSql?.let { connectionInitSql = it }
  // HikariCP takes the name of the java.sql.Connection constant
  pool.transactionIsolation?.let { transactionIsolation = "TRANSACTION_${it.name}" }
  pool.registerMbeans?.let { isRegisterMbeans = it }
}
