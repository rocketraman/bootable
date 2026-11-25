plugins {
  kotlin("jvm")
}

// The snippets, wired with Kodein: each module's kodein flavor. In-repo that's the module's `kodeinElements`
// configuration; a downstream consumer depends on the module's `-kodein` artifact.
dependencies {
  implementation(project(":docs:snippets:common"))
  implementation(project(path = ":boot", configuration = "kodeinElements"))
  implementation(project(path = ":boot-clock", configuration = "kodeinElements"))
  implementation(project(path = ":boot-config-hoplite", configuration = "kodeinElements"))
  implementation(project(path = ":boot-env", configuration = "kodeinElements"))
  implementation(project(path = ":boot-db-jdbc-sqlite", configuration = "kodeinElements"))
  implementation(project(path = ":boot-db-pool-hikari", configuration = "kodeinElements"))
  implementation(project(path = ":boot-db-flyway", configuration = "kodeinElements"))
  implementation(project(path = ":boot-db-exposed", configuration = "kodeinElements"))
  implementation(project(path = ":boot-db-jdbc-otel", configuration = "kodeinElements"))
  implementation(project(path = ":boot-db-pool-hikari-otel", configuration = "kodeinElements"))
  implementation(project(path = ":boot-telemetry-otel", configuration = "kodeinElements"))
  implementation(project(path = ":boot-telemetry-otel-prometheus", configuration = "kodeinElements"))
  implementation(project(path = ":boot-telemetry-otel-signoz", configuration = "kodeinElements"))
  implementation(project(path = ":boot-telemetry-otel-gcp", configuration = "kodeinElements"))
  implementation(project(path = ":boot-telemetry-otel-micrometer-bridge", configuration = "kodeinElements"))
  implementation(project(path = ":boot-server-http-ktor", configuration = "kodeinElements"))
  implementation(project(path = ":boot-server-http-ktor-otel", configuration = "kodeinElements"))
  implementation(project(path = ":boot-client-http-ktor", configuration = "kodeinElements"))
  implementation(project(path = ":boot-client-http-ktor-otel", configuration = "kodeinElements"))
  implementation(project(path = ":boot-periodic", configuration = "kodeinElements"))
  implementation(project(path = ":boot-leader-k8s", configuration = "kodeinElements"))
  implementation(project(path = ":boot-scheduler-db", configuration = "kodeinElements"))
  implementation(project(path = ":boot-server-grpc", configuration = "kodeinElements"))
  implementation(project(path = ":boot-server-grpc-otel", configuration = "kodeinElements"))
  // modules without DI flavours
  implementation(project(":boot-server-http-ktor-cio"))
  // the engine of the probes' and the scrape's own listeners
  implementation(project(path = ":boot-server-http-ktor-cio", configuration = "kodeinElements"))
  implementation(project(":boot-server-http-ktor-netty"))
  implementation(project(":boot-client-http-ktor-cio"))
  implementation(project(":boot-client-http-ktor-java"))
  implementation(project(":boot-leader-common"))
  implementation(project(":boot-logging-log4j2"))
  implementation(project(path = ":boot-test-otel", configuration = "kodeinElements"))
  implementation(project(":boot-test"))
}

