plugins {
  kotlin("jvm")
  application
}

kotlin {
  jvmToolchain(21)
}

dependencies {
  implementation(project(":boot"))
  implementation(project(":boot-config-hoplite"))
  implementation(project(":boot-logging-log4j2"))
}

application {
  mainClass.set("AppKt")
}
