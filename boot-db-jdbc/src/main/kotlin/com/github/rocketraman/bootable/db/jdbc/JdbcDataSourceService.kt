package com.github.rocketraman.bootable.db.jdbc

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.DefaultPhase
import com.github.rocketraman.bootable.boot.Ordering
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
 * The service is in the [DefaultPhase.Infrastructure] phase, so services that use the database, in later phases,
 * start after the pool and shut down before it. It provides [DbMilestones.DataSourceReady], for anything in the same
 * phase that must start after the pool.
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

  override fun ordering() = Ordering(DefaultPhase.Infrastructure, provides = setOf(DbMilestones.DataSourceReady))

  companion object {
    private const val VALIDATION_TIMEOUT_SECONDS = 5
  }
}
