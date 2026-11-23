plugins {
  kotlin("jvm")
  application
}

kotlin {
  jvmToolchain(25)
}

dependencies {
  implementation(project(":examples:server-ktor-db:common"))
  // consume each Bootable module's koin flavor. In-repo that's the module's `koinElements` configuration;
  // a real downstream consumer writes implementation("com.github.rocketraman.bootable:boot-koin:<v>") etc.
  implementation(project(path = ":boot", configuration = "koinElements"))
  implementation(project(path = ":boot-config-hoplite", configuration = "koinElements"))
  implementation(project(":boot-logging-log4j2"))
  // the two database choices: a driver (SQLite) and a pool (HikariCP)
  implementation(project(path = ":boot-db-jdbc-sqlite", configuration = "koinElements"))
  implementation(project(path = ":boot-db-pool-hikari", configuration = "koinElements"))
  // each query traced, under the request's span, through the application's OpenTelemetry
  implementation(project(path = ":boot-db-jdbc-otel", configuration = "koinElements"))
  // migrations, and Exposed with schema verification
  implementation(project(path = ":boot-db-flyway", configuration = "koinElements"))
  implementation(project(path = ":boot-db-exposed", configuration = "koinElements"))
  // telemetry: the OpenTelemetry SDK, Prometheus export, and the Ktor instrumentation that serves its scrape
  implementation(project(path = ":boot-telemetry-otel", configuration = "koinElements"))
  implementation(project(path = ":boot-telemetry-otel-prometheus", configuration = "koinElements"))
  implementation(project(path = ":boot-server-http-ktor-otel", configuration = "koinElements"))
  // the health probes, served by the server
  implementation(project(path = ":boot-server-http-ktor", configuration = "koinElements"))
  // the server's engine, CIO, for the listeners of their own of the health probes and the scrape, when their hostPort
  // is set
  implementation(project(path = ":boot-server-http-ktor-cio", configuration = "koinElements"))
  // HikariCP's pool metrics, through the application's OpenTelemetry
  implementation(project(path = ":boot-db-pool-hikari-otel", configuration = "koinElements"))
  // the HTTP clients, each request traced within its caller's trace
  implementation(project(path = ":boot-client-http-ktor", configuration = "koinElements"))
  implementation(project(path = ":boot-client-http-ktor-otel", configuration = "koinElements"))
  // the entry count's loop, gated on the leader election, and traced through the OpenTelemetry SDK, a trace for each
  // run
  implementation(project(path = ":boot-periodic", configuration = "koinElements"))
  implementation(project(path = ":boot-leader-k8s", configuration = "koinElements"))
  // the entry digest, a recurring db-scheduler task, and traced through the OpenTelemetry SDK, a trace for each
  // execution
  implementation(project(path = ":boot-scheduler-db", configuration = "koinElements"))
}

application {
  mainClass.set("AppKt")
}
