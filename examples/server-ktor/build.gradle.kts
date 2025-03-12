plugins {
  alias(libs.plugins.kotlin.jvm)
  application
}

group = "com.github.rocketraman.bootable.examples"
version = "1.0"

repositories {
  // to use the locally built version of boot
  mavenLocal()
  mavenCentral()
  maven(url = "https://maven.pkg.jetbrains.space/public/p/kotlinx-html/maven") {
    content {
      includeModule("org.jetbrains.kotlinx", "kotlinx-html-jvm")
    }
  }
}

dependencies {
  // Mix-and-match bootable modules as necessary.
  // This basic set provides the basic boot functionality configuration via hoplite, logging via log4j2, and convenience
  // methods for exposing ktor services.
  implementation(libs.bootable.boot)
  implementation(libs.bootable.boot.config.hoplite)
  implementation(libs.bootable.boot.logging.log4j2)
  implementation(libs.bootable.boot.server.http.ktor)

  implementation(libs.ktor.server.html.builder)
  implementation(libs.kotlinx.html)

  testImplementation(kotlin("test-junit5"))
  testImplementation(libs.junit.jupiter.api)
  testRuntimeOnly(libs.junit.jupiter.engine)
}

tasks.test {
  useJUnitPlatform()
}

kotlin {
  jvmToolchain(21)
}

java {
  withSourcesJar()
}

application {
  mainClass.set("AppKt")
}
