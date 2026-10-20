plugins {
  kotlin("jvm")
  application
}

kotlin {
  jvmToolchain(25)
}

// htmx's webjar, from which the build takes only the script.
val htmx = configurations.create("htmx") {
  isCanBeConsumed = false
  isTransitive = false
}

dependencies {
  implementation(project(":boot"))
  implementation(project(":boot-config-hoplite"))
  implementation(project(":boot-logging-log4j2"))
  implementation(project(":boot-server-http-ktor-netty"))

  implementation(libs.ktor.server.html.builder)
  implementation(libs.ktor.server.htmx)
  implementation(libs.ktor.htmx.html)

  htmx(libs.htmx)

  testImplementation(libs.ktor.server.test.host)
}

// Serve htmx from this server's classpath, as web/htmx.min.js, rather than from a CDN.
tasks.processResources {
  from(htmx.elements.map { jars -> jars.map { zipTree(it) } }) {
    include("**/dist/htmx.min.js")
    eachFile { path = "web/$name" }
    includeEmptyDirs = false
  }
}

application {
  mainClass.set("AppKt")
}
