package com.github.rocketraman.bootable.db.jdbc

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * A strongly typed [JdbcConfig] for a SQLite database file, with defaults suited to a pooled server application:
 *
 * - [journalMode] `WAL`, so readers don't block the writer.
 * - [foreignKeys] on. SQLite ignores foreign key constraints unless this is enabled on each connection.
 * - [busyTimeout] of 5 seconds, so a connection waits for a lock instead of failing immediately with
 *   `SQLITE_BUSY`.
 * - A pool of one connection unless `pool.maximumPoolSize` is set, since SQLite allows only one writer at a time.
 *
 * ```
 * db {
 *   path = "./app.db"
 * }
 * ```
 *
 * The settings are applied as SQLite pragmas on every connection the pool opens. Pass any other pragma or
 * driver setting through [properties], e.g. `cache_size`.
 *
 * @property path The database file. Created if it does not exist; its directory must already exist.
 */
data class SqliteConfig(
  val path: String,
  val journalMode: SqliteJournalMode = SqliteJournalMode.WAL,
  val foreignKeys: Boolean = true,
  val busyTimeout: Duration = 5.seconds,
  val synchronous: SqliteSynchronous? = null,
  val properties: Map<String, String> = emptyMap(),
  override val pool: PoolConfig = PoolConfig(),
) : JdbcConfig {
  override val jdbcUrl get() = "jdbc:sqlite:$path"
  override val driverClassName get() = "org.sqlite.JDBC"
  override val username: String? get() = null
  override val password: String? get() = null
  override val defaultMaximumPoolSize get() = 1

  override val dataSourceProperties: Map<String, String>
    get() = buildMap {
      put("journal_mode", journalMode.name)
      put("foreign_keys", foreignKeys.toString())
      put("busy_timeout", busyTimeout.inWholeMilliseconds.toString())
      synchronous?.let { put("synchronous", it.name) }
      putAll(properties)
    }
}

/** SQLite's [journal modes](https://www.sqlite.org/pragma.html#pragma_journal_mode). */
enum class SqliteJournalMode { DELETE, TRUNCATE, PERSIST, MEMORY, WAL, OFF }

/**
 * SQLite's [synchronous settings](https://www.sqlite.org/pragma.html#pragma_synchronous). `NORMAL` is the
 * usual choice with WAL: still safe from corruption, but the latest commits can be lost on power failure.
 */
enum class SqliteSynchronous { OFF, NORMAL, FULL, EXTRA }
