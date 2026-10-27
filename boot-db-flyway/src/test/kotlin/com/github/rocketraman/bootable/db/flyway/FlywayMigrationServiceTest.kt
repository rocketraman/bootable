package com.github.rocketraman.bootable.db.flyway

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.StartOrder
import com.github.rocketraman.bootable.boot.startPlan
import com.github.rocketraman.bootable.db.jdbc.HikariConnectionPoolFactory
import com.github.rocketraman.bootable.db.jdbc.JdbcDataSourceService
import com.github.rocketraman.bootable.db.jdbc.SqliteConfig
import de.infix.testBalloon.framework.core.testSuite
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.api.FlywayException
import java.nio.file.Path
import javax.sql.DataSource
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively

val FlywayMigrationServiceTest by testSuite {
  test("start applies the pending migrations") {
    withDatabase { dataSource ->
      FlywayMigrationService(dataSource).start()

      assert(dataSource.query("SELECT text FROM greeting WHERE id = 1") == listOf("hello"))
      assert(dataSource.query("SELECT version FROM flyway_schema_history ORDER BY installed_rank") == listOf("1", "2"))
    }
  }

  test("a second start finds nothing to do") {
    withDatabase { dataSource ->
      FlywayMigrationService(dataSource).start()
      FlywayMigrationService(dataSource).start()

      assert(dataSource.query("SELECT count(*) FROM flyway_schema_history") == listOf("2"))
    }
  }

  test("creating the service opens no connection") {
    withTempDir { dir ->
      val database = JdbcDataSourceService(SqliteConfig(path = dir.resolve("lazy.db").toString()), HikariConnectionPoolFactory())
      try {
        FlywayMigrationService(database.dataSource)
        assert((database.dataSource as HikariDataSource).hikariPoolMXBean == null)
      } finally {
        database.shutdown()
      }
    }
  }

  test("typed settings are applied") {
    withDatabase { dataSource ->
      val config = FlywayConfig(
        locations = listOf("classpath:db/placeholder"),
        table = "schema_history",
        placeholders = mapOf("greeting" to "hi"),
        baselineOnMigrate = true,
        baselineVersion = "0",
        validateOnMigrate = false,
        validateMigrationNaming = true,
        outOfOrder = true,
        target = "1",
        cleanDisabled = true,
        failOnMissingLocations = true,
      )
      val service = FlywayMigrationService(dataSource, config)
      with(service.flyway.configuration) {
        assert(locations.map { it.descriptor } == listOf("classpath:db/placeholder"))
        assert(table == "schema_history")
        assert(isBaselineOnMigrate)
        assert(baselineVersion.version == "0")
        assert(!isValidateOnMigrate)
        assert(isValidateMigrationNaming)
        assert(isOutOfOrder)
        assert(target.version == "1")
        assert(isCleanDisabled)
        assert(isFailOnMissingLocations)
      }

      service.start()
      assert(dataSource.query("SELECT text FROM greeting WHERE id = 1") == listOf("hi"))
      assert(dataSource.query("SELECT version FROM schema_history ORDER BY installed_rank") == listOf("1"))
    }
  }

  test("the customizer runs after the typed settings") {
    withDatabase { dataSource ->
      val service = FlywayMigrationService(dataSource, FlywayConfig(table = "typed")) { it.table("customized") }
      service.start()
      assert(dataSource.query("SELECT count(*) FROM customized") == listOf("2"))
    }
  }

  test("a failed migration fails the start") {
    withDatabase { dataSource ->
      val service = FlywayMigrationService(dataSource, FlywayConfig(locations = listOf("classpath:db/broken")))
      assert(runCatching { service.start() }.exceptionOrNull() is FlywayException)
    }
  }

  test("a missing location fails the start when failOnMissingLocations is on") {
    withDatabase { dataSource ->
      val config = FlywayConfig(locations = listOf("classpath:db/missing"), failOnMissingLocations = true)
      assert(runCatching { FlywayMigrationService(dataSource, config).start() }.exceptionOrNull() is FlywayException)
    }
  }

  test("starts after the pool, and before the application's services") {
    withTempDir { dir ->
      // nothing starts, so nothing opens a connection
      val pool = JdbcDataSourceService(SqliteConfig(path = dir.resolve("plan.db").toString()), HikariConnectionPoolFactory())
      try {
        val migrations = FlywayMigrationService(pool.dataSource)
        val app = object : AppService {}
        val plan = startPlan(setOf(app, migrations, pool), tiebreaker = StartOrder.Tiebreaker.Shuffled())
        assert(plan == listOf(pool, migrations, app))
      } finally {
        pool.shutdown()
      }
    }
  }
}

@OptIn(ExperimentalPathApi::class)
internal fun withTempDir(block: (Path) -> Unit) {
  val dir = createTempDirectory("boot-db-flyway")
  try {
    block(dir)
  } finally {
    dir.deleteRecursively()
  }
}

/** A started SQLite pool in a temporary directory. */
private fun withDatabase(block: (DataSource) -> Unit) = withTempDir { dir ->
  val database = JdbcDataSourceService(SqliteConfig(path = dir.resolve("test.db").toString()), HikariConnectionPoolFactory())
  database.start()
  try {
    block(database.dataSource)
  } finally {
    database.shutdown()
  }
}

/** The first column of every row. */
internal fun DataSource.query(sql: String): List<String> = connection.use { connection ->
  connection.createStatement().use { statement ->
    statement.executeQuery(sql).use { rs ->
      buildList { while (rs.next()) add(rs.getString(1)) }
    }
  }
}
