plugins {
  kotlin("jvm")
  id("dev.zacsweers.metro")
}

// The Background work snippets (periodic work, leader election, scheduled tasks), wired with Metro: each
// module's metro flavor.
// In-repo that's the module's `metroElements` configuration; a downstream consumer depends on the module's
// `-metro` artifact.
// A project of its own, with only this function's modules, as an application has: a -metro module's bindings join
// every graph in the compilation, so another function's modules would need bindings these snippets don't have.
dependencies {
  implementation(project(":docs:snippets:common"))
  implementation(project(path = ":boot", configuration = "metroElements"))
  implementation(project(path = ":boot-config-hoplite", configuration = "metroElements"))
  implementation(project(path = ":boot-periodic", configuration = "metroElements"))
  implementation(project(path = ":boot-leader-k8s", configuration = "metroElements"))
  implementation(project(path = ":boot-scheduler-db", configuration = "metroElements"))
  implementation(project(path = ":boot-db-jdbc-sqlite", configuration = "metroElements"))
  implementation(project(path = ":boot-db-pool-hikari", configuration = "metroElements"))
  implementation(project(path = ":boot-db-flyway", configuration = "metroElements"))
  implementation(project(path = ":boot-telemetry-otel", configuration = "metroElements"))
  implementation(project(path = ":boot-client-http-ktor", configuration = "metroElements"))
  // modules without DI flavours
  implementation(project(":boot-leader-common"))
  implementation(project(":boot-client-http-ktor-cio"))
}
