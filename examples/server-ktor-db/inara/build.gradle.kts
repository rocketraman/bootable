plugins {
  kotlin("jvm")
  // Inara is a KSP processor: it generates the builder for the application's @DIModule
  id("com.google.devtools.ksp")
  application
}

kotlin {
  jvmToolchain(25)
}

dependencies {
  implementation(project(":examples:server-ktor-db:common"))
  // consume each Bootable module's inara flavor. In-repo that's the module's `inaraElements` configuration;
  // a real downstream consumer writes implementation("com.github.rocketraman.bootable:boot-inara:<v>") etc.
  implementation(project(path = ":boot", configuration = "inaraElements"))
  implementation(project(path = ":boot-config-hoplite", configuration = "inaraElements"))
  implementation(project(":boot-logging-log4j2"))
  // the two database choices: a driver (SQLite) and a pool (HikariCP)
  implementation(project(path = ":boot-db-jdbc-sqlite", configuration = "inaraElements"))
  implementation(project(path = ":boot-db-pool-hikari", configuration = "inaraElements"))
  // migrations, and Exposed with schema verification
  implementation(project(path = ":boot-db-flyway", configuration = "inaraElements"))
  implementation(project(path = ":boot-db-exposed", configuration = "inaraElements"))
  ksp(libs.inara.processor)
}

application {
  mainClass.set("AppKt")
}
