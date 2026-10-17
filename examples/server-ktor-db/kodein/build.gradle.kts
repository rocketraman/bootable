plugins {
  kotlin("jvm")
  application
}

kotlin {
  jvmToolchain(21)
}

dependencies {
  implementation(project(":examples:server-ktor-db:common"))
  // consume each Bootable module's kodein flavor. In-repo that's the module's `kodeinElements` configuration;
  // a real downstream consumer writes implementation("com.github.rocketraman.bootable:boot-kodein:<v>") etc.
  implementation(project(path = ":boot", configuration = "kodeinElements"))
  implementation(project(path = ":boot-config-hoplite", configuration = "kodeinElements"))
  implementation(project(":boot-logging-log4j2"))
  // the two database choices: a driver (SQLite) and a pool (HikariCP)
  implementation(project(path = ":boot-db-jdbc-sqlite", configuration = "kodeinElements"))
  implementation(project(path = ":boot-db-pool-hikari", configuration = "kodeinElements"))
}

application {
  mainClass.set("AppKt")
}
