package com.github.rocketraman.bootable.db.jdbc

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.HealthCheck
import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config
import org.kodein.di.DI
import org.kodein.di.bindSet
import org.kodein.di.bindSingleton
import org.kodein.di.inBindSet
import org.kodein.di.instance
import org.kodein.di.singleton
import javax.sql.DataSource

/**
 * A Kodein module that binds the pooled [DataSource] and registers its [JdbcDataSourceService] into the
 * app-service set, so the pool starts and stops with the application.
 *
 * It needs two bindings, one for each choice the application makes:
 * - the driver's [JdbcConfig]: import a driver module's config module such as `sqliteConfigModule` from
 *   `boot-db-jdbc-sqlite`, or [jdbcGenericConfigModule] for any database by URL.
 * - the pool's [ConnectionPoolFactory]: import a pool module such as `hikariPoolModule` from
 *   `boot-db-pool-hikari`.
 *
 * To wrap the pool's [DataSource], add a [DataSourceWrapper] to the set this module opens, or import a module that
 * does, such as `jdbcTracingModule` from `boot-db-jdbc-otel`:
 *
 * ```
 * inBindSet<DataSourceWrapper> { add { singleton { DataSourceWrapper { MyDataSource(it) } } } }
 * ```
 */
@Deprecated("Kodein is deprecated: use the -inara flavor, or the -koin flavor for a DSL like Kodein's")
val jdbcDataSourceModule by DI.Module {
  bindSet<DataSourceWrapper>()
  bindSingleton {
    JdbcDataSourceService(instance(), instance(), DataSourceWrapper.of(instance<Set<DataSourceWrapper>>()))
  }
  bindSingleton<DataSource> { instance<JdbcDataSourceService>().dataSource }
  inBindSet<AppService> { add { singleton { instance<JdbcDataSourceService>() } } }
}

/**
 * A Kodein module that decodes a [JdbcGenericConfig] from the `db.jdbc` config section as the [JdbcConfig].
 * Requires a `ConfigBinder` binding (e.g. from boot-config-hoplite's `hopliteConfigModule`).
 */
@Deprecated("Kodein is deprecated: use the -inara flavor, or the -koin flavor for a DSL like Kodein's")
val jdbcGenericConfigModule by DI.Module {
  bindSingleton<JdbcConfig> { instance<ConfigBinder>().config<JdbcGenericConfig>("db.jdbc") }
}

/**
 * A Kodein module that adds a [JdbcHealthCheck] of the pooled [DataSource] to the readiness checks of
 * `bootableModule` (from boot-kodein). Import it alongside [jdbcDataSourceModule]. See [JdbcHealthCheck] for when
 * readiness should depend on the database.
 */
@Deprecated("Kodein is deprecated: use the -inara flavor, or the -koin flavor for a DSL like Kodein's")
val jdbcHealthCheckModule by DI.Module {
  inBindSet<HealthCheck> { add { singleton { JdbcHealthCheck(instance()) } } }
}
