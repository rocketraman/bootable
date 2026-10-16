package com.github.rocketraman.bootable.db.jdbc

import com.github.rocketraman.bootable.boot.AppService
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.util.Properties
import javax.sql.DataSource

/**
 * Owns a HikariCP connection pool and ties it to the application lifecycle.
 *
 * [dataSource] can be handed to other components as soon as this is constructed, but the pool only opens
 * connections when the service starts (or on first use, if something asks for a connection earlier). Starting
 * opens and validates a connection, so a database that can't be reached stops the application at boot rather
 * than on the first request. Shutdown closes the pool.
 *
 * The service starts at [PRIORITY], ahead of the default priority of 0, so services that use the database start
 * after the pool and shut down before it.
 */
class JdbcDataSourceService(config: JdbcConfig) : AppService {

  private val hikari: HikariDataSource = HikariDataSource().also { hikariConfig(config).copyStateTo(it) }

  /** The pooled [DataSource]. Bind this, not the service, into anything that needs connections. */
  val dataSource: DataSource get() = hikari

  override fun start() {
    hikari.connection.use { connection ->
      check(connection.isValid(VALIDATION_TIMEOUT_SECONDS)) { "Connection from pool ${hikari.poolName} is not valid" }
    }
  }

  override fun shutdown() {
    hikari.close()
  }

  override fun priority() = PRIORITY

  companion object {
    /**
     * High, so the pool starts before (and stops after) the services that use it. Room is left above it for
     * anything that must run even earlier, and below it for services that set up the database, such as migrations.
     */
    const val PRIORITY = Int.MAX_VALUE - 1_000

    private const val VALIDATION_TIMEOUT_SECONDS = 5
  }
}

/** Translates a [JdbcConfig] into a [HikariConfig]. */
fun hikariConfig(config: JdbcConfig): HikariConfig {
  val pool = config.pool
  // HikariConfig(Properties) applies each entry as a bean property, so the untyped settings go first and the
  // typed fields below override them
  val hikariConfig = if (pool.properties.isEmpty()) HikariConfig()
  else HikariConfig(Properties().apply { putAll(pool.properties) })

  return hikariConfig.apply {
    jdbcUrl = config.jdbcUrl
    config.driverClassName?.let { driverClassName = it }
    config.username?.let { username = it }
    config.password?.let { password = it }
    config.dataSourceProperties.forEach { (key, value) -> addDataSourceProperty(key, value) }

    pool.poolName?.let { poolName = it }
    (pool.maximumPoolSize ?: config.defaultMaximumPoolSize)?.let { maximumPoolSize = it }
    pool.minimumIdle?.let { minimumIdle = it }
    pool.connectionTimeout?.let { connectionTimeout = it.inWholeMilliseconds }
    pool.idleTimeout?.let { idleTimeout = it.inWholeMilliseconds }
    pool.maxLifetime?.let { maxLifetime = it.inWholeMilliseconds }
  }
}
