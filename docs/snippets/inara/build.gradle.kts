plugins {
  kotlin("jvm")
  // Inara is a KSP processor: it generates the builders of the snippets' @DIModules
  id("com.google.devtools.ksp")
}

// The snippets, wired with Inara: each module's inara flavor. In-repo that's the module's `inaraElements`
// configuration; a downstream consumer depends on the module's `-inara` artifact.
dependencies {
  implementation(project(":docs:snippets:common"))
  implementation(project(path = ":boot", configuration = "inaraElements"))
  implementation(project(path = ":boot-config-hoplite", configuration = "inaraElements"))
  implementation(project(path = ":boot-env", configuration = "inaraElements"))
  implementation(project(path = ":boot-db-jdbc-sqlite", configuration = "inaraElements"))
  implementation(project(path = ":boot-db-pool-hikari", configuration = "inaraElements"))
  implementation(project(path = ":boot-db-flyway", configuration = "inaraElements"))
  implementation(project(path = ":boot-db-exposed", configuration = "inaraElements"))
  implementation(project(path = ":boot-db-jdbc-otel", configuration = "inaraElements"))
  implementation(project(path = ":boot-db-pool-hikari-otel", configuration = "inaraElements"))
  implementation(project(path = ":boot-telemetry-otel", configuration = "inaraElements"))
  implementation(project(path = ":boot-telemetry-otel-prometheus", configuration = "inaraElements"))
  implementation(project(path = ":boot-telemetry-otel-signoz", configuration = "inaraElements"))
  implementation(project(path = ":boot-telemetry-otel-gcp", configuration = "inaraElements"))
  implementation(project(path = ":boot-telemetry-otel-micrometer-bridge", configuration = "inaraElements"))
  implementation(project(path = ":boot-server-http-ktor", configuration = "inaraElements"))
  implementation(project(path = ":boot-server-http-ktor-otel", configuration = "inaraElements"))
  implementation(project(path = ":boot-client-http-ktor", configuration = "inaraElements"))
  implementation(project(path = ":boot-client-http-ktor-otel", configuration = "inaraElements"))
  implementation(project(path = ":boot-periodic", configuration = "inaraElements"))
  implementation(project(path = ":boot-leader-common", configuration = "inaraElements"))
  implementation(project(path = ":boot-leader-k8s", configuration = "inaraElements"))
  implementation(project(path = ":boot-scheduler-db", configuration = "inaraElements"))
  // modules without DI flavours
  implementation(project(":boot-server-http-ktor-cio"))
  // the engine of the probes' and the scrape's own listeners
  implementation(project(path = ":boot-server-http-ktor-cio", configuration = "inaraElements"))
  implementation(project(":boot-server-http-ktor-netty"))
  implementation(project(":boot-client-http-ktor-cio"))
  implementation(project(":boot-client-http-ktor-java"))
  implementation(project(":boot-logging-log4j2"))
  implementation(project(path = ":boot-test-otel", configuration = "inaraElements"))
  implementation(project(":boot-test"))
  ksp(libs.inara.processor)
}

