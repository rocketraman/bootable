package com.github.rocketraman.bootable.db.jdbc

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.BootScope
import com.github.rocketraman.bootable.boot.HealthCheck
import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import javax.sql.DataSource

/**
 * Provides the pooled [DataSource] and registers its [JdbcDataSourceService] into the app-service set, so the
 * pool starts and stops with the application.
 *
 * It needs two bindings, one for each choice the application makes:
 * - the driver's [JdbcConfig]: a driver module such as `boot-db-jdbc-sqlite` contributes one. To configure any
 *   database by URL instead, include [JdbcGenericConfigBindings] or provide your own:
 *   ```
 *   @ContributesTo(AppScope::class)
 *   interface DbConfigBindings : ConfigBinderProvider {
 *     @Provides fun dbConfig(): JdbcConfig = config<JdbcGenericConfig>("db.jdbc")
 *   }
 *   ```
 * - the pool's [ConnectionPoolFactory]: a pool module such as `boot-db-pool-hikari` contributes one.
 *
 * Without either, the graph fails to compile with a missing binding.
 *
 * To wrap the pool's [DataSource], contribute a [DataSourceWrapper], or depend on a module that does, such as
 * `boot-db-jdbc-otel-metro`:
 *
 * ```
 * @Provides @IntoSet fun myWrapper(): DataSourceWrapper = DataSourceWrapper { MyDataSource(it) }
 * ```
 */
@ContributesTo(BootScope::class)
interface JdbcDataSourceBindings {
  @Multibinds(allowEmpty = true) val dataSourceWrappers: Set<DataSourceWrapper>

  companion object {
    @Provides @SingleIn(BootScope::class)
    fun jdbcDataSourceService(
      config: JdbcConfig,
      poolFactory: ConnectionPoolFactory,
      wrappers: Set<DataSourceWrapper>,
    ): JdbcDataSourceService = JdbcDataSourceService(config, poolFactory, DataSourceWrapper.of(wrappers))

    @Provides fun dataSource(service: JdbcDataSourceService): DataSource = service.dataSource

    @Provides @IntoSet fun jdbcDataSourceAppService(service: JdbcDataSourceService): AppService = service
  }
}

/**
 * Decodes a [JdbcGenericConfig] from the `db.jdbc` config section as the [JdbcConfig].
 *
 * Deliberately not contributed, since a driver module may provide the config instead. Name it in your graph:
 *
 * ```
 * @DependencyGraph(AppScope::class, additionalScopes = [BootScope::class], bindingContainers = [JdbcGenericConfigBindings::class])
 * ```
 */
@BindingContainer
object JdbcGenericConfigBindings {
  @Provides @SingleIn(BootScope::class)
  fun jdbcConfig(binder: ConfigBinder): JdbcConfig = binder.config<JdbcGenericConfig>("db.jdbc")
}

/**
 * Adds a [JdbcHealthCheck] of the pooled [DataSource] to the readiness checks of `BootableDefaultBindings` (from
 * boot-metro). See [JdbcHealthCheck] for when readiness should depend on the database.
 *
 * Deliberately not contributed, so that depending on boot-db-jdbc-metro doesn't change readiness. Name it in your
 * graph:
 *
 * ```
 * @DependencyGraph(AppScope::class, additionalScopes = [BootScope::class], bindingContainers = [JdbcHealthCheckBindings::class])
 * ```
 */
@BindingContainer
object JdbcHealthCheckBindings {
  @Provides @IntoSet
  fun jdbcHealthCheck(dataSource: DataSource): HealthCheck = JdbcHealthCheck(dataSource)
}
