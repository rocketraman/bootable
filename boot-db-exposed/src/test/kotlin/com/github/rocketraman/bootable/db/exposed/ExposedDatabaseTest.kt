package com.github.rocketraman.bootable.db.exposed

import com.github.rocketraman.bootable.db.jdbc.HikariConnectionPoolFactory
import com.github.rocketraman.bootable.db.jdbc.JdbcDataSourceService
import com.github.rocketraman.bootable.db.jdbc.SqliteConfig
import com.zaxxer.hikari.HikariDataSource
import de.infix.testBalloon.framework.core.testSuite
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.nio.file.Path
import java.sql.Connection
import java.util.concurrent.Executors
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

val ExposedDatabaseTest by testSuite {
  test("typed settings are applied") {
    val config = databaseConfig(
      ExposedConfig(
        defaultIsolationLevel = TransactionIsolation.READ_COMMITTED,
        defaultReadOnly = true,
        defaultMaxAttempts = 1,
        defaultMinRetryDelay = 10.milliseconds,
        defaultMaxRetryDelay = 2.seconds,
        defaultQueryTimeout = 30.seconds,
        defaultFetchSize = 500,
        warnLongQueriesDuration = 1.seconds,
        useNestedTransactions = true,
      ),
      customizer = { },
    )
    assert(config.defaultIsolationLevel == Connection.TRANSACTION_READ_COMMITTED)
    assert(config.defaultReadOnly)
    assert(config.defaultMaxAttempts == 1)
    assert(config.defaultMinRetryDelay == 10L)
    assert(config.defaultMaxRetryDelay == 2_000L)
    assert(config.defaultQueryTimeout == 30)
    assert(config.defaultFetchSize == 500)
    assert(config.warnLongQueriesDuration == 1_000L)
    assert(config.useNestedTransactions)
  }

  test("unset settings keep Exposed's defaults") {
    val config = databaseConfig(ExposedConfig(), customizer = { })
    assert(config.defaultIsolationLevel == -1)
    assert(!config.defaultReadOnly)
    assert(config.defaultMaxAttempts == 3)
    assert(config.warnLongQueriesDuration == null)
  }

  test("the customizer runs after the typed settings") {
    val config = databaseConfig(ExposedConfig(defaultMaxAttempts = 2)) {
      it.defaultMaxAttempts = 5
      it.sqlLogger = StdOutSqlLogger
    }
    assert(config.defaultMaxAttempts == 5)
    assert(config.sqlLogger === StdOutSqlLogger)
  }

  test("the isolation level belongs to each database, not to Exposed globally") {
    withDataSource { database ->
      val serializable = exposedDatabase(database.dataSource, ExposedConfig(defaultIsolationLevel = TransactionIsolation.SERIALIZABLE))
      val readUncommitted = exposedDatabase(database.dataSource, ExposedConfig(defaultIsolationLevel = TransactionIsolation.READ_UNCOMMITTED))
      assert(serializable.config.defaultIsolationLevel == Connection.TRANSACTION_SERIALIZABLE)
      assert(readUncommitted.config.defaultIsolationLevel == Connection.TRANSACTION_READ_UNCOMMITTED)
      assert(transaction(serializable) { connection.transactionIsolation } == Connection.TRANSACTION_SERIALIZABLE)
      assert(transaction(readUncommitted) { connection.transactionIsolation } == Connection.TRANSACTION_READ_UNCOMMITTED)
    }
  }

  test("creating the database opens no connection") {
    withTempDir { dir ->
      val database = JdbcDataSourceService(SqliteConfig(path = dir.resolve("lazy.db").toString()), HikariConnectionPoolFactory())
      try {
        exposedDatabase(database.dataSource)
        assert((database.dataSource as HikariDataSource).hikariPoolMXBean == null)
      } finally {
        database.shutdown()
      }
    }
  }

  testSuite("dbQuery") {
    test("runs a transaction, reading back what it wrote") {
      withDatabase { db ->
        db.dbQuery { GreetingTable.insert { it[id] = 1; it[text] = "hello" } }
        val text = db.dbQuery { GreetingTable.selectAll().where { GreetingTable.id eq 1 }.single()[GreetingTable.text] }
        assert(text == "hello")
      }
    }

    test("runs on the IO dispatcher, off the caller's thread") {
      withDatabase { db ->
        val single = Executors.newSingleThreadExecutor { Thread(it, "caller") }
        try {
          val thread = withContext(single.asCoroutineDispatcher()) { db.dbQuery { Thread.currentThread().name } }
          assert(thread != "caller")
          assert(thread.startsWith("DefaultDispatcher-worker"))
        } finally {
          single.shutdown()
        }
      }
    }

    test("rolls back when the statement throws") {
      withDatabase { db ->
        runCatching {
          db.dbQuery {
            GreetingTable.insert { it[id] = 1; it[text] = "hello" }
            error("boom")
          }
        }
        assert(db.dbQuery { GreetingTable.selectAll().count() } == 0L)
      }
    }

    test("overrides the database's read-only default for one transaction") {
      withDatabase { db ->
        // the transaction's flag, not the connection's: SQLite can't make an open connection read-only
        assert(!db.dbQuery { readOnly })
        assert(db.dbQuery(readOnly = true) { readOnly })
      }
    }

    test("overrides the database's isolation default for one transaction") {
      withDatabase { db ->
        assert(db.dbQuery(isolation = TransactionIsolation.READ_UNCOMMITTED) { connection.transactionIsolation } == Connection.TRANSACTION_READ_UNCOMMITTED)
      }
    }
  }
}

@OptIn(ExperimentalPathApi::class)
internal suspend fun withTempDir(block: suspend (Path) -> Unit) {
  val dir = createTempDirectory("boot-db-exposed")
  try {
    block(dir)
  } finally {
    dir.deleteRecursively()
  }
}

/** A started SQLite pool in a temporary directory. */
internal suspend fun withDataSource(block: suspend (JdbcDataSourceService) -> Unit) = withTempDir { dir ->
  val database = JdbcDataSourceService(SqliteConfig(path = dir.resolve("test.db").toString()), HikariConnectionPoolFactory())
  database.start()
  try {
    block(database)
  } finally {
    database.shutdown()
  }
}

/** An Exposed database with the greeting table, over a started SQLite pool. */
private suspend fun withDatabase(block: suspend (Database) -> Unit) = withDataSource { database ->
  val db = exposedDatabase(database.dataSource)
  transaction(db) { SchemaUtils.create(GreetingTable) }
  block(db)
}
