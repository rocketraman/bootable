plugins {
  kotlin("jvm")
  id("dev.zacsweers.metro")
}

// The database snippets, wired with Metro: each module's metro flavor. In-repo that's the module's `metroElements`
// configuration; a downstream consumer depends on the module's `-metro` artifact.
// A project of its own, with only the database's modules, as an application has: a -metro module's bindings join
// every graph in the compilation, so another function's modules would need bindings these snippets don't have.
dependencies {
  implementation(project(":docs:snippets:common"))
  implementation(project(path = ":boot", configuration = "metroElements"))
  implementation(project(path = ":boot-config-hoplite", configuration = "metroElements"))
  implementation(project(path = ":boot-db-jdbc-sqlite", configuration = "metroElements"))
  implementation(project(path = ":boot-db-pool-hikari", configuration = "metroElements"))
  implementation(project(path = ":boot-db-flyway", configuration = "metroElements"))
  implementation(project(path = ":boot-db-exposed", configuration = "metroElements"))
  implementation(project(path = ":boot-db-jdbc-otel", configuration = "metroElements"))
  implementation(project(path = ":boot-db-pool-hikari-otel", configuration = "metroElements"))
  implementation(project(path = ":boot-telemetry-otel", configuration = "metroElements"))
}
