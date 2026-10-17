package com.github.rocketraman.bootable.db.flyway

/**
 * Flyway settings. Every field is optional; an unset field keeps Flyway's default.
 *
 * Decoded from the `db.flyway` config section:
 *
 * ```
 * db {
 *   jdbc { ... }
 *   flyway {
 *     locations = ["classpath:db/migration", "classpath:db/seed"]
 *     baselineOnMigrate = true
 *   }
 * }
 * ```
 *
 * For a Flyway setting not listed here, such as callbacks or Java migrations, use a
 * [FlywayConfigurationCustomizer].
 */
data class FlywayConfig(
  /**
   * Where to find migrations, e.g. `classpath:db/migration` or `filesystem:/opt/app/sql`. Flyway's default is
   * `classpath:db/migration`.
   */
  val locations: List<String>? = null,
  /** Whether to fail when a location doesn't exist, rather than quietly finding no migrations in it. */
  val failOnMissingLocations: Boolean? = null,
  /** The schema Flyway manages and keeps its history table in. Defaults to the connection's default schema. */
  val defaultSchema: String? = null,
  /** The schemas Flyway manages, creating them if they don't exist. */
  val schemas: List<String>? = null,
  /** The name of Flyway's schema history table. Flyway's default is `flyway_schema_history`. */
  val table: String? = null,
  /**
   * Whether to baseline a database that has tables but no history table, rather than fail. Use it to bring an
   * existing database under Flyway: migrations up to [baselineVersion] are assumed applied.
   */
  val baselineOnMigrate: Boolean? = null,
  /** The version [baselineOnMigrate] marks an existing database with. Flyway's default is `1`. */
  val baselineVersion: String? = null,
  /** Whether to validate the applied migrations against the available ones before migrating. On by default. */
  val validateOnMigrate: Boolean? = null,
  /** Whether to fail on migration files whose names don't follow Flyway's naming, rather than ignore them. */
  val validateMigrationNaming: Boolean? = null,
  /** Whether to apply a migration older than the latest applied one, rather than fail validation. */
  val outOfOrder: Boolean? = null,
  /** The version to migrate up to, rather than the latest, e.g. `5` or `latest`. */
  val target: String? = null,
  /**
   * Whether Flyway's `clean`, which drops every object in the managed schemas, is disabled. Flyway disables it by
   * default; this service never cleans, but the [FlywayMigrationService.flyway] instance it exposes could.
   */
  val cleanDisabled: Boolean? = null,
  /**
   * Values for the `${name}` placeholders in migration scripts, e.g. a role name that differs between environments.
   */
  val placeholders: Map<String, String> = emptyMap(),
)
