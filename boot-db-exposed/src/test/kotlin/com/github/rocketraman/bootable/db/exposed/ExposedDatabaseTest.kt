package com.github.rocketraman.bootable.db.exposed

import com.github.rocketraman.bootable.db.flyway.FlywayMigrationService
import com.github.rocketraman.bootable.db.jdbc.HikariConnectionPoolFactory
import com.github.rocketraman.bootable.db.jdbc.JdbcDataSourceService
import com.github.rocketraman.bootable.db.jdbc.SqliteConfig
import com.github.rocketraman.bootable.db.jdbc.TransactionIsolation
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
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
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
      fun db(isolation: TransactionIsolation) =
        ExposedDatabaseService(database.dataSource, ExposedConfig(defaultIsolationLevel = isolation)).database
      val serializable = db(TransactionIsolation.SERIALIZABLE)
      val readUncommitted = db(TransactionIsolation.READ_UNCOMMITTED)
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
        ExposedDatabaseService(database.dataSource)
        assert((database.dataSource as HikariDataSource).hikariPoolMXBean == null)
      } finally {
        database.shutdown()
      }
    }
  }

  test("shutdown unregisters the database from Exposed") {
    withDataSource { database ->
      val service = ExposedDatabaseService(database.dataSource)
      val db = service.database
      assert(TransactionManager.primaryDatabase === db)
      transaction { SchemaUtils.create(GreetingTable) }

      service.shutdown()
      assert(TransactionManager.primaryDatabase !== db)
      assert(runCatching { TransactionManager.managerFor(db) }.isFailure)
    }
  }

  test("is unregistered after the services that use the database, and before the pool closes") {
    val priority = ExposedDatabaseService.PRIORITY
    assert(priority < JdbcDataSourceService.PRIORITY)
    assert(priority > FlywayMigrationService.PRIORITY && priority > SchemaVerificationService.PRIORITY && priority > 0)
  }

  testSuite("withTransaction") {
    test("runs a transaction, reading back what it wrote") {
      withDatabase { db ->
        db.withTransaction { GreetingTable.insert { it[id] = 1; it[text] = "hello" } }
        val text = db.withTransaction {
          GreetingTable.selectAll().where { GreetingTable.id eq 1 }.single()[GreetingTable.text]
        }
        assert(text == "hello")
      }
    }

    test("runs on the IO dispatcher by default, off the caller's thread") {
      withDatabase { db ->
        val single = Executors.newSingleThreadExecutor { Thread(it, "caller") }
        try {
          val thread = withContext(single.asCoroutineDispatcher()) {
            db.withTransaction { Thread.currentThread().name }
          }
          assert(thread != "caller")
          assert(thread.startsWith("DefaultDispatcher-worker"))
        } finally {
          single.shutdown()
        }
      }
    }

    test("runs on the dispatcher an ExposedConfigCustomizer sets") {
      withDataSource { database ->
        val dbExecutor = Executors.newSingleThreadExecutor { Thread(it, "db-dispatcher") }
        try {
          val dispatcher = dbExecutor.asCoroutineDispatcher()
          val db = ExposedDatabaseService(database.dataSource) { it.dispatcher = dispatcher }.database
          assert(db.withTransaction { Thread.currentThread().name }.startsWith("db-dispatcher"))
        } finally {
          dbExecutor.shutdown()
        }
      }
    }

    test("rolls back when the statement throws") {
      withDatabase { db ->
        runCatching {
          db.withTransaction {
            GreetingTable.insert { it[id] = 1; it[text] = "hello" }
            error("boom")
          }
        }
        assert(db.withTransaction { GreetingTable.selectAll().count() } == 0L)
      }
    }

    testSuite("called inside another withTransaction") {
      // e.g. repository methods, each a withTransaction of its own, composed by a caller into one unit of work
      suspend fun Database.insertGreeting(id: Int) = withTransaction {
        GreetingTable.insert { it[GreetingTable.id] = id; it[text] = "hi" }
      }

      test("joins the outer transaction") {
        withDatabase { db ->
          db.withTransaction {
            val outer = this
            db.withTransaction { assert(this === outer) }
          }
        }
      }

      test("commits with the outer transaction") {
        withDatabase { db ->
          db.withTransaction {
            db.insertGreeting(1)
            db.insertGreeting(2)
          }
          assert(db.withTransaction { GreetingTable.selectAll().count() } == 2L)
        }
      }

      test("rolls back with the outer transaction") {
        withDatabase { db ->
          runCatching {
            db.withTransaction {
              db.insertGreeting(1)
              db.insertGreeting(2)
              error("boom")
            }
          }
          assert(db.withTransaction { GreetingTable.selectAll().count() } == 0L)
        }
      }

      test("rolls back the outer transaction when it fails") {
        withDatabase { db ->
          runCatching {
            db.withTransaction {
              db.insertGreeting(1)
              db.insertGreeting(1) // duplicate key
            }
          }
          assert(db.withTransaction { GreetingTable.selectAll().count() } == 0L)
        }
      }

      test("with useNestedTransactions, rolls back only its own savepoint") {
        withDataSource { database ->
          val db = ExposedDatabaseService(database.dataSource, ExposedConfig(useNestedTransactions = true)).database
          transaction(db) { SchemaUtils.create(GreetingTable) }
          db.withTransaction {
            db.insertGreeting(1)
            runCatching { db.withTransaction { db.insertGreeting(2); error("boom") } }
          }
          assert(db.withTransaction { GreetingTable.selectAll().map { it[GreetingTable.id] } } == listOf(1))
        }
      }
    }

    test("overrides the database's read-only default for one transaction") {
      withDatabase { db ->
        // the transaction's flag, not the connection's: SQLite can't make an open connection read-only
        assert(!db.withTransaction { readOnly })
        assert(db.withTransaction(readOnly = true) { readOnly })
      }
    }

    test("overrides the database's isolation default for one transaction") {
      withDatabase { db ->
        val level = db.withTransaction(isolation = TransactionIsolation.READ_UNCOMMITTED) {
          connection.transactionIsolation
        }
        assert(level == Connection.TRANSACTION_READ_UNCOMMITTED)
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
  val db = ExposedDatabaseService(database.dataSource).database
  transaction(db) { SchemaUtils.create(GreetingTable) }
  block(db)
}
