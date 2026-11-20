package com.github.rocketraman.bootable.scheduler.db

import com.github.kagkarlsson.scheduler.Scheduler
import com.github.kagkarlsson.scheduler.SchedulerClient
import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.BootScope
import com.github.rocketraman.bootable.boot.HealthCheck
import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.db.jdbc.JdbcConfig
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.opentelemetry.api.OpenTelemetry
import javax.sql.DataSource
import kotlin.time.Clock

/**
 * Registers a [DbSchedulerService] into the app-service set, over the `DataSource` that `JdbcDataSourceBindings` (from
 * boot-db-jdbc-metro) provides, with its [DbSchedulerConfig] decoded from the `db.scheduler` config section, and
 * provides its [Scheduler], and the [SchedulerClient] to schedule executions with. Its dialect matches the graph's
 * [JdbcConfig]'s URL; its clock is the graph's `kotlin.time.Clock`, if it has one.
 *
 * Each execution is traced, with [dbSchedulerTracing], through the graph's `OpenTelemetry`, if it has one, such as
 * boot-telemetry-otel-metro's, as the graph's [DbSchedulerTracingOptions], if it has one, says.
 *
 * Contribute the application's tasks, recurring and one-time, through a [DbSchedulerTaskProvider], and adjust
 * db-scheduler beyond [DbSchedulerConfig] with a [DbSchedulerCustomizer]:
 *
 * ```
 * @Provides @IntoSet fun reminderTasks(reminders: Reminders): DbSchedulerTaskProvider = reminders
 * ```
 */
@ContributesTo(BootScope::class)
interface DbSchedulerBindings {
  @Multibinds(allowEmpty = true) val dbSchedulerTaskProviders: Set<DbSchedulerTaskProvider>

  @Multibinds(allowEmpty = true) val dbSchedulerCustomizers: Set<DbSchedulerCustomizer>

  companion object {
    @Provides @SingleIn(BootScope::class)
    fun dbSchedulerConfig(binder: ConfigBinder): DbSchedulerConfig = binder.config("db.scheduler")

    @Provides @SingleIn(BootScope::class)
    fun dbSchedulerService(
      dataSource: DataSource,
      config: DbSchedulerConfig,
      jdbcConfig: JdbcConfig,
      taskProviders: Set<DbSchedulerTaskProvider>,
      customizers: Set<DbSchedulerCustomizer>,
      clock: Clock = Clock.System,
      openTelemetry: OpenTelemetry = OpenTelemetry.noop(),
      tracingOptions: DbSchedulerTracingOptions = DbSchedulerTracingOptions(),
    ): DbSchedulerService {
      // the tracing first, so its interceptor is outermost
      val allCustomizers = listOfNotNull(tracingOptions.customizer(openTelemetry)) + customizers
      return DbSchedulerService(
        dataSource = dataSource,
        config = config,
        tasks = taskProviders.flatMap { it.tasks() },
        customizer = { builder -> allCustomizers.forEach { it.customize(builder) } },
        clock = clock,
        jdbcUrl = jdbcConfig.jdbcUrl,
      )
    }

    @Provides fun scheduler(service: DbSchedulerService): Scheduler = service.scheduler

    @Provides fun schedulerClient(scheduler: Scheduler): SchedulerClient = scheduler

    @Provides @IntoSet fun dbSchedulerAppService(service: DbSchedulerService): AppService = service
  }
}

/**
 * Adds a [DbSchedulerHealthCheck] to the readiness checks of `BootableDefaultBindings` (from boot-metro). See
 * [DbSchedulerHealthCheck] for when readiness should depend on it.
 *
 * Deliberately not contributed, so that depending on boot-scheduler-db-metro doesn't change readiness. Name it in your
 * graph's `bindingContainers`.
 */
@BindingContainer
object DbSchedulerHealthCheckBindings {
  @Provides @IntoSet
  fun dbSchedulerHealthCheck(scheduler: Scheduler): HealthCheck = DbSchedulerHealthCheck(scheduler)
}
