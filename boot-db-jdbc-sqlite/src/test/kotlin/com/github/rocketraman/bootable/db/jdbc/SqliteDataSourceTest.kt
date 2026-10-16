package com.github.rocketraman.bootable.db.jdbc

import com.zaxxer.hikari.HikariDataSource
import de.infix.testBalloon.framework.core.testSuite
import java.nio.file.Path
import java.sql.SQLException
import javax.sql.DataSource
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.time.Duration.Companion.milliseconds

val SqliteDataSourceTest by testSuite {
  test("pool applies the SQLite pragmas to its connections") {
    withTempDir { dir ->
      val config = SqliteConfig(path = dir.resolve("test.db").toString(), synchronous = SqliteSynchronous.NORMAL)
      JdbcDataSourceService(config).use { dataSource ->
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
      JdbcDataSourceService(SqliteConfig(path = dir.resolve("fk.db").toString())).use { dataSource ->
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

  test("shutdown closes the pool") {
    withTempDir { dir ->
      val service = JdbcDataSourceService(SqliteConfig(path = dir.resolve("close.db").toString()))
      service.use { }
      assert((service.dataSource as HikariDataSource).isClosed)
    }
  }

  test("start fails when the database cannot be opened") {
    withTempDir { dir ->
      val service = JdbcDataSourceService(
        SqliteConfig(
          path = dir.resolve("missing-dir/test.db").toString(),
          pool = PoolConfig(connectionTimeout = 500.milliseconds),
        )
      )
      try {
        assert(runCatching { service.start() }.exceptionOrNull() is SQLException)
      } finally {
        service.shutdown()
      }
    }
  }

  test("pool defaults to a single connection unless configured") {
    assert(hikariConfig(SqliteConfig(path = "x.db")).maximumPoolSize == 1)
    assert(hikariConfig(SqliteConfig(path = "x.db", pool = PoolConfig(maximumPoolSize = 4))).maximumPoolSize == 4)
  }

  test("properties override the typed pragmas") {
    val config = SqliteConfig(path = "x.db", properties = mapOf("journal_mode" to "DELETE", "cache_size" to "-2000"))
    assert(config.dataSourceProperties["journal_mode"] == "DELETE")
    assert(config.dataSourceProperties["cache_size"] == "-2000")
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
