import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  kotlin("jvm") version "1.5.10"
  application
}

group = "com.github.rocketraman.bootable.examples"
version = "1.0"

repositories {
  jcenter()
  mavenCentral()
  maven { url = uri("https://maven.pkg.jetbrains.space/public/p/kotlinx-html/maven") }
  // temporary for boot
  mavenLocal()
}

dependencies {
  implementation("com.github.rocketraman.bootable:boot:0.1")
  implementation("com.github.rocketraman.bootable:boot-config-cfg4k:0.1")
  implementation("com.github.rocketraman.bootable:boot-logging-log4j2:0.1")
  implementation("com.github.rocketraman.bootable:boot-server-http-ktor:0.1")
  implementation("io.ktor:ktor-server-netty:1.6.0")
  implementation("io.ktor:ktor-html-builder:1.6.0")
  implementation("org.jetbrains.kotlinx:kotlinx-html-jvm:0.7.2")

  testImplementation(kotlin("test-junit5"))
  testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.0")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.6.0")
}

tasks.test {
  useJUnitPlatform()
}

tasks.withType<KotlinCompile>() {
  kotlinOptions.jvmTarget = "11"
}

application {
  mainClassName = "ServerKt"
}
