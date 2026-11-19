package com.github.rocketraman.bootable.scheduler.db

import com.github.kagkarlsson.scheduler.Scheduler
import com.github.kagkarlsson.scheduler.SchedulerClient
import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.HealthCheck
import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.db.jdbc.JdbcConfig
import org.kodein.di.DI
import org.kodein.di.bindSet
import org.kodein.di.bindSingleton
import org.kodein.di.inBindSet
import org.kodein.di.instance
import org.kodein.di.instanceOrNull
import org.kodein.di.singleton
import kotlin.time.Clock

/**
 * A Kodein module that registers a [DbSchedulerService] into the app-service set, over the `DataSource` that
 * `jdbcDataSourceModule` provides, with its [DbSchedulerConfig] decoded from the `db.scheduler` config section, and
 * binds its [Scheduler], and the [SchedulerClient] to schedule executions with. Requires a `ConfigBinder` binding (e.g.
 * from boot-config-hoplite's `hopliteConfigModule`). Its dialect matches the [JdbcConfig] binding's URL; its clock is
 * the `kotlin.time.Clock` binding, if there is one.
 *
 * Add the application's tasks, recurring and one-time, through a [DbSchedulerTaskProvider] in the
 * `Set<DbSchedulerTaskProvider>` it opens, and adjust db-scheduler beyond [DbSchedulerConfig] with a
 * [DbSchedulerCustomizer]:
 *
 * ```
 * bindSingleton { new(::Reminders) }
 * inBindSet<DbSchedulerTaskProvider> { add { singleton { instance<Reminders>() } } }
 * inBindSet<DbSchedulerCustomizer> { add { singleton { DbSchedulerCustomizer { it.addSchedulerListener(listener) } } } }
 * ```
 */
val dbSchedulerModule by DI.Module {
  bindSet<DbSchedulerTaskProvider>()
  bindSet<DbSchedulerCustomizer>()
  bindSingleton<DbSchedulerConfig> { instance<ConfigBinder>().config("db.scheduler") }
  bindSingleton {
    val customizers = instance<Set<DbSchedulerCustomizer>>()
    DbSchedulerService(
      dataSource = instance(),
      config = instance(),
      tasks = instance<Set<DbSchedulerTaskProvider>>().flatMap { it.tasks() },
      customizer = { builder -> customizers.forEach { it.customize(builder) } },
      clock = instanceOrNull<Clock>() ?: Clock.System,
      jdbcUrl = instanceOrNull<JdbcConfig>()?.jdbcUrl,
    )
  }
  bindSingleton<Scheduler> { instance<DbSchedulerService>().scheduler }
  bindSingleton<SchedulerClient> { instance<Scheduler>() }
  inBindSet<AppService> { add { singleton { instance<DbSchedulerService>() } } }
}

/**
 * A Kodein module that adds a [DbSchedulerHealthCheck] to the readiness checks of `bootableModule` (from boot-kodein).
 * Import it alongside [dbSchedulerModule]. See [DbSchedulerHealthCheck] for when readiness should depend on it.
 */
val dbSchedulerHealthCheckModule by DI.Module {
  inBindSet<HealthCheck> { add { singleton { DbSchedulerHealthCheck(instance()) } } }
}
