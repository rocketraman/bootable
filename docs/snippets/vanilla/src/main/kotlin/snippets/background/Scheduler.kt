package snippets.background

import com.github.rocketraman.bootable.boot.Bootable
import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.db.flyway.FlywayMigrationService
import com.github.rocketraman.bootable.db.jdbc.HikariConnectionPoolFactory
import com.github.rocketraman.bootable.db.jdbc.JdbcDataSourceService
import com.github.rocketraman.bootable.db.jdbc.SqliteConfig
import com.github.rocketraman.bootable.scheduler.db.DbSchedulerService

fun schedulerApp(binder: ConfigBinder): Bootable {
  // tag::wire[]
  val jdbcConfig = binder.config<SqliteConfig>("db.jdbc")
  val database = JdbcDataSourceService(jdbcConfig, HikariConnectionPoolFactory(binder.config("db.pool")))
  val reminders = Reminders(Notifications())
  val scheduler = DbSchedulerService(
    database.dataSource,
    binder.config("db.scheduler"),
    reminders.tasks(),
    jdbcUrl = jdbcConfig.jdbcUrl,  // the SQL dialect, from the URL
  )
  val reminderApi = ReminderApi(reminders, scheduler.scheduler)

  return Bootable(
    setOf(
      database,
      FlywayMigrationService(database.dataSource, binder.config("db.flyway")),  // migrates the scheduler's table
      scheduler,
      // ... and the services that use reminderApi
    ),
  )
  // end::wire[]
}
