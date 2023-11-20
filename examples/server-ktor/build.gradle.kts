plugins {
  val kotlinVersion: String by System.getProperties()
  kotlin("jvm") version kotlinVersion
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
  implementation("com.github.rocketraman.bootable:boot:0.9.0")
  implementation("com.github.rocketraman.bootable:boot-config-cfg4k:0.9.0")
  implementation("com.github.rocketraman.bootable:boot-logging-log4j2:0.9.0")
  implementation("com.github.rocketraman.bootable:boot-server-http-ktor:0.9.0")
  implementation("io.ktor:ktor-server-html-builder:2.3.6")
  implementation("io.ktor:ktor-server-netty:2.3.6")
  implementation("org.jetbrains.kotlinx:kotlinx-html-jvm:0.8.0")

  testImplementation(kotlin("test-junit5"))
  testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.0")
}

tasks.test {
  useJUnitPlatform()
}

kotlin {
  jvmToolchain(17)
}

java {
  withSourcesJar()
}

application {
  mainClass.set("AppKt")
}
