package com.github.rocketraman.bootable.db.jdbc

import de.infix.testBalloon.framework.core.testSuite
import kotlin.time.Duration.Companion.seconds

private val prepStmtCache = mapOf(
  "cachePrepStmts" to "true",
  "prepStmtCacheSize" to "250",
  "prepStmtCacheSqlLimit" to "2048",
)

val MysqlConfigTest by testSuite {
  test("the URL addresses the database") {
    assert(MysqlConfig(database = "app").jdbcUrl == "jdbc:mysql://localhost:3306/app")
    assert(MysqlConfig(host = "db", port = 3307, database = "app").jdbcUrl == "jdbc:mysql://db:3307/app")
  }

  test("an IPv6 host is bracketed") {
    assert(MysqlConfig(host = "::1", database = "app").jdbcUrl == "jdbc:mysql://[::1]:3306/app")
    assert(MysqlConfig(host = "[::1]", database = "app").jdbcUrl == "jdbc:mysql://[::1]:3306/app")
  }

  test("by default, only the prepared statement cache is set") {
    assert(MysqlConfig(database = "app").dataSourceProperties == prepStmtCache)
  }

  test("turning off the prepared statement cache drops its sizes") {
    assert(MysqlConfig(database = "app", cachePrepStmts = false).dataSourceProperties == mapOf("cachePrepStmts" to "false"))
  }

  test("settings become the driver's connection properties") {
    val config = MysqlConfig(
      database = "app",
      sslMode = MysqlSslMode.VERIFY_IDENTITY,
      allowPublicKeyRetrieval = true,
      connectTimeout = 3.seconds,
      socketTimeout = 60.seconds,
      rewriteBatchedStatements = true,
    )
    assert(
      config.dataSourceProperties == prepStmtCache + mapOf(
        "sslMode" to "VERIFY_IDENTITY",
        "allowPublicKeyRetrieval" to "true",
        "connectTimeout" to "3000",
        "socketTimeout" to "60000",
        "rewriteBatchedStatements" to "true",
      )
    )
  }

  test("toString masks the password") {
    val string = MysqlConfig(database = "app", username = "app", password = "secret").toString()
    assert("secret" !in string && "password=****" in string && "username=app" in string)
  }
}
