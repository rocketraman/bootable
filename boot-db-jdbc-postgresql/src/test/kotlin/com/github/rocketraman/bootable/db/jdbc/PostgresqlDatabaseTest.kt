package com.github.rocketraman.bootable.db.jdbc

import com.github.rocketraman.bootable.db.flyway.FlywayMigrationService
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.disable
import de.infix.testBalloon.framework.core.testSuite
import org.testcontainers.DockerClientFactory
import org.testcontainers.postgresql.PostgreSQLContainer
import javax.sql.DataSource

/**
 * Against a real PostgreSQL server, in a container. Skipped where Docker is unavailable, except on CI, where it must
 * run. With Podman, point `DOCKER_HOST` at its socket, e.g. `unix:///run/user/1000/podman/podman.sock`.
 */
val PostgresqlDatabaseTest by testSuite(
  testConfig = if (System.getenv("CI") != null || DockerClientFactory.instance().isDockerAvailable) TestConfig
  else TestConfig.disable()
) {
  val server = testFixture {
    PostgreSQLContainer("postgres:18-alpine").apply { start() }
  } closeWith { stop() }

  suspend fun config() = server().let {
    PostgresqlConfig(
      host = it.host,
      port = it.firstMappedPort,
      database = it.databaseName,
      username = it.username,
      password = it.password,
      applicationName = "bootable-test",
    )
  }

  test("connections carry the configured settings") {
    JdbcDataSourceService(config().copy(currentSchema = "pg_catalog"), HikariConnectionPoolFactory()).use {
      assert(it.queryString("SELECT current_setting('application_name')") == "bootable-test")
      assert(it.queryString("SELECT current_schema()") == "pg_catalog")
    }
  }

  test("flyway migrates the database") {
    JdbcDataSourceService(config(), HikariConnectionPoolFactory()).use {
      FlywayMigrationService(it).start()
      assert(it.queryString("SELECT message FROM greeting WHERE id = 1") == "hello")
    }
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

private fun DataSource.queryString(sql: String): String = connection.use { connection ->
  connection.createStatement().use { statement ->
    statement.executeQuery(sql).use { rs ->
      rs.next()
      rs.getString(1)
    }
  }
}
