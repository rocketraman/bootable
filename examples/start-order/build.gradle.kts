plugins {
  kotlin("jvm")
  application
}

kotlin {
  jvmToolchain(25)
}

dependencies {
  implementation(project(":boot"))
  implementation(project(":boot-config-hoplite"))
  implementation(project(":boot-logging-log4j2"))
  implementation(project(":boot-server-http-ktor-cio"))
}

application {
  mainClass.set("AppKt")
}
