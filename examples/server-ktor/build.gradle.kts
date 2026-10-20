import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
  kotlin("jvm")
  kotlin("plugin.power-assert")
  id("de.infix.testBalloon")
  alias(libs.plugins.graalvm.native)
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
  // methods for exposing ktor services, on the Netty engine (or -cio / -jetty for the other engines).
  implementation(project(":boot"))
  implementation(project(":boot-config-hoplite"))
  implementation(project(":boot-logging-log4j2"))
  implementation(project(":boot-server-http-ktor-netty"))

  implementation(libs.ktor.server.html.builder)
  implementation(libs.kotlinx.html)

  testImplementation(libs.testBalloon.framework.core)
  testImplementation(project(":boot-env"))
  testImplementation(libs.ktor.server.test.host)
}

kotlin {
  jvmToolchain(21)
}

@OptIn(ExperimentalKotlinGradlePluginApi::class)
powerAssert {
  functions = listOf("kotlin.assert")
  includedSourceSets = listOf("test")
}

java {
  withSourcesJar()
}

application {
  mainClass.set("AppKt")
}

// GraalVM native-image smoke test. This example consumes bootable from mavenLocal and exercises
// boot + hoplite config (ServerConfig implements HostPort) + log4j2 base-xml selection + ktor.
// It relies entirely on the reachability-metadata bundled inside the bootable jars — there is no
// hand-maintained native metadata here. Build with `./gradlew nativeCompile`, run the binary,
// then send SIGTERM and confirm a clean shutdown with no KotlinReflectionInternalError.
graalvmNative {
  metadataRepository {
    enabled.set(true)
  }
  binaries {
    named("main") {
      // the root build applies java-library to every subproject, and native-build-tools then
      // defaults the main binary to a shared library (--shared); this example is an executable
      sharedLibrary.set(false)
      // a missing reflection/resource registration must be a hard error, never a JVM fallback
      fallback.set(false)
      quickBuild.set(true)
    }
  }
}
