plugins {
  kotlin("jvm")
  application
}

kotlin {
  jvmToolchain(25)
}

dependencies {
  implementation(project(":examples:server-ktor-db:common"))
  implementation(project(":boot-config-hoplite"))
  implementation(project(":boot-logging-log4j2"))
  // the two database choices: a driver (SQLite) and a pool (HikariCP)
  implementation(project(":boot-db-jdbc-sqlite"))
  implementation(project(":boot-db-pool-hikari"))
  // migrations, and Exposed with schema verification
  implementation(project(":boot-db-flyway"))
  implementation(project(":boot-db-exposed"))
  // telemetry: the OpenTelemetry SDK, Prometheus export, and the Ktor instrumentation that serves its scrape
  implementation(project(":boot-telemetry-otel"))
  implementation(project(":boot-telemetry-otel-prometheus"))
  implementation(project(":boot-server-http-ktor-otel"))
  // HikariCP's pool metrics, through the application's OpenTelemetry
  implementation(project(":boot-db-pool-hikari-otel"))
}

application {
  mainClass.set("AppKt")
}
