package com.github.rocketraman.bootable.db.jdbc

import com.github.rocketraman.bootable.db.flyway.FlywayMigrationService
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.disable
import de.infix.testBalloon.framework.core.testSuite
import org.testcontainers.DockerClientFactory
import org.testcontainers.mysql.MySQLContainer
import javax.sql.DataSource

/**
 * Against a real MySQL server, in a container. Skipped where Docker is unavailable, except on CI, where it must
 * run. With Podman, point `DOCKER_HOST` at its socket, e.g. `unix:///run/user/1000/podman/podman.sock`.
 */
val MysqlDatabaseTest by testSuite(
  testConfig = if (System.getenv("CI") != null || DockerClientFactory.instance().isDockerAvailable) TestConfig
  else TestConfig.disable()
) {
  val server = testFixture {
    MySQLContainer("mysql:8.4").apply { start() }
  } closeWith { stop() }

  suspend fun config() = server().let {
    MysqlConfig(
      host = it.host,
      port = it.firstMappedPort,
      database = it.databaseName,
      username = it.username,
      password = it.password,
    )
  }

  test("connections carry the configured settings") {
    JdbcDataSourceService(config().copy(sslMode = MysqlSslMode.REQUIRED), HikariConnectionPoolFactory()).use {
      // the second column of SHOW STATUS is the value: a cipher only on an SSL connection
      assert(it.queryString("SHOW SESSION STATUS LIKE 'Ssl_cipher'", column = 2).isNotEmpty())
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

private fun DataSource.queryString(sql: String, column: Int = 1): String = connection.use { connection ->
  connection.createStatement().use { statement ->
    statement.executeQuery(sql).use { rs ->
      rs.next()
      rs.getString(column)
    }
  }
}
