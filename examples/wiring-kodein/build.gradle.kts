plugins {
  kotlin("jvm")
  application
}

dependencies {
  // consume each Bootable module's kodein flavor. In-repo that's the module's `kodeinElements` configuration;
  // a real downstream consumer writes implementation("com.github.rocketraman.bootable:boot-kodein:<v>") etc.
  implementation(project(path = ":boot", configuration = "kodeinElements"))
  implementation(project(path = ":boot-config-hoplite", configuration = "kodeinElements"))
  implementation(project(":boot-logging-log4j2"))
}

application {
  mainClass.set("AppKt")
}
