plugins {
  kotlin("jvm")
  // Inara is a KSP processor: it generates the builder for the application's @DIModule
  id("com.google.devtools.ksp")
  application
}

dependencies {
  // consume each Bootable module's inara flavor. In-repo that's the module's `inaraElements` configuration;
  // a real downstream consumer writes implementation("com.github.rocketraman.bootable:boot-inara:<v>") etc.
  implementation(project(path = ":boot", configuration = "inaraElements"))
  implementation(project(path = ":boot-config-hoplite", configuration = "inaraElements"))
  implementation(project(":boot-logging-log4j2"))
  ksp(libs.inara.processor)
}

application {
  mainClass.set("AppKt")
}
