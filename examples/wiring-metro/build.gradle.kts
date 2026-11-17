plugins {
  kotlin("jvm")
  id("dev.zacsweers.metro")
  application
}

dependencies {
  // consume each Bootable module's metro flavor. In-repo that's the module's `metroElements` configuration;
  // a real downstream consumer writes implementation("com.github.rocketraman.bootable:boot-metro:<v>") etc.
  implementation(project(path = ":boot", configuration = "metroElements"))
  implementation(project(path = ":boot-config-hoplite", configuration = "metroElements"))
  implementation(project(":boot-logging-log4j2"))
}

application {
  mainClass.set("AppKt")
}
