plugins {
  kotlin("jvm")
}

kotlin {
  jvmToolchain(25)
}

// The server, repository, table, migrations, config and resources shared by the vanilla, kodein, metro, and inara apps. Only the
// plain Bootable modules here; each app adds the DI flavor it wires with.
dependencies {
  api(project(":boot"))
  // the server runs on Ktor's CIO engine; boot-server-http-ktor-netty is the other choice
  api(project(":boot-server-http-ktor-cio"))
  // the server traces and measures its requests, and serves the metrics scrape
  api(project(":boot-server-http-ktor-otel"))
  // the relay calls the server back through a lifecycle-managed HTTP client on Ktor's CIO engine, traced as a client
  // span within the request's trace
  api(project(":boot-client-http-ktor-cio"))
  api(project(":boot-client-http-ktor-otel"))
  implementation(project(":boot-db-exposed"))
  // the entry count, a periodic loop that only the leader runs, of the instances elected through a Kubernetes Lease
  api(project(":boot-periodic"))
  api(project(":boot-leader-k8s"))
  // the entry digest, a recurring db-scheduler task, persistent, and run once across the instances
  api(project(":boot-scheduler-db"))
  implementation(project(":boot-logging-log4j2"))

  implementation(libs.ktor.server.html.builder)
  implementation(libs.kotlinx.html)

  // the routes run against a real database: SQLite through a HikariCP pool, migrated by Flyway
  testImplementation(project(":boot-db-jdbc-sqlite"))
  testImplementation(project(":boot-db-pool-hikari"))
  testImplementation(project(":boot-db-flyway"))
  testImplementation(project(":boot-telemetry-otel-prometheus"))
  testImplementation(libs.ktor.server.test.host)
  // the server's log, captured
  testImplementation(project(":boot-test"))
}
