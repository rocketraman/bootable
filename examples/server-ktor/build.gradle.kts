import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  kotlin("jvm") version "1.8.10"
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
  implementation("com.github.rocketraman.bootable:boot:0.8.1-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-config-cfg4k:0.8.1-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-logging-log4j2:0.8.1-SNAPSHOT")
  implementation("com.github.rocketraman.bootable:boot-server-http-ktor:0.8.1-SNAPSHOT")
  implementation("io.ktor:ktor-server-html-builder:2.2.4")
  implementation("io.ktor:ktor-server-netty:2.2.4")
  implementation("org.jetbrains.kotlinx:kotlinx-html-jvm:0.8.0")

  testImplementation(kotlin("test-junit5"))
  testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.2")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.2")
}

tasks.test {
  useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
  kotlinOptions.jvmTarget = JavaVersion.VERSION_11.toString()
}

java {
  withSourcesJar()
}

kotlin {
  jvmToolchain {
    languageVersion.set(JavaLanguageVersion.of(11))
  }
}

application {
  mainClass.set("AppKt")
}
