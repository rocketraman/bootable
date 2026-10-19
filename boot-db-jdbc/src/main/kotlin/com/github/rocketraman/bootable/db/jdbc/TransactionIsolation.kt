package com.github.rocketraman.bootable.db.jdbc

import java.sql.Connection

/**
 * The JDBC transaction isolation levels, as [Connection]'s `TRANSACTION_*` constants, for typed config: e.g. a
 * pool's default for new connections, or a database library's default for new transactions.
 *
 * Not every database supports every level: SQLite, for one, supports only [READ_UNCOMMITTED] and [SERIALIZABLE].
 */
enum class TransactionIsolation(val jdbcLevel: Int) {
  READ_UNCOMMITTED(Connection.TRANSACTION_READ_UNCOMMITTED),
  READ_COMMITTED(Connection.TRANSACTION_READ_COMMITTED),
  REPEATABLE_READ(Connection.TRANSACTION_REPEATABLE_READ),
  SERIALIZABLE(Connection.TRANSACTION_SERIALIZABLE),
}
