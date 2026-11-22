plugins {
  kotlin("jvm")
}

// The snippets, wired by hand: the plain coordinate of each module.
dependencies {
  implementation(project(":docs:snippets:common"))
  implementation(project(":boot-config-hoplite"))
  implementation(project(":boot-env"))
  implementation(project(":boot-db-jdbc-sqlite"))
  implementation(project(":boot-db-pool-hikari"))
  implementation(project(":boot-db-flyway"))
  implementation(project(":boot-db-exposed"))
  implementation(project(":boot-db-jdbc-otel"))
  implementation(project(":boot-db-pool-hikari-otel"))
  implementation(project(":boot-telemetry-otel"))
  implementation(project(":boot-telemetry-otel-prometheus"))
  implementation(project(":boot-telemetry-otel-signoz"))
  implementation(project(":boot-telemetry-otel-gcp"))
  implementation(project(":boot-telemetry-otel-micrometer-bridge"))
  implementation(project(":boot-server-http-ktor"))
  implementation(project(":boot-server-http-ktor-otel"))
  implementation(project(":boot-client-http-ktor"))
  implementation(project(":boot-client-http-ktor-otel"))
  implementation(project(":boot-periodic"))
  implementation(project(":boot-leader-k8s"))
  implementation(project(":boot-scheduler-db"))
  implementation(project(":boot-server-http-ktor-cio"))
  implementation(project(":boot-server-http-ktor-netty"))
  implementation(project(":boot-client-http-ktor-cio"))
  implementation(project(":boot-client-http-ktor-java"))
  implementation(project(":boot-leader-common"))
  implementation(project(":boot-logging-log4j2"))
  implementation(project(":boot-test-otel"))
  implementation(project(":boot-test"))
}

