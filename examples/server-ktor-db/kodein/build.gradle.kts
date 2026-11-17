plugins {
  kotlin("jvm")
  application
}

kotlin {
  jvmToolchain(25)
}

dependencies {
  implementation(project(":examples:server-ktor-db:common"))
  // consume each Bootable module's kodein flavor. In-repo that's the module's `kodeinElements` configuration;
  // a real downstream consumer writes implementation("com.github.rocketraman.bootable:boot-kodein:<v>") etc.
  implementation(project(path = ":boot", configuration = "kodeinElements"))
  implementation(project(path = ":boot-config-hoplite", configuration = "kodeinElements"))
  implementation(project(":boot-logging-log4j2"))
  // the two database choices: a driver (SQLite) and a pool (HikariCP)
  implementation(project(path = ":boot-db-jdbc-sqlite", configuration = "kodeinElements"))
  implementation(project(path = ":boot-db-pool-hikari", configuration = "kodeinElements"))
  // each query traced, under the request's span, through the application's OpenTelemetry
  implementation(project(path = ":boot-db-jdbc-otel", configuration = "kodeinElements"))
  // migrations, and Exposed with schema verification
  implementation(project(path = ":boot-db-flyway", configuration = "kodeinElements"))
  implementation(project(path = ":boot-db-exposed", configuration = "kodeinElements"))
  // telemetry: the OpenTelemetry SDK, Prometheus export, and the Ktor instrumentation that serves its scrape
  implementation(project(path = ":boot-telemetry-otel", configuration = "kodeinElements"))
  implementation(project(path = ":boot-telemetry-otel-prometheus", configuration = "kodeinElements"))
  implementation(project(path = ":boot-server-http-ktor-otel", configuration = "kodeinElements"))
  // HikariCP's pool metrics, through the application's OpenTelemetry
  implementation(project(path = ":boot-db-pool-hikari-otel", configuration = "kodeinElements"))
}

application {
  mainClass.set("AppKt")
}
