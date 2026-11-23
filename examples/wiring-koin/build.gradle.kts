plugins {
  kotlin("jvm")
  application
}

dependencies {
  // consume each Bootable module's koin flavor. In-repo that's the module's `koinElements` configuration;
  // a real downstream consumer writes implementation("com.github.rocketraman.bootable:boot-koin:<v>") etc.
  implementation(project(path = ":boot", configuration = "koinElements"))
  implementation(project(path = ":boot-config-hoplite", configuration = "koinElements"))
  implementation(project(":boot-logging-log4j2"))
}

application {
  mainClass.set("AppKt")
}
