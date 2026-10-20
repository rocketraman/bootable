plugins {
  kotlin("jvm")
}

kotlin {
  jvmToolchain(21)
}

// The server, repository, table, migrations, config and resources shared by the vanilla, kodein, and metro apps. Only the
// plain Bootable modules here; each app adds the DI flavor it wires with.
dependencies {
  api(project(":boot"))
  // the server runs on Ktor's CIO engine; boot-server-http-ktor-netty and -jetty are the other choices
  api(project(":boot-server-http-ktor-cio"))
  implementation(project(":boot-db-exposed"))
  implementation(project(":boot-logging-log4j2"))

  implementation(libs.ktor.server.html.builder)
  implementation(libs.kotlinx.html)

  // the routes run against a real database: SQLite through a HikariCP pool, migrated by Flyway
  testImplementation(project(":boot-db-jdbc-sqlite"))
  testImplementation(project(":boot-db-pool-hikari"))
  testImplementation(project(":boot-db-flyway"))
  testImplementation(libs.ktor.server.test.host)
}
