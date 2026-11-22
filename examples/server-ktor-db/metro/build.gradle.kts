plugins {
  kotlin("jvm")
  id("dev.zacsweers.metro")
  application
}

kotlin {
  jvmToolchain(25)
}

dependencies {
  implementation(project(":examples:server-ktor-db:common"))
  // consume each Bootable module's metro flavor. In-repo that's the module's `metroElements` configuration;
  // a real downstream consumer writes implementation("com.github.rocketraman.bootable:boot-metro:<v>") etc.
  implementation(project(path = ":boot", configuration = "metroElements"))
  implementation(project(path = ":boot-config-hoplite", configuration = "metroElements"))
  implementation(project(":boot-logging-log4j2"))
  // the two database choices: a driver (SQLite) and a pool (HikariCP)
  implementation(project(path = ":boot-db-jdbc-sqlite", configuration = "metroElements"))
  implementation(project(path = ":boot-db-pool-hikari", configuration = "metroElements"))
  // each query traced, under the request's span, through the application's OpenTelemetry
  implementation(project(path = ":boot-db-jdbc-otel", configuration = "metroElements"))
  // migrations, and Exposed with schema verification
  implementation(project(path = ":boot-db-flyway", configuration = "metroElements"))
  implementation(project(path = ":boot-db-exposed", configuration = "metroElements"))
  // telemetry: the OpenTelemetry SDK, Prometheus export, and the Ktor instrumentation that serves its scrape
  implementation(project(path = ":boot-telemetry-otel", configuration = "metroElements"))
  implementation(project(path = ":boot-telemetry-otel-prometheus", configuration = "metroElements"))
  implementation(project(path = ":boot-server-http-ktor-otel", configuration = "metroElements"))
  // the health probes, served by the server
  implementation(project(path = ":boot-server-http-ktor", configuration = "metroElements"))
  // the server's engine, CIO, for the listeners of their own of the health probes and the scrape, when their hostPort
  // is set
  implementation(project(path = ":boot-server-http-ktor-cio", configuration = "metroElements"))
  // HikariCP's pool metrics, through the application's OpenTelemetry
  implementation(project(path = ":boot-db-pool-hikari-otel", configuration = "metroElements"))
  // the HTTP clients, each request traced within its caller's trace
  implementation(project(path = ":boot-client-http-ktor", configuration = "metroElements"))
  implementation(project(path = ":boot-client-http-ktor-otel", configuration = "metroElements"))
  // the entry count's loop, gated on the leader election, and traced through the OpenTelemetry SDK, a trace for each
  // run
  implementation(project(path = ":boot-periodic", configuration = "metroElements"))
  implementation(project(path = ":boot-leader-k8s", configuration = "metroElements"))
  // the entry digest, a recurring db-scheduler task, and traced through the OpenTelemetry SDK, a trace for each
  // execution
  implementation(project(path = ":boot-scheduler-db", configuration = "metroElements"))
}

application {
  mainClass.set("AppKt")
}
