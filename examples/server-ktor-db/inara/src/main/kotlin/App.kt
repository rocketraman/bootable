import com.github.rocketraman.bootable.boot.AppServiceAcceptor
import com.github.rocketraman.bootable.boot.BootableModule
import com.github.rocketraman.bootable.boot.boot
import com.github.rocketraman.bootable.config.ConfigModule
import com.github.rocketraman.bootable.config.HopliteConfigModule
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.db.exposed.ExposedDatabaseModule
import com.github.rocketraman.bootable.db.flyway.FlywayMigrationModule
import com.github.rocketraman.bootable.db.jdbc.HikariPool
import com.github.rocketraman.bootable.db.jdbc.JdbcDataSourceModule
import com.github.rocketraman.bootable.db.jdbc.SqliteConfigSource
import org.kodein.di.inara.Binding
import org.kodein.di.inara.DIModule

/** This application's own module: the server and what it uses. */
@DIModule
interface EntriesModule {
  val serverConfig: ServerConfig
  val entryRepository: EntryRepository
  val seedEntriesService: SeedEntriesService
  val entryServer: EntryServer
}

/**
 * An [EntriesModule] over [exposed]'s `Database`, with its config from [context], and adding its services to
 * [context], as the Bootable modules do.
 */
fun <C> EntriesModule(
  exposed: ExposedDatabaseModule,
  context: C,
): EntriesModule where C : ConfigModule, C : AppServiceAcceptor {
  val module = EntriesModule(
    serverConfig = context.config("server"),
    entryRepository = Binding.single { EntryRepository(exposed.database) },
    seedEntriesService = Binding.single { SeedEntriesService(entryRepository) },
    entryServer = Binding.single { EntryServer(serverConfig, entryRepository, "inara") },
  )
  context.addAppService { module.seedEntriesService }
  context.addAppService { module.entryServer }
  return module
}

/** The application's module: the Bootable modules it uses, and its own, composed. */
@DIModule(composed = true)
interface AppModule :
  BootableModule,
  JdbcDataSourceModule,
  FlywayMigrationModule,
  ExposedDatabaseModule,
  EntriesModule

/**
 * Inara wiring of the database-backed server. Each module is built by its own function, from the modules it uses and
 * the `BootableModule`, which gives it its config (Hoplite's `HopliteConfigModule()`) and takes its services. The
 * database is `JdbcDataSourceModule(...)` with the two choices: a driver module's config (`SqliteConfigSource`, from
 * `db.jdbc`) and a pool (`HikariPool()`, from `db.pool`). On top of it, `FlywayMigrationModule(...)` migrates the
 * schema (`db.flyway`), and `ExposedDatabaseModule(...)` provides the Exposed `Database` and verifies the schema of the
 * given tables (`db.exposed`).
 *
 * Composing the modules into an `AppModule` is optional, since each builder adds its services to the
 * `BootableModule`, but it gives the application, and its tests, one module to reach everything from.
 */
fun appModule(): AppModule {
  val bootable = BootableModule(HopliteConfigModule())
  val jdbc = JdbcDataSourceModule(SqliteConfigSource, HikariPool(), bootable)
  val exposed = ExposedDatabaseModule(jdbc, setOf(EntryTable), bootable)
  return AppModule(
    bootableModule = bootable,
    jdbcDataSourceModule = jdbc,
    flywayMigrationModule = FlywayMigrationModule(jdbc, bootable),
    exposedDatabaseModule = exposed,
    entriesModule = EntriesModule(exposed, bootable),
  )
}

fun main() = boot {
  appModule().bootable
}
