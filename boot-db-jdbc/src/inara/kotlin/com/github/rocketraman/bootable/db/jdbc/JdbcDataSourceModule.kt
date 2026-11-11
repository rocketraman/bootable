@file:JvmName("JdbcDataSourceModuleInara")

package com.github.rocketraman.bootable.db.jdbc

import com.github.rocketraman.bootable.boot.AppServiceAcceptor
import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.ConfigModule
import com.github.rocketraman.bootable.config.config
import org.kodein.di.inara.Binding
import org.kodein.di.inara.DIModule
import javax.sql.DataSource

/**
 * The Inara module that provides the pooled [DataSource]. Build it with the
 * `JdbcDataSourceModule(configSource, pool, …)` builder, which adds its [jdbcDataSourceService] to the application's
 * services, so the pool starts and stops with the application.
 */
@DIModule
interface JdbcDataSourceModule {
  val jdbcConfig: JdbcConfig
  val connectionPoolFactory: ConnectionPoolFactory
  val jdbcDataSourceService: JdbcDataSourceService
  val dataSource: DataSource get() = jdbcDataSourceService.dataSource
}

/**
 * Where `JdbcDataSourceModule(…)` gets its [JdbcConfig], such as `SqliteConfigSource` from `boot-db-jdbc-sqlite`, which
 * decodes a typed `SqliteConfig`. The JDBC driver itself comes with the driver module, as a dependency.
 */
fun interface JdbcConfigSource {
  /** Decodes the [JdbcConfig], typically from the `db.jdbc` config section. */
  fun jdbcConfig(configBinder: ConfigBinder): JdbcConfig
}

/** A connection pool for `JdbcDataSourceModule(…)`, such as `HikariPool()` from `boot-db-pool-hikari`. */
fun interface JdbcPool {
  /** Creates this pool's [ConnectionPoolFactory], typically with settings from the `db.pool` config section. */
  fun connectionPoolFactory(configBinder: ConfigBinder): ConnectionPoolFactory
}

/** Any database by URL, with a [JdbcGenericConfig] decoded from the `db.jdbc` config section. */
object JdbcGenericConfigSource : JdbcConfigSource {
  override fun jdbcConfig(configBinder: ConfigBinder): JdbcConfig = configBinder.config<JdbcGenericConfig>("db.jdbc")
}

/**
 * A [JdbcDataSourceModule] from the application's two choices, a [configSource] and a [pool], configured from
 * [context]'s config, and adding its service to [context], such as the `BootableModule`:
 *
 * ```
 * JdbcDataSourceModule(SqliteConfigSource, HikariPool(), bootable)
 * ```
 */
fun <C> JdbcDataSourceModule(
  configSource: JdbcConfigSource,
  pool: JdbcPool,
  context: C,
): JdbcDataSourceModule where C : ConfigModule, C : AppServiceAcceptor {
  val module = JdbcDataSourceModule(
    jdbcConfig = Binding.single { configSource.jdbcConfig(context.configBinder) },
    connectionPoolFactory = Binding.single { pool.connectionPoolFactory(context.configBinder) },
    jdbcDataSourceService = Binding.single { JdbcDataSourceService(jdbcConfig, connectionPoolFactory) },
  )
  context.addAppService { module.jdbcDataSourceService }
  return module
}
