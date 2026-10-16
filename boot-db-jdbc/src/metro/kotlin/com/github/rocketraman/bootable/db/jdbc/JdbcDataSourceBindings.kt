package com.github.rocketraman.bootable.db.jdbc

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.BootScope
import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import javax.sql.DataSource

/**
 * Provides the pooled [DataSource] and registers its [JdbcDataSourceService] into the app-service set, so the
 * pool starts and stops with the application.
 *
 * It needs a [JdbcConfig] binding. A driver module such as `boot-db-jdbc-sqlite` contributes one; to
 * configure any database by URL instead, include [JdbcGenericConfigBindings] or provide your own:
 *
 * ```
 * @ContributesTo(AppScope::class)
 * interface DbConfigBindings : ConfigBinderProvider {
 *   @Provides fun dbConfig(): JdbcConfig = config<JdbcGenericConfig>("db")
 * }
 * ```
 */
@ContributesTo(BootScope::class)
@BindingContainer
object JdbcDataSourceBindings {
  @Provides @SingleIn(BootScope::class)
  fun jdbcDataSourceService(config: JdbcConfig): JdbcDataSourceService = JdbcDataSourceService(config)

  @Provides fun dataSource(service: JdbcDataSourceService): DataSource = service.dataSource

  @Provides @IntoSet fun jdbcDataSourceAppService(service: JdbcDataSourceService): AppService = service
}

/**
 * Decodes a [JdbcGenericConfig] from the `db` config section as the [JdbcConfig].
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
  fun jdbcConfig(binder: ConfigBinder): JdbcConfig = binder.config<JdbcGenericConfig>("db")
}
