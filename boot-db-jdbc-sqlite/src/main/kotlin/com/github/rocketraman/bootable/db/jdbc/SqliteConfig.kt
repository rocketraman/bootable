package com.github.rocketraman.bootable.db.jdbc

import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * A strongly typed [JdbcConfig] for a SQLite database file, with defaults suited to a pooled server application:
 *
 * - [journalMode] `WAL`, so readers don't block the writer.
 * - [foreignKeys] on. SQLite ignores foreign key constraints unless this is enabled on each connection.
 * - [busyTimeout] of 5 seconds, so a connection waits for a lock instead of failing immediately with
 *   `SQLITE_BUSY`.
 * - A pool of one connection unless the pool's `maximumPoolSize` is set, since SQLite allows only one writer at a
 *   time.
 * - [createDirectories] on, so the database file's directory is created when the application starts.
 *
 * Decoded from the `db.jdbc` config section:
 *
 * ```
 * db {
 *   jdbc {
 *     path = "./app.db"
 *   }
 * }
 * ```
 *
 * The settings are applied as SQLite pragmas on every connection the pool opens.
 *
 * @property path The database file, created by SQLite if it does not exist.
 * @property createDirectories Whether to create the directory of [path] if it doesn't exist, when the application
 *   starts. SQLite creates the database file itself, but not its directory. Turn this off where the application should
 *   not create directories, so that a missing one fails the start instead.
 */
data class SqliteConfig(
  val path: String,
  val journalMode: SqliteJournalMode = SqliteJournalMode.WAL,
  val foreignKeys: Boolean = true,
  val busyTimeout: Duration = 5.seconds,
  val synchronous: SqliteSynchronous? = null,
  val createDirectories: Boolean = true,
) : JdbcConfig {
  override val jdbcUrl get() = "jdbc:sqlite:$path"
  override val driverClassName get() = "org.sqlite.JDBC"
  override val username: String? get() = null
  override val password: String? get() = null
  override val maxConnectionsHint get() = 1

  override fun prepare() {
    if (createDirectories) Path(path).parent?.createDirectories()
  }

  override val dataSourceProperties: Map<String, String>
    get() = buildMap {
      put("journal_mode", journalMode.name)
      put("foreign_keys", foreignKeys.toString())
      put("busy_timeout", busyTimeout.inWholeMilliseconds.toString())
      synchronous?.let { put("synchronous", it.name) }
    }
}

/** SQLite's [journal modes](https://www.sqlite.org/pragma.html#pragma_journal_mode). */
enum class SqliteJournalMode { DELETE, TRUNCATE, PERSIST, MEMORY, WAL, OFF }

/**
 * SQLite's [synchronous settings](https://www.sqlite.org/pragma.html#pragma_synchronous). `NORMAL` is the
 * usual choice with WAL: still safe from corruption, but the latest commits can be lost on power failure.
 */
enum class SqliteSynchronous { OFF, NORMAL, FULL, EXTRA }
