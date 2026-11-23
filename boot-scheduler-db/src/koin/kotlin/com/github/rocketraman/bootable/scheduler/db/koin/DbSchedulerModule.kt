package com.github.rocketraman.bootable.scheduler.db.koin

import com.github.kagkarlsson.scheduler.Scheduler
import com.github.kagkarlsson.scheduler.SchedulerClient
import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.HealthCheck
import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.db.jdbc.JdbcConfig
import com.github.rocketraman.bootable.scheduler.db.DbSchedulerConfig
import com.github.rocketraman.bootable.scheduler.db.DbSchedulerCustomizer
import com.github.rocketraman.bootable.scheduler.db.DbSchedulerHealthCheck
import com.github.rocketraman.bootable.scheduler.db.DbSchedulerService
import com.github.rocketraman.bootable.scheduler.db.DbSchedulerTaskProvider
import com.github.rocketraman.bootable.scheduler.db.DbSchedulerTracingOptions
import com.github.rocketraman.bootable.scheduler.db.dbSchedulerTracing
import io.opentelemetry.api.OpenTelemetry
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlin.time.Clock

/**
 * A Koin module that adds a [DbSchedulerService] to the app services, over the `DataSource` that
 * `jdbcDataSourceModule()` provides, with its [DbSchedulerConfig] decoded from the `db.scheduler` config section, and
 * defines its [Scheduler], and the [SchedulerClient] to schedule executions with. Requires a `ConfigBinder` definition
 * (e.g. from boot-config-hoplite's `hopliteConfigModule()`). Its dialect matches the [JdbcConfig] definition's URL; its
 * clock is the `kotlin.time.Clock` definition, if there is one.
 *
 * Each execution is traced, with [dbSchedulerTracing], through the `OpenTelemetry` definition, if there is one, such as
 * `openTelemetryModule()`'s from boot-telemetry-otel-koin, as the [DbSchedulerTracingOptions] definition, if there is
 * one, says.
 *
 * Add the application's tasks, recurring and one-time, through a [DbSchedulerTaskProvider], and adjust db-scheduler
 * beyond [DbSchedulerConfig] with a [DbSchedulerCustomizer], each with a qualifier of its own:
 *
 * ```
 * single { Reminders(get()) }
 * single<DbSchedulerTaskProvider>(named<Reminders>()) { get<Reminders>() }
 * single<DbSchedulerCustomizer>(named("listener")) { DbSchedulerCustomizer { it.addSchedulerListener(listener) } }
 * ```
 */
fun dbSchedulerModule(): Module = module {
  single<DbSchedulerConfig> { get<ConfigBinder>().config("db.scheduler") }
  single {
    // the tracing first, so its interceptor is outermost
    val tracing = (getOrNull<DbSchedulerTracingOptions>() ?: DbSchedulerTracingOptions())
      .customizer(getOrNull<OpenTelemetry>() ?: OpenTelemetry.noop())
    val customizers = listOfNotNull(tracing) + getAll<DbSchedulerCustomizer>()
    DbSchedulerService(
      dataSource = get(),
      config = get(),
      tasks = getAll<DbSchedulerTaskProvider>().flatMap { it.tasks() },
      customizer = { builder -> customizers.forEach { it.customize(builder) } },
      clock = getOrNull<Clock>() ?: Clock.System,
      jdbcUrl = getOrNull<JdbcConfig>()?.jdbcUrl,
    )
  }
  single<Scheduler> { get<DbSchedulerService>().scheduler }
  single<SchedulerClient> { get<Scheduler>() }
  single<AppService>(named<DbSchedulerService>()) { get<DbSchedulerService>() }
}

/**
 * A Koin module that adds a [DbSchedulerHealthCheck] to the readiness checks of `bootableModule()` (from boot-koin).
 * Include it alongside [dbSchedulerModule]. See [DbSchedulerHealthCheck] for when readiness should depend on it.
 */
fun dbSchedulerHealthCheckModule(): Module = module {
  single<HealthCheck>(named<DbSchedulerHealthCheck>()) { DbSchedulerHealthCheck(get()) }
}
