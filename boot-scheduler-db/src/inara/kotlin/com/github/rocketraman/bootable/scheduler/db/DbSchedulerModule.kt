@file:JvmName("DbSchedulerModuleInara")

package com.github.rocketraman.bootable.scheduler.db

import com.github.kagkarlsson.scheduler.Scheduler
import com.github.kagkarlsson.scheduler.SchedulerClient
import com.github.rocketraman.bootable.boot.AppServices
import com.github.rocketraman.bootable.boot.HealthChecks
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.db.jdbc.JdbcDataSourceModule
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryApiModule
import org.kodein.di.inara.Binding
import org.kodein.di.inara.DIModule
import org.kodein.di.inara.DISet
import kotlin.time.Clock

/** Marks a module member as one of the [DbSchedulerTaskProvider]s, the application's db-scheduler tasks. */
@DISet(of = DbSchedulerTaskProvider::class)
annotation class DbSchedulerTaskProviders

/** Marks a module member as one of the [DbSchedulerCustomizer]s of db-scheduler. */
@DISet(of = DbSchedulerCustomizer::class)
annotation class DbSchedulerCustomizers

/**
 * The Inara module that provides a [DbSchedulerService], over the `DataSource` of the [JdbcDataSourceModule] it
 * requires, with its [DbSchedulerConfig] decoded from the `db.scheduler` config section, and its [Scheduler], and the
 * [SchedulerClient] to schedule executions with. The service is one of the application's services. Its dialect
 * matches the `JdbcConfig`'s URL.
 *
 * Each execution is traced, with [dbSchedulerTracing], through the OpenTelemetry of the [OpenTelemetryApiModule] it
 * requires, which with the `NoopOpenTelemetryModule` records nothing.
 *
 * Add the application's tasks, recurring and one-time, with members marked [DbSchedulerTaskProviders], and adjust
 * db-scheduler beyond [DbSchedulerConfig] with members marked [DbSchedulerCustomizers].
 */
@DIModule(sets = [DbSchedulerTaskProviders::class, DbSchedulerCustomizers::class])
interface DbSchedulerModule : @DIModule.Required JdbcDataSourceModule, @DIModule.Required OpenTelemetryApiModule {
  val dbSchedulerConfig: DbSchedulerConfig
  @AppServices val dbSchedulerService: DbSchedulerService
  val scheduler: Scheduler get() = dbSchedulerService.scheduler
  val schedulerClient: SchedulerClient get() = scheduler
}

/** A [DbSchedulerModule] on [clock], with each execution traced as [tracingOptions] says. */
fun dbSchedulerModule(
  clock: Clock = Clock.System,
  tracingOptions: DbSchedulerTracingOptions = DbSchedulerTracingOptions(),
): DbSchedulerModulePart = DbSchedulerModulePart(
  dbSchedulerConfig = config("db.scheduler"),
  dbSchedulerService = Binding.single {
    // the tracing first, so its interceptor is outermost
    val customizers = listOfNotNull(tracingOptions.customizer(openTelemetry)) + setOfDbSchedulerCustomizers()
    DbSchedulerService(
      dataSource = dataSource,
      config = dbSchedulerConfig,
      tasks = setOfDbSchedulerTaskProviders().flatMap { it.tasks() },
      customizer = { builder -> customizers.forEach { it.customize(builder) } },
      clock = clock,
      jdbcUrl = jdbcConfig.jdbcUrl,
    )
  },
)

/**
 * The Inara module that adds a [DbSchedulerHealthCheck] of the [DbSchedulerModule] it requires to the application's
 * readiness checks. See [DbSchedulerHealthCheck] for when readiness should depend on it.
 */
@DIModule
interface DbSchedulerHealthCheckModule : @DIModule.Required DbSchedulerModule {
  @HealthChecks val dbSchedulerHealthCheck: DbSchedulerHealthCheck
}

/** A [DbSchedulerHealthCheckModule]. */
fun dbSchedulerHealthCheckModule(): DbSchedulerHealthCheckModulePart =
  DbSchedulerHealthCheckModulePart(dbSchedulerHealthCheck = Binding.single { DbSchedulerHealthCheck(scheduler) })
