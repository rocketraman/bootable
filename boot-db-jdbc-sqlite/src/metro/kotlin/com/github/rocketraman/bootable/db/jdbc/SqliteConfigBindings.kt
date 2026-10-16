package com.github.rocketraman.bootable.db.jdbc

import com.github.rocketraman.bootable.boot.BootScope
import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * Provides a [SqliteConfig], decoded from the `db` config section, as the [JdbcConfig] that
 * `JdbcDataSourceBindings` (from boot-db-jdbc-metro) builds the pool from.
 */
@ContributesTo(BootScope::class)
@BindingContainer
object SqliteConfigBindings {
  @Provides @SingleIn(BootScope::class)
  fun sqliteConfig(binder: ConfigBinder): JdbcConfig = binder.config<SqliteConfig>("db")
}
