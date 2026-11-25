package com.github.rocketraman.bootable.db.jdbc.koin

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.HealthCheck
import com.github.rocketraman.bootable.config.koin.configAs
import com.github.rocketraman.bootable.db.jdbc.ConnectionPoolFactory
import com.github.rocketraman.bootable.db.jdbc.DataSourceWrapper
import com.github.rocketraman.bootable.db.jdbc.JdbcConfig
import com.github.rocketraman.bootable.db.jdbc.JdbcDataSourceService
import com.github.rocketraman.bootable.db.jdbc.JdbcGenericConfig
import com.github.rocketraman.bootable.db.jdbc.JdbcHealthCheck
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import javax.sql.DataSource

/**
 * A Koin module that defines the pooled [DataSource] and adds its [JdbcDataSourceService] to the app services, so
 * the pool starts and stops with the application.
 *
 * It needs two definitions, one for each choice the application makes:
 * - the driver's [JdbcConfig]: include a driver module's config module such as `sqliteConfigModule()` from
 *   `boot-db-jdbc-sqlite`, or [jdbcGenericConfigModule] for any database by URL.
 * - the pool's [ConnectionPoolFactory]: include a pool module such as `hikariPoolModule()` from
 *   `boot-db-pool-hikari`.
 *
 * To wrap the pool's [DataSource], define a [DataSourceWrapper], with a qualifier of its own, or include a module that
 * does, such as `jdbcTracingModule` from `boot-db-jdbc-otel`:
 *
 * ```
 * single<DataSourceWrapper>(named("mine")) { DataSourceWrapper { MyDataSource(it) } }
 * ```
 */
fun jdbcDataSourceModule(): Module = module {
  single { JdbcDataSourceService(get(), get(), DataSourceWrapper.of(getAll<DataSourceWrapper>())) }
  single<DataSource> { get<JdbcDataSourceService>().dataSource }
  single<AppService>(named<JdbcDataSourceService>()) { get<JdbcDataSourceService>() }
}

/**
 * A Koin module that decodes a [JdbcGenericConfig] from the `db.jdbc` config section as the [JdbcConfig].
 * Requires a `ConfigBinder` definition (e.g. from boot-config-hoplite's `hopliteConfigModule()`).
 */
fun jdbcGenericConfigModule(): Module = module {
  configAs<JdbcConfig, JdbcGenericConfig>("db.jdbc")
}

/**
 * A Koin module that adds a [JdbcHealthCheck] of the pooled [DataSource] to the readiness checks of `bootableModule()`
 * (from boot-koin). Include it alongside [jdbcDataSourceModule]. See [JdbcHealthCheck] for when readiness should depend
 * on the database.
 */
fun jdbcHealthCheckModule(): Module = module {
  single<HealthCheck>(named<JdbcHealthCheck>()) { JdbcHealthCheck(get()) }
}
