package snippets.background

import com.github.rocketraman.bootable.boot.koin.bootableModule
import com.github.rocketraman.bootable.config.koin.hopliteConfigModule
import com.github.rocketraman.bootable.db.flyway.koin.flywayMigrationModule
import com.github.rocketraman.bootable.db.jdbc.koin.hikariPoolModule
import com.github.rocketraman.bootable.db.jdbc.koin.jdbcDataSourceModule
import com.github.rocketraman.bootable.db.jdbc.koin.sqliteConfigModule
import com.github.rocketraman.bootable.scheduler.db.DbSchedulerTaskProvider
import com.github.rocketraman.bootable.scheduler.db.koin.dbSchedulerModule
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module

// tag::wire[]
fun schedulerKoin() = koinApplication {
  modules(
    bootableModule(),
    hopliteConfigModule(),
    jdbcDataSourceModule(),
    sqliteConfigModule(),
    hikariPoolModule(),
    flywayMigrationModule(),  // migrates the scheduler's table
    dbSchedulerModule(),      // the scheduler, from db.scheduler, and its SchedulerClient
    module {
      single { Notifications() }
      single { Reminders(get()) }
      single<DbSchedulerTaskProvider>(named<Reminders>()) { get<Reminders>() }
      single { ReminderApi(get(), get()) }  // the Reminders, and the SchedulerClient
      // ... and the services that use ReminderApi
    },
  )
}.koin
// end::wire[]
