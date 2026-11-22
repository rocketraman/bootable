plugins {
  kotlin("jvm")
  id("dev.zacsweers.metro")
}

// The Concepts snippets, wired with Metro: a minimal application, with `boot` and the config in their metro flavors,
// and a CIO server. In-repo that's each module's `metroElements` configuration; a downstream consumer depends on the
// module's `-metro` artifact.
// A project of its own: a -metro module's bindings join every graph in the compilation, so another function's
// modules would need bindings these snippets don't have. The server is the plain module, for the same reason.
dependencies {
  implementation(project(":docs:snippets:common"))
  implementation(project(path = ":boot", configuration = "metroElements"))
  implementation(project(path = ":boot-config-hoplite", configuration = "metroElements"))
  implementation(project(":boot-server-http-ktor-cio"))
}
