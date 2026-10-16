package com.github.rocketraman.bootable.db.jdbc

import com.zaxxer.hikari.HikariDataSource
import de.infix.testBalloon.framework.core.testSuite
import java.nio.file.Path
import java.sql.SQLException
import javax.sql.DataSource
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.time.Duration.Companion.milliseconds

val SqliteDataSourceTest by testSuite {
  test("pool applies the SQLite pragmas to its connections") {
    withTempDir { dir ->
      val config = SqliteConfig(path = dir.resolve("test.db").toString(), synchronous = SqliteSynchronous.NORMAL)
      JdbcDataSourceService(config, HikariConnectionPoolFactory()).use { dataSource ->
        assert(dataSource.pragma("journal_mode") == "wal")
        assert(dataSource.pragma("foreign_keys") == "1")
        assert(dataSource.pragma("busy_timeout") == "5000")
        // NORMAL
        assert(dataSource.pragma("synchronous") == "1")
      }
    }
  }

  test("foreign keys are enforced") {
    withTempDir { dir ->
      JdbcDataSourceService(SqliteConfig(path = dir.resolve("fk.db").toString()), HikariConnectionPoolFactory())
        .use { dataSource ->
          dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
              statement.execute("CREATE TABLE parent (id INTEGER PRIMARY KEY)")
              statement.execute("CREATE TABLE child (id INTEGER PRIMARY KEY, parent_id INTEGER REFERENCES parent(id))")
              val e = runCatching { statement.execute("INSERT INTO child (id, parent_id) VALUES (1, 42)") }
                .exceptionOrNull()
              assert(e is SQLException && "FOREIGN KEY" in e.message.orEmpty())
            }
          }
        }
    }
  }

  test("pool defaults to a single connection") {
    val config = SqliteConfig(path = "x.db")
    assert(config.maxConnectionsHint == 1)
    val pool = HikariConnectionPoolFactory().create(config)
    try {
      assert(pool.hikariDataSource.maximumPoolSize == 1)
    } finally {
      pool.close()
    }
  }

  test("start creates the database file's directory") {
    withTempDir { dir ->
      val database = dir.resolve("nested/deeper/test.db")
      JdbcDataSourceService(SqliteConfig(path = database.toString()), HikariConnectionPoolFactory()).use { }
      assert(database.exists())
    }
  }

  test("start fails on a missing directory when createDirectories is off") {
    withTempDir { dir ->
      val config = SqliteConfig(path = dir.resolve("missing/test.db").toString(), createDirectories = false)
      val service = JdbcDataSourceService(config, HikariConnectionPoolFactory(HikariPoolConfig(connectionTimeout = 500.milliseconds)))
      try {
        assert(runCatching { service.start() }.exceptionOrNull() is SQLException)
        assert(!dir.resolve("missing").exists())
      } finally {
        service.shutdown()
      }
    }
  }
}

@OptIn(ExperimentalPathApi::class)
private fun withTempDir(block: (Path) -> Unit) {
  val dir = createTempDirectory("boot-db-jdbc-sqlite")
  try {
    block(dir)
  } finally {
    dir.deleteRecursively()
  }
}

private fun JdbcDataSourceService.use(block: (DataSource) -> Unit) {
  start()
  try {
    block(dataSource)
  } finally {
    shutdown()
  }
}

private fun DataSource.pragma(name: String): String = connection.use { connection ->
  connection.createStatement().use { statement ->
    statement.executeQuery("PRAGMA $name").use { rs ->
      rs.next()
      rs.getString(1)
    }
  }
}
