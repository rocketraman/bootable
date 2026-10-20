import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsPlugin

plugins {
  kotlin("jvm")
  application
}

// Kilua's kilua-ssr-server, whose SsrEngine runs the renderer, is built for Java 25, so this targets 25, where the rest of
// the repository targets 17
java {
  sourceCompatibility = JavaVersion.VERSION_25
  targetCompatibility = JavaVersion.VERSION_25
}

kotlin {
  compilerOptions {
    jvmTarget.set(JvmTarget.JVM_25)
  }
}

// The client's production bundles: for the browser, its index.html, stylesheet, JavaScript, and source maps; and for
// Node.js, the renderer, as ssr.zip.
val clientBundle = configurations.create("clientBundle") {
  isCanBeConsumed = false
}
val ssrBundle = configurations.create("ssrBundle") {
  isCanBeConsumed = false
}

dependencies {
  implementation(project(":examples:kilua:shared"))
  implementation(project(":boot"))
  implementation(project(":boot-config-hoplite"))
  implementation(project(":boot-logging-log4j2"))
  implementation(project(":boot-server-http-ktor-netty"))

  implementation(libs.ktor.server.content.negotiation)

  // Kilua's SsrEngine, which runs the renderer, without its logback, which would compete with the app's log4j for SLF4J
  implementation(libs.kilua.ssr.server) {
    exclude(group = "ch.qos.logback")
  }
  // it calls the renderer with a Ktor client built for an older Ktor than the server's, so align them
  implementation(platform(libs.ktor.bom))
  implementation(libs.ktor.serialization.kotlinx.json)

  clientBundle(project(path = ":examples:kilua:client", configuration = "browserDistribution"))
  ssrBundle(project(path = ":examples:kilua:client", configuration = "ssrBundle"))

  testImplementation(libs.ktor.server.test.host)
  testImplementation(libs.ktor.client.content.negotiation)
}

// Serve the client from this server's classpath, under web/, and render with it from ssr.zip, so the app runs as one
// process and one jar, plus Node.js.
tasks.processResources {
  from(clientBundle) {
    into("web")
  }
  from(ssrBundle)
}

// The renderer needs Node.js. `run` and the tests use the one the Kotlin Gradle plugin downloads for Kotlin/JS builds, by
// setting SSR_NODE, which Bootable's config reads as ssr.node; anything else falls back to `node` on the PATH.
val nodeJs = NodeJsPlugin.apply(project)
fun <T> T.useDownloadedNode() where T : Task, T : ProcessForkOptions {
  dependsOn(with(nodeJs) { project.nodeJsSetupTaskProvider })
  val node = nodeJs.executable
  doFirst { environment("SSR_NODE", node.get()) }
}
tasks.named<JavaExec>("run") { useDownloadedNode() }
tasks.test { useDownloadedNode() }

application {
  mainClass.set("AppKt")
}
