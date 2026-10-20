plugins {
  kotlin("jvm")
  application
}

kotlin {
  jvmToolchain(21)
}

// The client's production bundle: its index.html, JavaScript, and source maps.
val clientBundle = configurations.create("clientBundle") {
  isCanBeConsumed = false
}

dependencies {
  implementation(project(":examples:compose-html:shared"))
  implementation(project(":boot"))
  implementation(project(":boot-config-hoplite"))
  implementation(project(":boot-logging-log4j2"))
  implementation(project(":boot-server-http-ktor"))

  implementation(libs.ktor.server.content.negotiation)
  implementation(libs.ktor.serialization.kotlinx.json)

  clientBundle(project(path = ":examples:compose-html:client", configuration = "browserDistribution"))

  testImplementation(libs.ktor.server.test.host)
  testImplementation(libs.ktor.client.content.negotiation)
}

// Serve the client from this server's classpath, under web/, so the app runs as one process and one jar.
tasks.processResources {
  from(clientBundle) {
    into("web")
  }
}

application {
  mainClass.set("AppKt")
}
