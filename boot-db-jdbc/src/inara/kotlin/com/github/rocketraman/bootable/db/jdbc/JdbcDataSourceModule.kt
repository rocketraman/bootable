@file:JvmName("JdbcDataSourceModuleInara")

package com.github.rocketraman.bootable.db.jdbc

import com.github.rocketraman.bootable.boot.AppServices
import com.github.rocketraman.bootable.boot.HealthChecks
import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.ConfigModule
import com.github.rocketraman.bootable.config.config
import org.kodein.di.inara.Binding
import org.kodein.di.inara.DIModule
import org.kodein.di.inara.DISet
import javax.sql.DataSource

/** Marks a module member as one of the [DataSourceWrapper]s of the pooled `DataSource`, in the order they wrap it. */
@DISet(of = DataSourceWrapper::class)
annotation class DataSourceWrappers

/**
 * The Inara module that provides the application's [ConnectionPoolFactory], such as `hikariPoolModule()` from
 * `boot-db-pool-hikari`.
 */
@DIModule
interface ConnectionPoolModule {
  val connectionPoolFactory: ConnectionPoolFactory
}

/**
 * The Inara module that provides the pooled [DataSource], with the pool of the [ConnectionPoolModule] it requires, and
 * its config from the [ConfigModule]. Build it with `jdbcDataSourceModule(configSource)`, and compose it into the
 * application's module. Its [jdbcDataSourceService] is one of the application's services, so the pool starts and
 * stops with the application.
 *
 * To wrap the pool's [DataSource], mark a member [DataSourceWrappers], or compose a module that does, such as
 * `jdbcTracingModule()` from `boot-db-jdbc-otel`.
 */
@DIModule(sets = [DataSourceWrappers::class])
interface JdbcDataSourceModule : @DIModule.Required ConfigModule, @DIModule.Required ConnectionPoolModule {
  val jdbcConfig: JdbcConfig
  @AppServices val jdbcDataSourceService: JdbcDataSourceService
  val dataSource: DataSource get() = jdbcDataSourceService.dataSource
}

/**
 * Where `jdbcDataSourceModule(…)` gets its [JdbcConfig], such as `SqliteConfigSource` from `boot-db-jdbc-sqlite`, which
 * decodes a typed `SqliteConfig`. The JDBC driver itself comes with the driver module, as a dependency.
 */
fun interface JdbcConfigSource {
  /** Decodes the [JdbcConfig], typically from the `db.jdbc` config section. */
  fun jdbcConfig(configBinder: ConfigBinder): JdbcConfig
}

/** Any database by URL, with a [JdbcGenericConfig] decoded from the `db.jdbc` config section. */
object JdbcGenericConfigSource : JdbcConfigSource {
  override fun jdbcConfig(configBinder: ConfigBinder): JdbcConfig = configBinder.config<JdbcGenericConfig>("db.jdbc")
}

/** A [JdbcDataSourceModule] whose [JdbcConfig] comes from [configSource], e.g. `jdbcDataSourceModule(SqliteConfigSource)`. */
fun jdbcDataSourceModule(configSource: JdbcConfigSource): JdbcDataSourceModulePart = JdbcDataSourceModulePart(
  jdbcConfig = Binding.single { configSource.jdbcConfig(configBinder) },
  jdbcDataSourceService = Binding.single {
    JdbcDataSourceService(jdbcConfig, connectionPoolFactory, DataSourceWrapper.of(setOfDataSourceWrappers()))
  },
)

/**
 * The Inara module that adds a [JdbcHealthCheck] of the pooled [DataSource] of the [JdbcDataSourceModule] it requires
 * to the application's readiness checks. See [JdbcHealthCheck] for when readiness should depend on the database.
 */
@DIModule
interface JdbcHealthCheckModule : @DIModule.Required JdbcDataSourceModule {
  @HealthChecks val jdbcHealthCheck: JdbcHealthCheck
}

/** A [JdbcHealthCheckModule]. */
fun jdbcHealthCheckModule(): JdbcHealthCheckModulePart =
  JdbcHealthCheckModulePart(jdbcHealthCheck = Binding.single { JdbcHealthCheck(dataSource) })
