plugins {
  kotlin("jvm")
  id("dev.zacsweers.metro")
}

// The Web / HTTP snippets (the Ktor server, its health probes, the Ktor client), wired with Metro: each module's
// metro flavor.
// In-repo that's the module's `metroElements` configuration; a downstream consumer depends on the module's
// `-metro` artifact.
// A project of its own, with only this function's modules, as an application has: a -metro module's bindings join
// every graph in the compilation, so another function's modules would need bindings these snippets don't have.
dependencies {
  implementation(project(":docs:snippets:common"))
  implementation(project(path = ":boot", configuration = "metroElements"))
  implementation(project(path = ":boot-config-hoplite", configuration = "metroElements"))
  implementation(project(path = ":boot-server-http-ktor", configuration = "metroElements"))
  implementation(project(path = ":boot-server-http-ktor-otel", configuration = "metroElements"))
  implementation(project(path = ":boot-client-http-ktor", configuration = "metroElements"))
  implementation(project(path = ":boot-client-http-ktor-otel", configuration = "metroElements"))
  implementation(project(path = ":boot-telemetry-otel", configuration = "metroElements"))
  // modules without DI flavours
  implementation(project(":boot-server-http-ktor-cio"))
  // the engine of the probes' and the scrape's own listeners
  implementation(project(path = ":boot-server-http-ktor-cio", configuration = "metroElements"))
  implementation(project(":boot-server-http-ktor-netty"))
  implementation(project(":boot-client-http-ktor-cio"))
  implementation(project(":boot-client-http-ktor-java"))
}
