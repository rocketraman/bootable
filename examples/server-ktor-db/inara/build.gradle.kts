plugins {
  kotlin("jvm")
  // Inara is a KSP processor: it generates the builder for the application's @DIModule
  id("com.google.devtools.ksp")
  application
}

kotlin {
  jvmToolchain(25)
}

dependencies {
  implementation(project(":examples:server-ktor-db:common"))
  // consume each Bootable module's inara flavor. In-repo that's the module's `inaraElements` configuration;
  // a real downstream consumer writes implementation("com.github.rocketraman.bootable:boot-inara:<v>") etc.
  implementation(project(path = ":boot", configuration = "inaraElements"))
  implementation(project(path = ":boot-config-hoplite", configuration = "inaraElements"))
  implementation(project(":boot-logging-log4j2"))
  // the two database choices: a driver (SQLite) and a pool (HikariCP)
  implementation(project(path = ":boot-db-jdbc-sqlite", configuration = "inaraElements"))
  implementation(project(path = ":boot-db-pool-hikari", configuration = "inaraElements"))
  // each query traced, under the request's span, through the application's OpenTelemetry
  implementation(project(path = ":boot-db-jdbc-otel", configuration = "inaraElements"))
  // migrations, and Exposed with schema verification
  implementation(project(path = ":boot-db-flyway", configuration = "inaraElements"))
  implementation(project(path = ":boot-db-exposed", configuration = "inaraElements"))
  // telemetry: the OpenTelemetry SDK, Prometheus export, and the Ktor instrumentation that serves its scrape
  implementation(project(path = ":boot-telemetry-otel", configuration = "inaraElements"))
  implementation(project(path = ":boot-telemetry-otel-prometheus", configuration = "inaraElements"))
  implementation(project(path = ":boot-server-http-ktor-otel", configuration = "inaraElements"))
  // the health probes, served by the server
  implementation(project(path = ":boot-server-http-ktor", configuration = "inaraElements"))
  // the server's engine, CIO, for the listeners of their own of the health probes and the scrape, when their hostPort
  // is set
  implementation(project(path = ":boot-server-http-ktor-cio", configuration = "inaraElements"))
  // HikariCP's pool metrics, through the application's OpenTelemetry
  implementation(project(path = ":boot-db-pool-hikari-otel", configuration = "inaraElements"))
  // the HTTP clients, each request traced within its caller's trace
  implementation(project(path = ":boot-client-http-ktor", configuration = "inaraElements"))
  implementation(project(path = ":boot-client-http-ktor-otel", configuration = "inaraElements"))
  // the entry count's loop, gated on the leader election, and traced through the OpenTelemetry SDK, a trace for each
  // run
  implementation(project(path = ":boot-periodic", configuration = "inaraElements"))
  implementation(project(path = ":boot-leader-k8s", configuration = "inaraElements"))
  // the entry digest, a recurring db-scheduler task, and traced through the OpenTelemetry SDK, a trace for each
  // execution
  implementation(project(path = ":boot-scheduler-db", configuration = "inaraElements"))
  ksp(libs.inara.processor)
}

application {
  mainClass.set("AppKt")
}
