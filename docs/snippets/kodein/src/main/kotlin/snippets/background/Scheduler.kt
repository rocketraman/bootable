package snippets.background

import com.github.rocketraman.bootable.boot.bootableModule
import com.github.rocketraman.bootable.config.hopliteConfigModule
import com.github.rocketraman.bootable.db.flyway.flywayMigrationModule
import com.github.rocketraman.bootable.db.jdbc.hikariPoolModule
import com.github.rocketraman.bootable.db.jdbc.jdbcDataSourceModule
import com.github.rocketraman.bootable.db.jdbc.sqliteConfigModule
import com.github.rocketraman.bootable.scheduler.db.DbSchedulerTaskProvider
import com.github.rocketraman.bootable.scheduler.db.dbSchedulerModule
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import org.kodein.di.inBindSet
import org.kodein.di.instance
import org.kodein.di.new
import org.kodein.di.singleton

// tag::wire[]
fun schedulerDI() = DI {
  import(bootableModule)
  import(hopliteConfigModule)
  import(jdbcDataSourceModule)
  import(sqliteConfigModule)
  import(hikariPoolModule)
  import(flywayMigrationModule)  // migrates the scheduler's table
  import(dbSchedulerModule)      // the scheduler, from db.scheduler, and its SchedulerClient

  bindSingleton { new(::Notifications) }
  bindSingleton { new(::Reminders) }
  inBindSet<DbSchedulerTaskProvider> { add { singleton { instance<Reminders>() } } }
  bindSingleton { ReminderApi(instance(), instance()) }  // the Reminders, and the SchedulerClient
  // ... and the services that use ReminderApi
}
// end::wire[]
