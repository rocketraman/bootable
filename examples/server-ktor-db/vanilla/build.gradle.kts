plugins {
  kotlin("jvm")
  application
  // aotCache: a training run of the installed app, which writes its JVM AOT cache
  id("bootable.aot-cache")
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
  // each query traced, under the request's span, through the application's OpenTelemetry
  implementation(project(":boot-db-jdbc-otel"))
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

// The training run serves a few requests too, so the cache covers the request path, not just the start. It listens
// on a port of its own, and keeps its database under build/aot.
val trainingHostPort = "127.0.0.1:18001"

tasks.aotCache {
  environment.put("SERVER_HOSTPORT", trainingHostPort)
  // An OTLP endpoint, as in production, where the app exports its telemetry: the SDK then builds its OTLP
  // exporters, whose classes, about 760 of them, the cache would otherwise leave out. Nothing listens on it, so the
  // training exports nothing, and logs a failed export as it stops.
  environment.put("OTEL_EXPORTER_OTLP_ENDPOINT", "http://127.0.0.1:1")
  request("PUT", "http://$trainingHostPort/entries/trained", "by the AOT training run")
  request("GET", "http://$trainingHostPort/entries/trained")
  request("GET", "http://$trainingHostPort/entries")
  request("GET", "http://$trainingHostPort/")
  request("GET", "http://$trainingHostPort/metrics")
  request("DELETE", "http://$trainingHostPort/entries/trained")
  rounds = 20
}
