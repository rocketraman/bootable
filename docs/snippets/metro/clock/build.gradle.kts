plugins {
  kotlin("jvm")
  id("dev.zacsweers.metro")
}

// The Clock snippets, wired with Metro: boot-clock's metro flavor.
// In-repo that's the module's `metroElements` configuration; a downstream consumer depends on the module's
// `-metro` artifact.
// A project of its own, with only this function's modules, as an application has: a -metro module's bindings join
// every graph in the compilation, so another function's modules would need bindings these snippets don't have.
// The test helpers are on the main classpath here, so the snippets compile with the rest; an application adds them
// as test dependencies.
dependencies {
  implementation(project(":docs:snippets:common"))
  implementation(project(path = ":boot", configuration = "metroElements"))
  implementation(project(path = ":boot-clock", configuration = "metroElements"))
  // modules without DI flavours
  implementation(project(":boot-test"))
}
