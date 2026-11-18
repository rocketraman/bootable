package com.github.rocketraman.bootable.scheduler.db

import com.github.rocketraman.bootable.config.Masked
import com.github.rocketraman.bootable.db.jdbc.MysqlConfig
import com.github.rocketraman.bootable.db.jdbc.PostgresqlConfig
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.disable
import de.infix.testBalloon.framework.core.testSuite
import org.testcontainers.DockerClientFactory
import org.testcontainers.mysql.MySQLContainer
import org.testcontainers.postgresql.PostgreSQLContainer

/**
 * Against real PostgreSQL and MySQL servers, in containers, migrated by this module's migrations. Skipped where Docker
 * is unavailable, except on CI, where they must run. With Podman, point `DOCKER_HOST` at its socket, e.g.
 * `unix:///run/user/1000/podman/podman.sock`.
 */
val ContainerDatabaseTest by testSuite(
  testConfig = if (System.getenv("CI") != null || DockerClientFactory.instance().isDockerAvailable) TestConfig
  else TestConfig.disable()
) {
  val postgres = testFixture {
    PostgreSQLContainer("postgres:18-alpine").apply { start() }
  } closeWith { stop() }

  val mysql = testFixture {
    MySQLContainer("mysql:8.4").apply { start() }
  } closeWith { stop() }

  test("runs recurring and one-time tasks on PostgreSQL, with lock-and-fetch") {
    val server = postgres()
    val config = PostgresqlConfig(
      host = server.host,
      port = server.firstMappedPort,
      database = server.databaseName,
      username = server.username,
      password = Masked(server.password),
    )
    withMigratedDatabase(config, "postgresql") { dataSource ->
      checkTasksRun(dataSource, config.jdbcUrl, testSchedulerConfig.copy(pollingStrategy = DbSchedulerPollingStrategy.LockAndFetch))
    }
  }

  test("runs recurring and one-time tasks on MySQL 8, with lock-and-fetch") {
    val server = mysql()
    val config = MysqlConfig(
      host = server.host,
      port = server.firstMappedPort,
      database = server.databaseName,
      username = server.username,
      password = Masked(server.password),
    )
    withMigratedDatabase(config, "mysql") { dataSource ->
      checkTasksRun(dataSource, config.jdbcUrl, testSchedulerConfig.copy(pollingStrategy = DbSchedulerPollingStrategy.LockAndFetch))
    }
  }
}
