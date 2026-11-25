plugins {
  kotlin("jvm")
}

// The snippets, wired with Koin: each module's koin flavor. In-repo that's the module's `koinElements`
// configuration; a downstream consumer depends on the module's `-koin` artifact.
dependencies {
  implementation(project(":docs:snippets:common"))
  implementation(project(path = ":boot", configuration = "koinElements"))
  implementation(project(path = ":boot-config-hoplite", configuration = "koinElements"))
  implementation(project(path = ":boot-env", configuration = "koinElements"))
  implementation(project(path = ":boot-db-jdbc-sqlite", configuration = "koinElements"))
  implementation(project(path = ":boot-db-pool-hikari", configuration = "koinElements"))
  implementation(project(path = ":boot-db-flyway", configuration = "koinElements"))
  implementation(project(path = ":boot-db-exposed", configuration = "koinElements"))
  implementation(project(path = ":boot-db-jdbc-otel", configuration = "koinElements"))
  implementation(project(path = ":boot-db-pool-hikari-otel", configuration = "koinElements"))
  implementation(project(path = ":boot-telemetry-otel", configuration = "koinElements"))
  implementation(project(path = ":boot-telemetry-otel-prometheus", configuration = "koinElements"))
  implementation(project(path = ":boot-telemetry-otel-signoz", configuration = "koinElements"))
  implementation(project(path = ":boot-telemetry-otel-gcp", configuration = "koinElements"))
  implementation(project(path = ":boot-telemetry-otel-micrometer-bridge", configuration = "koinElements"))
  implementation(project(path = ":boot-server-http-ktor", configuration = "koinElements"))
  implementation(project(path = ":boot-server-http-ktor-otel", configuration = "koinElements"))
  implementation(project(path = ":boot-client-http-ktor", configuration = "koinElements"))
  implementation(project(path = ":boot-client-http-ktor-otel", configuration = "koinElements"))
  implementation(project(path = ":boot-periodic", configuration = "koinElements"))
  implementation(project(path = ":boot-leader-k8s", configuration = "koinElements"))
  implementation(project(path = ":boot-scheduler-db", configuration = "koinElements"))
  implementation(project(path = ":boot-server-grpc", configuration = "koinElements"))
  implementation(project(path = ":boot-server-grpc-otel", configuration = "koinElements"))
  // modules without DI flavours
  implementation(project(":boot-server-http-ktor-cio"))
  // the engine of the probes' and the scrape's own listeners
  implementation(project(path = ":boot-server-http-ktor-cio", configuration = "koinElements"))
  implementation(project(":boot-server-http-ktor-netty"))
  implementation(project(":boot-client-http-ktor-cio"))
  implementation(project(":boot-client-http-ktor-java"))
  implementation(project(":boot-leader-common"))
  implementation(project(":boot-logging-log4j2"))
  implementation(project(path = ":boot-test-otel", configuration = "koinElements"))
  implementation(project(":boot-test"))
}

