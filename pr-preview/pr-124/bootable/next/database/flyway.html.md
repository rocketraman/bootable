# Migrations (Flyway)

> Flyway's migrations, run when the application starts, before anything uses the schema.

`boot-db-flyway` runs Flyway’s `migrate` when the application starts, with `FlywayMigrationService`.

It is in the `Initialization` phase, so it runs after the pool has started and validated its connection, and before the services that use the schema. It reaches the `DbMilestones.SchemaMigrated` milestone, so services in the same phase that use the schema, such as [schema verification](https://rocketraman.github.io/bootable/bootable/next/database/exposed.html.md#schema-verification), start after it. A failed migration fails the start.

## Migrations

The migrations are ordinary Flyway migrations, found in `classpath:db/migration` unless `locations` says otherwise:

```sql
CREATE TABLE note (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  text TEXT NOT NULL
);
```

`flyway-core` supports SQLite itself; the PostgreSQL and MySQL driver modules bring Flyway’s support for their databases.

## Settings

`FlywayConfig` reads `db.flyway`. Every setting is optional, and keeps Flyway’s default when unset.

```hocon
db.flyway {
  locations = ["classpath:db/migration", "classpath:db/seed"]  (1)
  placeholders.reader = notes_reader                          (2)
}
```

- (1) Adds migrations from another location, such as a library’s; Flyway’s default, `classpath:db/migration`, must then be named too.
- (2) Fills in a `${reader}` placeholder in the migration scripts, for a value that differs between environments, such as a role’s name.

The others include `defaultSchema`, `schemas`, `table`, `baselineOnMigrate`, `baselineVersion`, `validateOnMigrate`, `outOfOrder`, and `target`: see [all the settings](https://rocketraman.github.io/bootable/bootable/next/api/boot-db-flyway/com.github.rocketraman.bootable.db.flyway/-flyway-config/index.html). To bring an existing database under Flyway, set `baselineOnMigrate`: the migrations up to `baselineVersion` are taken as applied.

For a Flyway setting `FlywayConfig` doesn’t cover, such as callbacks or Java migrations, add a `FlywayConfigurationCustomizer` to its set binding, or, with vanilla wiring, pass one to `FlywayMigrationService`.

## Reference

- [`FlywayMigrationService`](https://rocketraman.github.io/bootable/bootable/next/api/boot-db-flyway/com.github.rocketraman.bootable.db.flyway/-flyway-migration-service/index.html): the migrations
- [`FlywayConfig`](https://rocketraman.github.io/bootable/bootable/next/api/boot-db-flyway/com.github.rocketraman.bootable.db.flyway/-flyway-config/index.html): the settings
- [`FlywayConfigurationCustomizer`](https://rocketraman.github.io/bootable/bootable/next/api/boot-db-flyway/com.github.rocketraman.bootable.db.flyway/-flyway-configuration-customizer/index.html): for the other settings
