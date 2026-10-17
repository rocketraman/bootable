package com.github.rocketraman.bootable.db.jdbc

import com.github.rocketraman.bootable.boot.AppService
import javax.sql.DataSource

/**
 * Owns the application's connection pool and ties it to the application lifecycle. The pool itself comes from a
 * [ConnectionPoolFactory], supplied by a pool module such as `boot-db-pool-hikari`.
 *
 * [dataSource] can be handed to other components as soon as this is constructed, but the pool only opens
 * connections when the service starts (or on first use, if something asks for a connection earlier). Starting
 * runs [JdbcConfig.prepare], then opens and validates a connection, so a database that can't be reached stops the
 * application at boot rather than on the first request. Shutdown closes the pool.
 *
 * The service starts at [PRIORITY], ahead of the default priority of 0, so services that use the database start
 * after the pool and shut down before it.
 */
class JdbcDataSourceService(private val config: JdbcConfig, poolFactory: ConnectionPoolFactory) : AppService {

  private val pool: ConnectionPool = poolFactory.create(config)

  /** The pooled [DataSource]. Bind this, not the service, into anything that needs connections. */
  val dataSource: DataSource get() = pool.dataSource

  override fun start() {
    config.prepare()
    dataSource.connection.use { connection ->
      check(connection.isValid(VALIDATION_TIMEOUT_SECONDS)) { "Connection from the pool is not valid" }
    }
  }

  override fun shutdown() {
    pool.close()
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
