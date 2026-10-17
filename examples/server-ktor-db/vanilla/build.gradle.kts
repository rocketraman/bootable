plugins {
  kotlin("jvm")
  application
}

kotlin {
  jvmToolchain(21)
}

dependencies {
  implementation(project(":examples:server-ktor-db:common"))
  implementation(project(":boot-config-hoplite"))
  implementation(project(":boot-logging-log4j2"))
  // the two database choices: a driver (SQLite) and a pool (HikariCP)
  implementation(project(":boot-db-jdbc-sqlite"))
  implementation(project(":boot-db-pool-hikari"))
}

application {
  mainClass.set("AppKt")
}
