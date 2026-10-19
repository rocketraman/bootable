package com.github.rocketraman.bootable.db.jdbc

import de.infix.testBalloon.framework.core.testSuite
import kotlin.time.Duration.Companion.seconds

val PostgresqlConfigTest by testSuite {
  test("the URL addresses the database") {
    assert(PostgresqlConfig(database = "app").jdbcUrl == "jdbc:postgresql://localhost:5432/app")
    assert(PostgresqlConfig(host = "db", port = 6543, database = "app").jdbcUrl == "jdbc:postgresql://db:6543/app")
  }

  test("an IPv6 host is bracketed") {
    assert(PostgresqlConfig(host = "::1", database = "app").jdbcUrl == "jdbc:postgresql://[::1]:5432/app")
    assert(PostgresqlConfig(host = "[::1]", database = "app").jdbcUrl == "jdbc:postgresql://[::1]:5432/app")
  }

  test("unset settings keep the driver's defaults") {
    assert(PostgresqlConfig(database = "app").dataSourceProperties.isEmpty())
  }

  test("settings become the driver's connection properties") {
    val config = PostgresqlConfig(
      database = "app",
      currentSchema = "app,public",
      applicationName = "my-app",
      sslMode = PostgresqlSslMode.VERIFY_FULL,
      connectTimeout = 3.seconds,
      socketTimeout = 60.seconds,
      reWriteBatchedInserts = true,
    )
    assert(
      config.dataSourceProperties == mapOf(
        "currentSchema" to "app,public",
        "ApplicationName" to "my-app",
        "sslmode" to "verify-full",
        "connectTimeout" to "3",
        "socketTimeout" to "60",
        "reWriteBatchedInserts" to "true",
      )
    )
  }

  test("toString masks the password") {
    val string = PostgresqlConfig(database = "app", username = "app", password = "secret").toString()
    assert("secret" !in string && "password=****" in string && "username=app" in string)
  }
}
