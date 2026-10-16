package com.github.rocketraman.bootable.db.jdbc

import de.infix.testBalloon.framework.core.testSuite
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.time.Duration.Companion.seconds

private data class TestJdbcConfig(
  override val jdbcUrl: String = "jdbc:sqlite:x.db",
  override val maxConnectionsHint: Int? = null,
  override val dataSourceProperties: Map<String, String> = emptyMap(),
) : JdbcConfig {
  override val driverClassName: String? get() = null
  override val username: String? get() = null
  override val password: String? get() = null
}

val HikariConnectionPoolFactoryTest by testSuite {
  testSuite("pool size") {
    fun sized(hint: Int?, maximumPoolSize: Int?): Pair<Int, List<String>> {
      val warnings = mutableListOf<String>()
      val hikari = hikariConfig(TestJdbcConfig(maxConnectionsHint = hint), HikariPoolConfig(maximumPoolSize), warnings::add)
      return hikari.maximumPoolSize to warnings
    }

    test("comes from the pool config, with a warning when above the driver's hint") {
      val (size, warnings) = sized(hint = 1, maximumPoolSize = 4)
      assert(size == 4)
      assert(warnings.single().contains("maximumPoolSize 4 exceeds the database's maxConnectionsHint of 1"))
    }

    test("comes from the pool config, silently when within the driver's hint") {
      assert(sized(hint = 8, maximumPoolSize = 4) == (4 to emptyList<String>()))
      assert(sized(hint = 4, maximumPoolSize = 4) == (4 to emptyList<String>()))
    }

    test("comes from the pool config, silently when the driver gives no hint") {
      assert(sized(hint = null, maximumPoolSize = 40) == (40 to emptyList<String>()))
    }

    test("defaults to the driver's hint") {
      assert(sized(hint = 1, maximumPoolSize = null) == (1 to emptyList<String>()))
    }

    test("defaults to HikariCP's default without a hint") {
      assert(sized(hint = null, maximumPoolSize = null) == (10 to emptyList<String>()))
    }
  }

  test("typed settings are applied") {
    val hikari = hikariConfig(
      TestJdbcConfig(dataSourceProperties = mapOf("foreign_keys" to "true")),
      HikariPoolConfig(
        minimumIdle = 2,
        connectionTimeout = 3.seconds,
        idleTimeout = 60.seconds,
        maxLifetime = 600.seconds,
        poolName = "typed",
        keepaliveTime = 120.seconds,
        validationTimeout = 2.seconds,
        leakDetectionThreshold = 10.seconds,
        connectionInitSql = "SELECT 1",
        registerMbeans = true,
      ),
      warn = { error("unexpected warning: $it") },
    )
    assert(hikari.jdbcUrl == "jdbc:sqlite:x.db")
    assert(hikari.dataSourceProperties["foreign_keys"] == "true")
    assert(hikari.minimumIdle == 2)
    assert(hikari.connectionTimeout == 3_000L)
    assert(hikari.idleTimeout == 60_000L)
    assert(hikari.maxLifetime == 600_000L)
    assert(hikari.poolName == "typed")
    assert(hikari.keepaliveTime == 120_000L)
    assert(hikari.validationTimeout == 2_000L)
    assert(hikari.leakDetectionThreshold == 10_000L)
    assert(hikari.connectionInitSql == "SELECT 1")
    assert(hikari.isRegisterMbeans)
  }

  test("the customizer runs after the typed settings") {
    val pool = HikariConnectionPoolFactory(HikariPoolConfig(maximumPoolSize = 4)) { it.maximumPoolSize = 7 }
      .create(TestJdbcConfig())
    try {
      assert(pool.hikariDataSource.maximumPoolSize == 7)
    } finally {
      pool.close()
    }
  }

  test("no connection is opened until first use") {
    withTempDir { dir ->
      val pool = HikariConnectionPoolFactory().create(TestJdbcConfig(jdbcUrl = "jdbc:sqlite:${dir.resolve("lazy.db")}"))
      try {
        // HikariCP creates its internal pool, and its first connection, on the first getConnection()
        assert(pool.hikariDataSource.hikariPoolMXBean == null)
        pool.dataSource.connection.use { }
        assert(pool.hikariDataSource.hikariPoolMXBean != null)
      } finally {
        pool.close()
      }
    }
  }

  test("close closes the HikariCP pool") {
    withTempDir { dir ->
      val pool = HikariConnectionPoolFactory().create(TestJdbcConfig(jdbcUrl = "jdbc:sqlite:${dir.resolve("close.db")}"))
      pool.dataSource.connection.use { }
      pool.close()
      assert(pool.hikariDataSource.isClosed)
    }
  }
}

@OptIn(ExperimentalPathApi::class)
private fun withTempDir(block: (Path) -> Unit) {
  val dir = createTempDirectory("boot-db-pool-hikari")
  try {
    block(dir)
  } finally {
    dir.deleteRecursively()
  }
}
