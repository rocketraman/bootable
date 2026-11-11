@file:JvmName("HikariPoolModuleInara")

package com.github.rocketraman.bootable.db.jdbc

import com.github.rocketraman.bootable.config.ConfigModule
import com.github.rocketraman.bootable.config.config
import org.kodein.di.inara.Binding
import org.kodein.di.inara.DIModule
import org.kodein.di.inara.DISet

/** Marks a module member as one of the [HikariConfigCustomizer]s of the HikariCP pool. */
@DISet(of = HikariConfigCustomizer::class)
annotation class HikariConfigCustomizers

/**
 * The Inara module that provides a HikariCP [ConnectionPoolFactory], with its [HikariPoolConfig] decoded from the
 * `db.pool` config section of the [ConfigModule] it requires. Build it with `hikariPoolModule()`, and compose it into
 * the application's module, alongside the `JdbcDataSourceModule` that builds the pool with it.
 *
 * To adjust HikariCP beyond [HikariPoolConfig], mark a member [HikariConfigCustomizers], or compose a module that does,
 * such as `hikariPoolMetricsModule()` from `boot-db-pool-hikari-otel`.
 */
@DIModule(sets = [HikariConfigCustomizers::class])
interface HikariPoolModule : ConnectionPoolModule, @DIModule.Required ConfigModule {
  val hikariPoolConfig: HikariPoolConfig
}

/** A [HikariPoolModule]. */
fun hikariPoolModule(): HikariPoolModulePart = HikariPoolModulePart(
  hikariPoolConfig = config("db.pool"),
  connectionPoolFactory = Binding.single {
    val customizers = setOfHikariConfigCustomizers()
    HikariConnectionPoolFactory(hikariPoolConfig) { config -> customizers.forEach { it.customize(config) } }
  },
)
