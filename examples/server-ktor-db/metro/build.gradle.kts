import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
  kotlin("jvm")
  id("dev.zacsweers.metro")
  application
}

kotlin {
  jvmToolchain(21)
  compilerOptions {
    // Metro requires Kotlin 2.3; the shared subprojects config targets 2.1
    apiVersion.set(KotlinVersion.KOTLIN_2_3)
    languageVersion.set(KotlinVersion.KOTLIN_2_3)
  }
}

dependencies {
  implementation(project(":examples:server-ktor-db:common"))
  // consume each Bootable module's metro flavor. In-repo that's the module's `metroElements` configuration;
  // a real downstream consumer writes implementation("com.github.rocketraman.bootable:boot-metro:<v>") etc.
  implementation(project(path = ":boot", configuration = "metroElements"))
  implementation(project(path = ":boot-config-hoplite", configuration = "metroElements"))
  implementation(project(":boot-logging-log4j2"))
  // the two database choices: a driver (SQLite) and a pool (HikariCP)
  implementation(project(path = ":boot-db-jdbc-sqlite", configuration = "metroElements"))
  implementation(project(path = ":boot-db-pool-hikari", configuration = "metroElements"))
  // migrations, and Exposed with schema verification
  implementation(project(path = ":boot-db-flyway", configuration = "metroElements"))
  implementation(project(path = ":boot-db-exposed", configuration = "metroElements"))
}

application {
  mainClass.set("AppKt")
}
