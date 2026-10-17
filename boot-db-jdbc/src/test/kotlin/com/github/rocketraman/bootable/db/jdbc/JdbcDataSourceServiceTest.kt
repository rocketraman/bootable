package com.github.rocketraman.bootable.db.jdbc

import de.infix.testBalloon.framework.core.testSuite
import java.io.PrintWriter
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.logging.Logger
import javax.sql.DataSource
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively

val JdbcDataSourceServiceTest by testSuite {
  test("the factory receives the JdbcConfig") {
    withTempDir { dir ->
      val config = JdbcGenericConfig(jdbcUrl = "jdbc:sqlite:${dir.resolve("config.db")}")
      var received: JdbcConfig? = null
      JdbcDataSourceService(config) { received = it; TestPool(it) }
      assert(received === config)
    }
  }

  test("no connection is opened until the service starts") {
    withTempDir { dir ->
      val pool = TestPool(JdbcGenericConfig(jdbcUrl = "jdbc:sqlite:${dir.resolve("lazy.db")}"))
      val service = JdbcDataSourceService(pool.config) { pool }
      assert(pool.connectionsOpened == 0)
      service.start()
      assert(pool.connectionsOpened == 1)
      service.shutdown()
    }
  }

  test("start prepares the database before opening the first connection") {
    withTempDir { dir ->
      val events = mutableListOf<String>()
      lateinit var pool: TestPool
      val config = object : JdbcConfig by JdbcGenericConfig(jdbcUrl = "jdbc:sqlite:${dir.resolve("prepare.db")}") {
        override fun prepare() {
          events += "prepare (connections opened: ${pool.connectionsOpened})"
        }
      }
      pool = TestPool(config)
      val service = JdbcDataSourceService(config) { pool }
      assert(events.isEmpty())
      service.start()
      assert(events == listOf("prepare (connections opened: 0)"))
      assert(pool.connectionsOpened == 1)
      service.shutdown()
    }
  }

  test("start fails when the database cannot be opened") {
    withTempDir { dir ->
      val pool = TestPool(JdbcGenericConfig(jdbcUrl = "jdbc:sqlite:${dir.resolve("missing-dir/test.db")}"))
      val service = JdbcDataSourceService(pool.config) { pool }
      assert(runCatching { service.start() }.exceptionOrNull() is SQLException)
    }
  }

  test("shutdown closes the pool") {
    withTempDir { dir ->
      val pool = TestPool(JdbcGenericConfig(jdbcUrl = "jdbc:sqlite:${dir.resolve("close.db")}"))
      val service = JdbcDataSourceService(pool.config) { pool }
      service.start()
      service.shutdown()
      assert(pool.closed)
    }
  }

  test("dataSource is the pool's") {
    withTempDir { dir ->
      val pool = TestPool(JdbcGenericConfig(jdbcUrl = "jdbc:sqlite:${dir.resolve("ds.db")}"))
      assert(JdbcDataSourceService(pool.config) { pool }.dataSource === pool.dataSource)
    }
  }
}

/** The least a [ConnectionPool] can be: a new driver connection per request, no pooling. */
private class TestPool(val config: JdbcConfig) : ConnectionPool {
  var connectionsOpened = 0
  var closed = false

  override val dataSource: DataSource = object : DataSource {
    override fun getConnection(): Connection {
      connectionsOpened++
      return DriverManager.getConnection(config.jdbcUrl)
    }

    override fun getConnection(username: String?, password: String?) = throw UnsupportedOperationException()
    override fun getLogWriter(): PrintWriter? = null
    override fun setLogWriter(out: PrintWriter?) = Unit
    override fun setLoginTimeout(seconds: Int) = Unit
    override fun getLoginTimeout() = 0
    override fun getParentLogger(): Logger = throw UnsupportedOperationException()
    override fun <T : Any?> unwrap(iface: Class<T>?): T = throw UnsupportedOperationException()
    override fun isWrapperFor(iface: Class<*>?) = false
  }

  override fun close() {
    closed = true
  }
}

@OptIn(ExperimentalPathApi::class)
private fun withTempDir(block: (Path) -> Unit) {
  val dir = createTempDirectory("boot-db-jdbc")
  try {
    block(dir)
  } finally {
    dir.deleteRecursively()
  }
}
