import com.vanniktech.maven.publish.JavaLibrary
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SourcesJar
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.powerassert.gradle.PowerAssertGradleExtension
import dev.zacsweers.metro.gradle.MetroPluginExtension
import com.google.protobuf.gradle.ProtobufExtension
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Jar

plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.dokka)
  alias(libs.plugins.dokka.javadoc)
  alias(libs.plugins.jib) apply false
  alias(libs.plugins.kilua) apply false
  alias(libs.plugins.metro) apply false
  alias(libs.plugins.protobuf) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.kotlin.power.assert) apply false
  alias(libs.plugins.kotlin.serialization) apply false
  alias(libs.plugins.ksp) apply false
  alias(libs.plugins.testBalloon) apply false
  signing
  alias(libs.plugins.maven.publish) apply false
}

val signingRequired: String by project

dependencies {
  dokka(project(":boot"))
  dokka(project(":boot-client-http-ktor"))
  dokka(project(":boot-client-http-ktor-cio"))
  dokka(project(":boot-client-http-ktor-java"))
  dokka(project(":boot-client-http-ktor-otel"))
  dokka(project(":boot-common"))
  dokka(project(":boot-config-common"))
  dokka(project(":boot-config-hoplite"))
  dokka(project(":boot-db-exposed"))
  dokka(project(":boot-db-flyway"))
  dokka(project(":boot-db-jdbc"))
  dokka(project(":boot-db-jdbc-mysql"))
  dokka(project(":boot-db-jdbc-otel"))
  dokka(project(":boot-db-jdbc-postgresql"))
  dokka(project(":boot-db-jdbc-sqlite"))
  dokka(project(":boot-db-pool-hikari"))
  dokka(project(":boot-db-pool-hikari-otel"))
  dokka(project(":boot-env"))
  dokka(project(":boot-leader-common"))
  dokka(project(":boot-leader-k8s"))
  dokka(project(":boot-logging"))
  dokka(project(":boot-logging-log4j2"))
  dokka(project(":boot-periodic"))
  dokka(project(":boot-scheduler-db"))
  dokka(project(":boot-server-http-ktor"))
  dokka(project(":boot-server-http-ktor-cio"))
  dokka(project(":boot-server-http-ktor-netty"))
  dokka(project(":boot-server-grpc"))
  dokka(project(":boot-server-grpc-otel"))
  dokka(project(":boot-server-http-ktor-otel"))
  dokka(project(":boot-telemetry-otel"))
  dokka(project(":boot-telemetry-otel-common"))
  dokka(project(":boot-telemetry-otel-gcp"))
  dokka(project(":boot-telemetry-otel-micrometer-bridge"))
  dokka(project(":boot-telemetry-otel-prometheus"))
  dokka(project(":boot-telemetry-otel-signoz"))
  dokka(project(":boot-test"))
  dokka(project(":boot-test-otel"))
}

// The published version comes from a `vX.Y.Z` tag on HEAD, so a release is driven purely by tagging; any
// other build (development, CI) falls back to the snapshot below. See docs/releasing.adoc.
val developmentVersion = "3.1.0-SNAPSHOT"
val bootableVersion: String = runCatching {
  providers.exec {
    commandLine("git", "tag", "--points-at", "HEAD")
    isIgnoreExitValue = true
  }.standardOutput.asText.get()
    .lineSequence()
    .firstNotNullOfOrNull { Regex("""^v(\d+\.\d+\.\d+)$""").matchEntire(it.trim())?.groupValues?.get(1) }
}.getOrNull() ?: developmentVersion

allprojects {
  group = "com.github.rocketraman.bootable"
  version = bootableVersion
}

// Kotlin Multiplatform projects, which configure their own Kotlin plugin; the JVM conventions below would clash with it.
val multiplatformProjects = setOf(
  ":examples:compose-html:client",
  ":examples:compose-html:shared",
  ":examples:kilua:client",
  ":examples:kilua:shared",
)

configure(subprojects.filter { it.path !in multiplatformProjects }) {
  apply {
    plugin("java-library")
    plugin("org.jetbrains.kotlin.jvm")
    plugin("org.jetbrains.kotlin.plugin.power-assert")
    plugin("de.infix.testBalloon")
  }

  dependencies {
    implementation(platform(rootProject.libs.kotlin.bom))
    // Bootable embeds no DI framework — callers wire the components with vanilla Kotlin, Inara, Koin, Metro, etc.
    testImplementation(rootProject.libs.testBalloon.framework.core)
  }

  configurations.all {
    resolutionStrategy.eachDependency {
      if (requested.group == "org.jetbrains.kotlin") {
        useVersion(rootProject.libs.versions.kotlin.get())
        because("Use consistent Kotlin stdlib and reflect artifacts")
      }
    }
  }

  java {
    // target 17 for max compatibility
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  kotlin {
    // compile with JDK 25, but for Java 17: the bytecode, and the JDK APIs it may use, which -Xjdk-release restricts to
    // those of the target, following jvmTarget wherever a module changes it
    jvmToolchain(25)
    compilerOptions {
      // target 17 for max compatibility
      jvmTarget.set(JvmTarget.JVM_17)
      freeCompilerArgs.addAll(jvmTarget.map { listOf("-Xjdk-release=${it.target}") })
      // the last two Kotlin versions: 2.3, which Metro needs, and kotlin.time.Clock is stable from, and 2.4
      apiVersion.set(KotlinVersion.KOTLIN_2_3)
      languageVersion.set(KotlinVersion.KOTLIN_2_3)
    }
  }

  @OptIn(ExperimentalKotlinGradlePluginApi::class)
  configure<PowerAssertGradleExtension> {
    functions.set(listOf(
      "kotlin.assert",
      "kotlin.test.assertTrue",
      "kotlin.test.assertEquals",
      "kotlin.test.assertNull",
      "kotlin.check",
    ))
  }

  tasks.withType<Test>().configureEach {
    // TestBalloon generates a test entry point in every module, so Gradle sees "test sources" even in modules with
    // no tests and fails the build when none are discovered. Keep that safety check where real tests exist.
    failOnNoDiscoveredTests.set(file("src/test").exists())
  }
}

// Only the library modules are published to Maven Central; everything under :examples is a runnable sample
// app, and everything under :docs is the documentation site's snippets, not an artifact, so they get none of the
// publish / signing / dokka machinery.
configure(subprojects.filter { !it.path.startsWith(":examples") && !it.path.startsWith(":docs") }) {
  apply {
    plugin("org.jetbrains.dokka")
    plugin("org.jetbrains.dokka-javadoc")
    plugin("signing")
    plugin("com.vanniktech.maven.publish")
  }

  configure<MavenPublishBaseExtension> {
    configure(
      JavaLibrary(
        javadocJar = JavadocJar.Dokka("dokkaGeneratePublicationHtml"),
        sourcesJar = SourcesJar.Sources(),
      )
    )
    // uploads to the Central Portal and releases automatically once validation passes
    publishToMavenCentral(automaticRelease = true)
    if (signingRequired.toBoolean()) {
      signAllPublications()
    }
    pom {
      name.set(project.name)
      description.set("Simple unopinionated microservice runtime")
      url.set("https://github.com/rocketraman/bootable")
      licenses {
        license {
          name.set("The Apache License, Version 2.0")
          url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
        }
      }
      developers {
        developer {
          id.set("rocketraman")
          name.set("Raman Gupta")
          email.set("rocketraman@gmail.com")
        }
      }
      scm {
        connection.set("scm:git:git@github.com:rocketraman/bootable.git")
        developerConnection.set("scm:git:ssh://github.com:rocketraman/bootable.git")
        url.set("https://github.com/rocketraman/bootable")
      }
    }
  }

  // vanniktech's signAllPublications() delegates to the signing plugin; keep using the local gpg agent
  if (signingRequired.toBoolean()) {
    signing {
      useGpgCmd()
    }
  }
}

project("boot") {
  apply(plugin = "org.jetbrains.kotlin.plugin.serialization")
  configureDiVariants()
  dependencies {
    // ServiceOrderConfig, Bootable's own settings, is decoded with a ConfigBinder
    api(project(":boot-config-common"))
    // HealthReport and BuildInfo are @Serializable, in any format: generated serializers, with no reflection, so
    // nothing to add for a native image
    api(rootProject.libs.kotlinx.serialization.core)
    // StaticConfigBinder, for the wiring tests' config
    testImplementation(project(":boot-test"))
    testImplementation(rootProject.libs.kotlinx.serialization.json)
    // the lifecycle is suspending, and services launch their background work in a CoroutineScope
    api(rootProject.libs.kotlinx.coroutines.core)
    implementation(project(":boot-common"))
    // todo for now, we depend on boot-logging-log4j2, later we could use service loaders to discover implementations at runtime
    implementation(project(":boot-logging-log4j2"))
    // note: no DI framework — Bootable's wiring is plain Kotlin (kotlin-reflect is still needed at runtime
    // for log4j-api-kotlin logging, and comes transitively via boot-logging-log4j2)
    // the inara flavor reads its config through config-common's ConfigModule
    "inaraApi"(project(path = ":boot-config-common", configuration = "inaraElements"))
    // the tests compose the inara flavor's BootableModule into a module of their own, as an application does
    "kspTest"(rootProject.libs.inara.processor)
  }

  dependencies {
    // virtual time, for the drain, the shutdown timeout, and the stop races
    testImplementation(rootProject.libs.kotlinx.coroutines.test)
  }

  // the tests exercise this module's kodein, koin, metro, and inara flavors too, wired up as a consumer would
  configurations["testImplementation"].extendsFrom(
    configurations["kodeinApi"],
    configurations["koinApi"],
    configurations["metroApi"],
    configurations["inaraApi"],
  )
  sourceSets.named("test") {
    compileClasspath += sourceSets["kodein"].output + sourceSets["koin"].output + sourceSets["metro"].output + sourceSets["inara"].output
    runtimeClasspath += sourceSets["kodein"].output + sourceSets["koin"].output + sourceSets["metro"].output + sourceSets["inara"].output
  }

}

project("boot-client-http-ktor") {
  configureDiVariants()
  dependencies {
    // the kodein and koin flavors bind their config with config-common's helpers
    "kodeinApi"(project(path = ":boot-config-common", configuration = "kodeinElements"))
    "koinApi"(project(path = ":boot-config-common", configuration = "koinElements"))
    // runSuspendCatching
    testImplementation(project(":boot-test"))
    // KtorClientService is an AppService, and the clients it owns are Ktor's own HttpClient
    api(project(":boot"))
    api(project(":boot-config-common"))
    // engine-agnostic: the engine comes from one of the boot-client-http-ktor-<engine> modules, or the application
    api(rootProject.libs.ktor.client.core)
    // the logging API only: the application chooses the logging implementation (e.g. boot-logging-log4j2)
    implementation(rootProject.libs.bundles.log4j.api)
    "metroApi"(project(path = ":boot", configuration = "metroElements"))
    // the inara flavor adds its service to boot's set, and reads its config
    "inaraApi"(project(path = ":boot", configuration = "inaraElements"))
    "inaraApi"(project(path = ":boot-config-common", configuration = "inaraElements"))

    // the clients run against a real server on each engine, and against a MockEngine through the engine seam
    testImplementation(project(":boot-client-http-ktor-cio"))
    testImplementation(project(":boot-client-http-ktor-java"))
    testImplementation(rootProject.libs.ktor.client.mock)
    // certificates for the mutual TLS tests, generated as they run
    testImplementation(rootProject.libs.ktor.network.tls.certificates)
    // the tests exercise this module's kodein, koin, metro, and inara flavors too, wired up as a consumer would
    testImplementation(project(path = ":boot", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot", configuration = "koinElements"))
    testImplementation(project(path = ":boot", configuration = "inaraElements"))
    testImplementation(project(path = ":boot", configuration = "metroElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "koinElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "inaraElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "metroElements"))
    testImplementation(project(":boot-logging-log4j2"))
  }

  // the tests compose the inara flavor into a module of their own, as an application does
  configurations["testImplementation"].extendsFrom(configurations["inaraApi"])
  dependencies { "kspTest"(rootProject.libs.inara.processor) }
  sourceSets.named("test") {
    compileClasspath += sourceSets["kodein"].output + sourceSets["koin"].output + sourceSets["metro"].output + sourceSets["inara"].output
    runtimeClasspath += sourceSets["kodein"].output + sourceSets["koin"].output + sourceSets["metro"].output + sourceSets["inara"].output
  }
}

// Each engine module is a KtorClientEngine for its engine, applying the clients' TLS settings in that engine's own
// terms, and brings that engine transitively
listOf(
  "cio" to rootProject.libs.ktor.client.cio,
  "java" to rootProject.libs.ktor.client.java,
).forEach { (engine, engineLibrary) ->
  project("boot-client-http-ktor-$engine") {
    dependencies {
      api(project(":boot-client-http-ktor"))
      api(engineLibrary)
    }
  }
}

// traces and measures each client request through the application's OpenTelemetry, as a KtorClientCustomizer
project("boot-client-http-ktor-otel") {
  configureDiVariants()
  dependencies {
    // the kodein and koin flavors bind their config with config-common's helpers
    "kodeinApi"(project(path = ":boot-config-common", configuration = "kodeinElements"))
    "koinApi"(project(path = ":boot-config-common", configuration = "koinElements"))
    api(project(":boot-client-http-ktor"))
    // the customizer only needs an OpenTelemetry, whichever SDK built it
    api(rootProject.libs.opentelemetry.api)
    api(rootProject.libs.opentelemetry.instrumentation.ktor)
    // each flavor adds to the clients' customizers, over the OpenTelemetry of boot-telemetry-otel's SDK, so it brings
    // both modules' matching flavors
    "metroApi"(project(path = ":boot", configuration = "metroElements"))
    "metroApi"(project(path = ":boot-client-http-ktor", configuration = "metroElements"))
    "metroApi"(project(path = ":boot-telemetry-otel", configuration = "metroElements"))
    "kodeinApi"(project(path = ":boot-client-http-ktor", configuration = "kodeinElements"))
    "koinApi"(project(path = ":boot-client-http-ktor", configuration = "koinElements"))
    "kodeinApi"(project(path = ":boot-telemetry-otel", configuration = "kodeinElements"))
    "koinApi"(project(path = ":boot-telemetry-otel", configuration = "koinElements"))
    "inaraApi"(project(path = ":boot-client-http-ktor", configuration = "inaraElements"))
    "inaraApi"(project(path = ":boot-telemetry-otel-common", configuration = "inaraElements"))

    // client calls are made from within a real, traced server's requests
    testImplementation(project(":boot-client-http-ktor-cio"))
    testImplementation(project(":boot-server-http-ktor-cio"))
    testImplementation(project(":boot-server-http-ktor-otel"))
    testImplementation(project(":boot-telemetry-otel"))
    testImplementation(rootProject.libs.ktor.client.mock)
    testImplementation(rootProject.libs.opentelemetry.sdk.testing)
    // the tests exercise this module's kodein, koin, metro, and inara flavors too, wired up as a consumer would
    testImplementation(project(path = ":boot", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot", configuration = "koinElements"))
    testImplementation(project(path = ":boot", configuration = "inaraElements"))
    testImplementation(project(path = ":boot", configuration = "metroElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "koinElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "inaraElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "metroElements"))
    testImplementation(project(path = ":boot-client-http-ktor", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot-client-http-ktor", configuration = "koinElements"))
    testImplementation(project(path = ":boot-client-http-ktor", configuration = "inaraElements"))
    testImplementation(project(path = ":boot-client-http-ktor", configuration = "metroElements"))
    testImplementation(project(path = ":boot-telemetry-otel", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot-telemetry-otel", configuration = "koinElements"))
    testImplementation(project(path = ":boot-telemetry-otel", configuration = "inaraElements"))
    testImplementation(project(path = ":boot-telemetry-otel", configuration = "metroElements"))
    testImplementation(project(":boot-logging-log4j2"))
  }

  // the tests compose the inara flavor into a module of their own, as an application does
  configurations["testImplementation"].extendsFrom(configurations["inaraApi"])
  dependencies { "kspTest"(rootProject.libs.inara.processor) }
  sourceSets.named("test") {
    compileClasspath += sourceSets["kodein"].output + sourceSets["koin"].output + sourceSets["metro"].output + sourceSets["inara"].output
    runtimeClasspath += sourceSets["kodein"].output + sourceSets["koin"].output + sourceSets["metro"].output + sourceSets["inara"].output
  }
}

project("boot-common") {
  dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${rootProject.libs.versions.kotlinx.coroutines.get()}")
  }
}

project("boot-config-hoplite") {
  configureDiVariants()
  dependencies {
    // the kodein and koin flavors re-expose config-common's, so their consumers get its config helpers too
    "kodeinApi"(project(path = ":boot-config-common", configuration = "kodeinElements"))
    "koinApi"(project(path = ":boot-config-common", configuration = "koinElements"))
    api(project(":boot-config-common"))
    api(rootProject.libs.bundles.hoplite)

    implementation(project(":boot"))
    // the metro flavor contributes to boot's BootScope, and re-exposes config-common's metro flavor
    // (ConfigBinderProvider) so consumers of boot-config-hoplite-metro get the config helper too. In-repo these
    // resolve the sibling metro source sets; in the published POM they map to the -metro coordinates.
    "metroApi"(project(path = ":boot", configuration = "metroElements"))
    "metroApi"(project(path = ":boot-config-common", configuration = "metroElements"))
    "inaraApi"(project(path = ":boot-config-common", configuration = "inaraElements"))
  }
}

project("boot-config-common") {
  configureDiVariants()
}

project("boot-db-jdbc") {
  configureDiVariants()
  dependencies {
    // the kodein and koin flavors bind their config with config-common's helpers
    "kodeinApi"(project(path = ":boot-config-common", configuration = "kodeinElements"))
    "koinApi"(project(path = ":boot-config-common", configuration = "koinElements"))
    api(project(":boot"))
    api(project(":boot-config-common"))
    "metroApi"(project(path = ":boot", configuration = "metroElements"))
    // the inara flavor marks its service with boot's AppServices set, and reads config-common's ConfigModule
    "inaraApi"(project(path = ":boot", configuration = "inaraElements"))
    "inaraApi"(project(path = ":boot-config-common", configuration = "inaraElements"))

    // a real driver, so the lifecycle tests run against a real database
    testImplementation(rootProject.libs.sqlite.jdbc)
  }
}

// traces each JDBC query through the application's OpenTelemetry, as a DataSourceWrapper for boot-db-jdbc
project("boot-db-jdbc-otel") {
  configureDiVariants()
  dependencies {
    api(project(":boot-db-jdbc"))
    // the wrapper only needs an OpenTelemetry, whichever SDK built it
    api(rootProject.libs.opentelemetry.api)
    api(rootProject.libs.opentelemetry.instrumentation.jdbc)
    // each flavor adds to boot-db-jdbc's wrappers, over the OpenTelemetry of boot-telemetry-otel's SDK, so it brings
    // both modules' matching flavors
    "metroApi"(project(path = ":boot", configuration = "metroElements"))
    "metroApi"(project(path = ":boot-db-jdbc", configuration = "metroElements"))
    "metroApi"(project(path = ":boot-telemetry-otel", configuration = "metroElements"))
    "kodeinApi"(project(path = ":boot-db-jdbc", configuration = "kodeinElements"))
    "koinApi"(project(path = ":boot-db-jdbc", configuration = "koinElements"))
    "kodeinApi"(project(path = ":boot-telemetry-otel", configuration = "kodeinElements"))
    "koinApi"(project(path = ":boot-telemetry-otel", configuration = "koinElements"))
    "inaraApi"(project(path = ":boot-db-jdbc", configuration = "inaraElements"))
    "inaraApi"(project(path = ":boot-telemetry-otel-common", configuration = "inaraElements"))

    testImplementation(rootProject.libs.sqlite.jdbc)
    testImplementation(rootProject.libs.opentelemetry.sdk.testing)
    // a real pool, and Exposed, whose transactions run on its own dispatcher, to check the spans' parents across it
    testImplementation(project(":boot-db-pool-hikari"))
    testImplementation(project(":boot-db-exposed"))
    // withSpan, for the enclosing span
    testImplementation(project(":boot-telemetry-otel"))
    // the tests exercise this module's kodein, koin, metro, and inara flavors too, wired up as a consumer would
    testImplementation(project(path = ":boot", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot", configuration = "koinElements"))
    testImplementation(project(path = ":boot", configuration = "inaraElements"))
    testImplementation(project(path = ":boot", configuration = "metroElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "koinElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "inaraElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "metroElements"))
    testImplementation(project(path = ":boot-db-jdbc", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot-db-jdbc", configuration = "koinElements"))
    testImplementation(project(path = ":boot-db-jdbc", configuration = "inaraElements"))
    testImplementation(project(path = ":boot-db-jdbc", configuration = "metroElements"))
    testImplementation(project(path = ":boot-db-pool-hikari", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot-db-pool-hikari", configuration = "koinElements"))
    testImplementation(project(path = ":boot-db-pool-hikari", configuration = "inaraElements"))
    testImplementation(project(path = ":boot-db-pool-hikari", configuration = "metroElements"))
    testImplementation(project(path = ":boot-telemetry-otel", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot-telemetry-otel", configuration = "koinElements"))
    testImplementation(project(path = ":boot-telemetry-otel", configuration = "inaraElements"))
    testImplementation(project(path = ":boot-telemetry-otel", configuration = "metroElements"))
    testImplementation(project(":boot-logging-log4j2"))
  }

  // the tests compose the inara flavor into a module of their own, as an application does
  configurations["testImplementation"].extendsFrom(configurations["inaraApi"])
  dependencies { "kspTest"(rootProject.libs.inara.processor) }
  sourceSets.named("test") {
    compileClasspath += sourceSets["kodein"].output + sourceSets["koin"].output + sourceSets["metro"].output + sourceSets["inara"].output
    runtimeClasspath += sourceSets["kodein"].output + sourceSets["koin"].output + sourceSets["metro"].output + sourceSets["inara"].output
  }
}

project("boot-db-pool-hikari") {
  configureDiVariants()
  dependencies {
    // the kodein and koin flavors bind their config with config-common's helpers
    "kodeinApi"(project(path = ":boot-config-common", configuration = "kodeinElements"))
    "koinApi"(project(path = ":boot-config-common", configuration = "koinElements"))
    api(project(":boot-db-jdbc"))
    api(rootProject.libs.hikari)
    // the logging API only: the application chooses the logging implementation (e.g. boot-logging-log4j2)
    implementation(rootProject.libs.bundles.log4j.api)
    // the pool is only useful with boot-db-jdbc's service, so each flavor brings boot-db-jdbc's matching flavor
    "metroApi"(project(path = ":boot", configuration = "metroElements"))
    "metroApi"(project(path = ":boot-db-jdbc", configuration = "metroElements"))
    "kodeinApi"(project(path = ":boot-db-jdbc", configuration = "kodeinElements"))
    "koinApi"(project(path = ":boot-db-jdbc", configuration = "koinElements"))
    "inaraApi"(project(path = ":boot-db-jdbc", configuration = "inaraElements"))

    testImplementation(rootProject.libs.sqlite.jdbc)
    // the tests exercise this module's kodein, koin, metro, and inara flavors too, wired up as a consumer would
    testImplementation(project(path = ":boot", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot", configuration = "koinElements"))
    testImplementation(project(path = ":boot", configuration = "inaraElements"))
    testImplementation(project(path = ":boot", configuration = "metroElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "koinElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "inaraElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "metroElements"))
    testImplementation(project(path = ":boot-db-jdbc", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot-db-jdbc", configuration = "koinElements"))
    testImplementation(project(path = ":boot-db-jdbc", configuration = "inaraElements"))
    testImplementation(project(path = ":boot-db-jdbc", configuration = "metroElements"))
    testImplementation(project(":boot-logging-log4j2"))
  }

  // the tests compose the inara flavor into a module of their own, as an application does
  configurations["testImplementation"].extendsFrom(configurations["inaraApi"])
  dependencies { "kspTest"(rootProject.libs.inara.processor) }
  sourceSets.named("test") {
    compileClasspath += sourceSets["kodein"].output + sourceSets["koin"].output + sourceSets["metro"].output + sourceSets["inara"].output
    runtimeClasspath += sourceSets["kodein"].output + sourceSets["koin"].output + sourceSets["metro"].output + sourceSets["inara"].output
  }
}

// HikariCP's pool metrics through the application's OpenTelemetry, as a HikariConfigCustomizer for the pool
project("boot-db-pool-hikari-otel") {
  configureDiVariants()
  dependencies {
    api(project(":boot-db-pool-hikari"))
    // the customizer only needs an OpenTelemetry, whichever SDK built it
    api(rootProject.libs.opentelemetry.api)
    api(rootProject.libs.opentelemetry.instrumentation.hikaricp)
    // each flavor adds to the pool's customizers, over the OpenTelemetry of boot-telemetry-otel's SDK, so it brings
    // both modules' matching flavors
    "metroApi"(project(path = ":boot", configuration = "metroElements"))
    "metroApi"(project(path = ":boot-db-pool-hikari", configuration = "metroElements"))
    "metroApi"(project(path = ":boot-telemetry-otel", configuration = "metroElements"))
    "kodeinApi"(project(path = ":boot-db-pool-hikari", configuration = "kodeinElements"))
    "koinApi"(project(path = ":boot-db-pool-hikari", configuration = "koinElements"))
    "kodeinApi"(project(path = ":boot-telemetry-otel", configuration = "kodeinElements"))
    "koinApi"(project(path = ":boot-telemetry-otel", configuration = "koinElements"))
    "inaraApi"(project(path = ":boot-db-pool-hikari", configuration = "inaraElements"))
    "inaraApi"(project(path = ":boot-telemetry-otel-common", configuration = "inaraElements"))

    testImplementation(rootProject.libs.sqlite.jdbc)
    testImplementation(rootProject.libs.opentelemetry.sdk.testing)
    // the tests exercise this module's kodein, koin, metro, and inara flavors too, wired up as a consumer would
    testImplementation(project(path = ":boot", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot", configuration = "koinElements"))
    testImplementation(project(path = ":boot", configuration = "inaraElements"))
    testImplementation(project(path = ":boot", configuration = "metroElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "koinElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "inaraElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "metroElements"))
    testImplementation(project(path = ":boot-db-jdbc", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot-db-jdbc", configuration = "koinElements"))
    testImplementation(project(path = ":boot-db-jdbc", configuration = "inaraElements"))
    testImplementation(project(path = ":boot-db-jdbc", configuration = "metroElements"))
    testImplementation(project(path = ":boot-db-pool-hikari", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot-db-pool-hikari", configuration = "koinElements"))
    testImplementation(project(path = ":boot-db-pool-hikari", configuration = "inaraElements"))
    testImplementation(project(path = ":boot-db-pool-hikari", configuration = "metroElements"))
    testImplementation(project(path = ":boot-telemetry-otel", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot-telemetry-otel", configuration = "koinElements"))
    testImplementation(project(path = ":boot-telemetry-otel", configuration = "inaraElements"))
    testImplementation(project(path = ":boot-telemetry-otel", configuration = "metroElements"))
    testImplementation(project(":boot-logging-log4j2"))
  }

  // the tests compose the inara flavor into a module of their own, as an application does
  configurations["testImplementation"].extendsFrom(configurations["inaraApi"])
  dependencies { "kspTest"(rootProject.libs.inara.processor) }
  sourceSets.named("test") {
    compileClasspath += sourceSets["kodein"].output + sourceSets["koin"].output + sourceSets["metro"].output + sourceSets["inara"].output
    runtimeClasspath += sourceSets["kodein"].output + sourceSets["koin"].output + sourceSets["metro"].output + sourceSets["inara"].output
  }
}

project("boot-db-jdbc-sqlite") {
  configureDiVariants()
  dependencies {
    // the kodein and koin flavors bind their config with config-common's helpers
    "kodeinApi"(project(path = ":boot-config-common", configuration = "kodeinElements"))
    "koinApi"(project(path = ":boot-config-common", configuration = "koinElements"))
    api(project(":boot-db-jdbc"))
    // the driver is the point of this module: depending on it brings the right JDBC driver transitively
    api(rootProject.libs.sqlite.jdbc)
    // SQLite migrations need no extra Flyway database module — flyway-core supports SQLite directly
    "metroApi"(project(path = ":boot", configuration = "metroElements"))
    "metroApi"(project(path = ":boot-db-jdbc", configuration = "metroElements"))
    "kodeinApi"(project(path = ":boot-db-jdbc", configuration = "kodeinElements"))
    "koinApi"(project(path = ":boot-db-jdbc", configuration = "koinElements"))
    "inaraApi"(project(path = ":boot-db-jdbc", configuration = "inaraElements"))

    // the tests exercise this module's kodein, koin, metro, and inara flavors too, wired up as a consumer would
    testImplementation(project(path = ":boot", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot", configuration = "koinElements"))
    testImplementation(project(path = ":boot", configuration = "inaraElements"))
    testImplementation(project(path = ":boot", configuration = "metroElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "koinElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "inaraElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "metroElements"))
    testImplementation(project(path = ":boot-db-jdbc", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot-db-jdbc", configuration = "koinElements"))
    testImplementation(project(path = ":boot-db-jdbc", configuration = "inaraElements"))
    testImplementation(project(path = ":boot-db-jdbc", configuration = "metroElements"))
    // the driver settings only matter through a real pool
    testImplementation(project(path = ":boot-db-pool-hikari", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot-db-pool-hikari", configuration = "koinElements"))
    testImplementation(project(path = ":boot-db-pool-hikari", configuration = "inaraElements"))
    testImplementation(project(path = ":boot-db-pool-hikari", configuration = "metroElements"))
    testImplementation(project(":boot-logging-log4j2"))
  }

  // the tests compose the inara flavor into a module of their own, as an application does
  configurations["testImplementation"].extendsFrom(configurations["inaraApi"])
  dependencies { "kspTest"(rootProject.libs.inara.processor) }
  sourceSets.named("test") {
    compileClasspath += sourceSets["kodein"].output + sourceSets["koin"].output + sourceSets["metro"].output + sourceSets["inara"].output
    runtimeClasspath += sourceSets["kodein"].output + sourceSets["koin"].output + sourceSets["metro"].output + sourceSets["inara"].output
  }
}

project("boot-db-jdbc-mysql") {
  configureDiVariants()
  dependencies {
    // the kodein and koin flavors bind their config with config-common's helpers
    "kodeinApi"(project(path = ":boot-config-common", configuration = "kodeinElements"))
    "koinApi"(project(path = ":boot-config-common", configuration = "koinElements"))
    api(project(":boot-db-jdbc"))
    // the driver is the point of this module: depending on it brings the right JDBC driver transitively
    api(rootProject.libs.mysql.connector.j)
    // Flyway needs its MySQL database module for MySQL migrations; harmless without boot-db-flyway
    runtimeOnly(rootProject.libs.flyway.mysql)
    "metroApi"(project(path = ":boot", configuration = "metroElements"))
    "metroApi"(project(path = ":boot-db-jdbc", configuration = "metroElements"))
    "kodeinApi"(project(path = ":boot-db-jdbc", configuration = "kodeinElements"))
    "koinApi"(project(path = ":boot-db-jdbc", configuration = "koinElements"))
    "inaraApi"(project(path = ":boot-db-jdbc", configuration = "inaraElements"))

    // the tests exercise this module's kodein, koin, metro, and inara flavors too, wired up as a consumer would
    testImplementation(project(path = ":boot", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot", configuration = "koinElements"))
    testImplementation(project(path = ":boot", configuration = "inaraElements"))
    testImplementation(project(path = ":boot", configuration = "metroElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "koinElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "inaraElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "metroElements"))
    testImplementation(project(path = ":boot-db-jdbc", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot-db-jdbc", configuration = "koinElements"))
    testImplementation(project(path = ":boot-db-jdbc", configuration = "inaraElements"))
    testImplementation(project(path = ":boot-db-jdbc", configuration = "metroElements"))
    // the driver settings only matter through a real pool, and Flyway proves its database module is there
    testImplementation(project(path = ":boot-db-pool-hikari", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot-db-pool-hikari", configuration = "koinElements"))
    testImplementation(project(path = ":boot-db-pool-hikari", configuration = "inaraElements"))
    testImplementation(project(path = ":boot-db-pool-hikari", configuration = "metroElements"))
    testImplementation(project(":boot-db-flyway"))
    testImplementation(project(":boot-logging-log4j2"))
    // a real MySQL in a container; those tests are skipped where Docker is unavailable, outside CI
    testImplementation(rootProject.libs.testcontainers.mysql)
  }

  // the tests compose the inara flavor into a module of their own, as an application does
  configurations["testImplementation"].extendsFrom(configurations["inaraApi"])
  dependencies { "kspTest"(rootProject.libs.inara.processor) }
  sourceSets.named("test") {
    compileClasspath += sourceSets["kodein"].output + sourceSets["koin"].output + sourceSets["metro"].output + sourceSets["inara"].output
    runtimeClasspath += sourceSets["kodein"].output + sourceSets["koin"].output + sourceSets["metro"].output + sourceSets["inara"].output
  }
}

project("boot-db-jdbc-postgresql") {
  configureDiVariants()
  dependencies {
    // the kodein and koin flavors bind their config with config-common's helpers
    "kodeinApi"(project(path = ":boot-config-common", configuration = "kodeinElements"))
    "koinApi"(project(path = ":boot-config-common", configuration = "koinElements"))
    api(project(":boot-db-jdbc"))
    // the driver is the point of this module: depending on it brings the right JDBC driver transitively
    api(rootProject.libs.postgresql)
    // Flyway needs its PostgreSQL database module for PostgreSQL migrations; harmless without boot-db-flyway
    runtimeOnly(rootProject.libs.flyway.database.postgresql)
    "metroApi"(project(path = ":boot", configuration = "metroElements"))
    "metroApi"(project(path = ":boot-db-jdbc", configuration = "metroElements"))
    "kodeinApi"(project(path = ":boot-db-jdbc", configuration = "kodeinElements"))
    "koinApi"(project(path = ":boot-db-jdbc", configuration = "koinElements"))
    "inaraApi"(project(path = ":boot-db-jdbc", configuration = "inaraElements"))

    // the tests exercise this module's kodein, koin, metro, and inara flavors too, wired up as a consumer would
    testImplementation(project(path = ":boot", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot", configuration = "koinElements"))
    testImplementation(project(path = ":boot", configuration = "inaraElements"))
    testImplementation(project(path = ":boot", configuration = "metroElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "koinElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "inaraElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "metroElements"))
    testImplementation(project(path = ":boot-db-jdbc", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot-db-jdbc", configuration = "koinElements"))
    testImplementation(project(path = ":boot-db-jdbc", configuration = "inaraElements"))
    testImplementation(project(path = ":boot-db-jdbc", configuration = "metroElements"))
    // the driver settings only matter through a real pool, and Flyway proves its database module is there
    testImplementation(project(path = ":boot-db-pool-hikari", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot-db-pool-hikari", configuration = "koinElements"))
    testImplementation(project(path = ":boot-db-pool-hikari", configuration = "inaraElements"))
    testImplementation(project(path = ":boot-db-pool-hikari", configuration = "metroElements"))
    testImplementation(project(":boot-db-flyway"))
    testImplementation(project(":boot-logging-log4j2"))
    // a real PostgreSQL in a container; those tests are skipped where Docker is unavailable, outside CI
    testImplementation(rootProject.libs.testcontainers.postgresql)
  }

  // the tests compose the inara flavor into a module of their own, as an application does
  configurations["testImplementation"].extendsFrom(configurations["inaraApi"])
  dependencies { "kspTest"(rootProject.libs.inara.processor) }
  sourceSets.named("test") {
    compileClasspath += sourceSets["kodein"].output + sourceSets["koin"].output + sourceSets["metro"].output + sourceSets["inara"].output
    runtimeClasspath += sourceSets["kodein"].output + sourceSets["koin"].output + sourceSets["metro"].output + sourceSets["inara"].output
  }
}

project("boot-db-exposed") {
  configureDiVariants()
  dependencies {
    // the kodein and koin flavors bind their config with config-common's helpers
    "kodeinApi"(project(path = ":boot-config-common", configuration = "kodeinElements"))
    "koinApi"(project(path = ":boot-config-common", configuration = "koinElements"))
    // runSuspendCatching
    testImplementation(project(":boot-test"))
    api(project(":boot-db-jdbc"))
    api(rootProject.libs.bundles.exposed)
    // the logging API only: the application chooses the logging implementation (e.g. boot-logging-log4j2)
    implementation(rootProject.libs.bundles.log4j.api)
    // Exposed needs boot-db-jdbc's DataSource, so each flavor brings boot-db-jdbc's matching flavor
    "metroApi"(project(path = ":boot", configuration = "metroElements"))
    "metroApi"(project(path = ":boot-db-jdbc", configuration = "metroElements"))
    "kodeinApi"(project(path = ":boot-db-jdbc", configuration = "kodeinElements"))
    "koinApi"(project(path = ":boot-db-jdbc", configuration = "koinElements"))
    "inaraApi"(project(path = ":boot-db-jdbc", configuration = "inaraElements"))

    // the tests exercise this module's kodein, koin, metro, and inara flavors too, wired up as a consumer would, against a real
    // SQLite database, with Flyway creating the schema that is verified
    testImplementation(project(path = ":boot", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot", configuration = "koinElements"))
    testImplementation(project(path = ":boot", configuration = "inaraElements"))
    testImplementation(project(path = ":boot", configuration = "metroElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "koinElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "inaraElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "metroElements"))
    testImplementation(project(path = ":boot-db-jdbc", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot-db-jdbc", configuration = "koinElements"))
    testImplementation(project(path = ":boot-db-jdbc", configuration = "inaraElements"))
    testImplementation(project(path = ":boot-db-jdbc", configuration = "metroElements"))
    testImplementation(project(path = ":boot-db-jdbc-sqlite", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot-db-jdbc-sqlite", configuration = "koinElements"))
    testImplementation(project(path = ":boot-db-jdbc-sqlite", configuration = "inaraElements"))
    testImplementation(project(path = ":boot-db-jdbc-sqlite", configuration = "metroElements"))
    testImplementation(project(path = ":boot-db-pool-hikari", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot-db-pool-hikari", configuration = "koinElements"))
    testImplementation(project(path = ":boot-db-pool-hikari", configuration = "inaraElements"))
    testImplementation(project(path = ":boot-db-pool-hikari", configuration = "metroElements"))
    testImplementation(project(path = ":boot-db-flyway", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot-db-flyway", configuration = "koinElements"))
    testImplementation(project(path = ":boot-db-flyway", configuration = "inaraElements"))
    testImplementation(project(path = ":boot-db-flyway", configuration = "metroElements"))
    testImplementation(project(":boot-logging-log4j2"))
  }

  // the tests compose the inara flavor into a module of their own, as an application does
  configurations["testImplementation"].extendsFrom(configurations["inaraApi"])
  dependencies { "kspTest"(rootProject.libs.inara.processor) }
  sourceSets.named("test") {
    compileClasspath += sourceSets["kodein"].output + sourceSets["koin"].output + sourceSets["metro"].output + sourceSets["inara"].output
    runtimeClasspath += sourceSets["kodein"].output + sourceSets["koin"].output + sourceSets["metro"].output + sourceSets["inara"].output
  }
}

project("boot-db-flyway") {
  configureDiVariants()
  dependencies {
    // the kodein and koin flavors bind their config with config-common's helpers
    "kodeinApi"(project(path = ":boot-config-common", configuration = "kodeinElements"))
    "koinApi"(project(path = ":boot-config-common", configuration = "koinElements"))
    api(project(":boot-db-jdbc"))
    // flyway-core supports SQLite itself; other databases' Flyway modules come with their driver modules
    // (boot-db-jdbc-postgresql, boot-db-jdbc-mysql)
    api(rootProject.libs.flyway.core)
    // the logging API only: the application chooses the logging implementation (e.g. boot-logging-log4j2)
    implementation(rootProject.libs.bundles.log4j.api)
    // migrations need boot-db-jdbc's DataSource, so each flavor brings boot-db-jdbc's matching flavor
    "metroApi"(project(path = ":boot", configuration = "metroElements"))
    "metroApi"(project(path = ":boot-db-jdbc", configuration = "metroElements"))
    "kodeinApi"(project(path = ":boot-db-jdbc", configuration = "kodeinElements"))
    "koinApi"(project(path = ":boot-db-jdbc", configuration = "koinElements"))
    "inaraApi"(project(path = ":boot-db-jdbc", configuration = "inaraElements"))

    // the tests exercise this module's kodein, koin, metro, and inara flavors too, wired up as a consumer would, against a real
    // SQLite database
    testImplementation(project(path = ":boot", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot", configuration = "koinElements"))
    testImplementation(project(path = ":boot", configuration = "inaraElements"))
    testImplementation(project(path = ":boot", configuration = "metroElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "koinElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "inaraElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "metroElements"))
    testImplementation(project(path = ":boot-db-jdbc", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot-db-jdbc", configuration = "koinElements"))
    testImplementation(project(path = ":boot-db-jdbc", configuration = "inaraElements"))
    testImplementation(project(path = ":boot-db-jdbc", configuration = "metroElements"))
    testImplementation(project(path = ":boot-db-jdbc-sqlite", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot-db-jdbc-sqlite", configuration = "koinElements"))
    testImplementation(project(path = ":boot-db-jdbc-sqlite", configuration = "inaraElements"))
    testImplementation(project(path = ":boot-db-jdbc-sqlite", configuration = "metroElements"))
    testImplementation(project(path = ":boot-db-pool-hikari", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot-db-pool-hikari", configuration = "koinElements"))
    testImplementation(project(path = ":boot-db-pool-hikari", configuration = "inaraElements"))
    testImplementation(project(path = ":boot-db-pool-hikari", configuration = "metroElements"))
    testImplementation(project(":boot-logging-log4j2"))
  }

  // the tests compose the inara flavor into a module of their own, as an application does
  configurations["testImplementation"].extendsFrom(configurations["inaraApi"])
  dependencies { "kspTest"(rootProject.libs.inara.processor) }
  sourceSets.named("test") {
    compileClasspath += sourceSets["kodein"].output + sourceSets["koin"].output + sourceSets["metro"].output + sourceSets["inara"].output
    runtimeClasspath += sourceSets["kodein"].output + sourceSets["koin"].output + sourceSets["metro"].output + sourceSets["inara"].output
  }
}

project("boot-env") {
  configureDiVariants()
  dependencies {
    // the kodein and koin flavors bind their config with config-common's helpers
    "kodeinApi"(project(path = ":boot-config-common", configuration = "kodeinElements"))
    "koinApi"(project(path = ":boot-config-common", configuration = "koinElements"))
    api(project(":boot-config-common"))
    implementation(project(":boot"))
    // the metro flavor contributes to boot's BootScope (in-repo: boot's metro source set; POM: boot-metro)
    "metroApi"(project(path = ":boot", configuration = "metroElements"))
    // the inara flavor marks its initializer with boot's LifecycleControllers, and reads config-common's ConfigModule
    "inaraApi"(project(path = ":boot", configuration = "inaraElements"))
    "inaraApi"(project(path = ":boot-config-common", configuration = "inaraElements"))
  }

  // the tests compose the inara flavor into a module of their own, as an application does, and exercise the koin
  // flavor, wired up as a consumer would
  configurations["testImplementation"].extendsFrom(configurations["inaraApi"], configurations["koinApi"])
  dependencies {
    "kspTest"(rootProject.libs.inara.processor)
    testImplementation(project(path = ":boot", configuration = "koinElements"))
  }
  sourceSets.named("test") {
    compileClasspath += sourceSets["koin"].output + sourceSets["inara"].output
    runtimeClasspath += sourceSets["koin"].output + sourceSets["inara"].output
  }
}

// LeaderElection, the role a periodic loop's gate asks about, with no implementation beyond AlwaysLeader
project("boot-leader-common") {
  // an inara flavor only: the small module that the other inara modules require
  configureDiVariants()
  dependencies {
    // leadership is a StateFlow
    api(rootProject.libs.kotlinx.coroutines.core)
  }
}

// a LeaderElection that holds a Kubernetes Lease, through the API server's REST API, with a boot-client-http-ktor client
project("boot-leader-k8s") {
  apply(plugin = "org.jetbrains.kotlin.plugin.serialization")
  configureDiVariants()
  dependencies {
    // the kodein and koin flavors bind their config with config-common's helpers
    "kodeinApi"(project(path = ":boot-config-common", configuration = "kodeinElements"))
    "koinApi"(project(path = ":boot-config-common", configuration = "koinElements"))
    // KubernetesLeaderElection is an AppService and a LeaderElection
    api(project(":boot"))
    api(project(":boot-config-common"))
    api(project(":boot-leader-common"))
    // the API server is called with one of the application's Ktor clients; the engine is the application's choice
    api(project(":boot-client-http-ktor"))
    // the Lease, as JSON: generated serializers, with no reflection, so nothing to add for a native image
    implementation(rootProject.libs.kotlinx.serialization.json)
    // the logging API only: the application chooses the logging implementation (e.g. boot-logging-log4j2)
    implementation(rootProject.libs.bundles.log4j.api)
    // each flavor takes the client from boot-client-http-ktor's service, so it brings that module's matching flavor
    "metroApi"(project(path = ":boot", configuration = "metroElements"))
    "metroApi"(project(path = ":boot-client-http-ktor", configuration = "metroElements"))
    "kodeinApi"(project(path = ":boot-client-http-ktor", configuration = "kodeinElements"))
    "koinApi"(project(path = ":boot-client-http-ktor", configuration = "koinElements"))
    "inaraApi"(project(path = ":boot-client-http-ktor", configuration = "inaraElements"))
    "inaraApi"(project(path = ":boot-leader-common", configuration = "inaraElements"))

    // the election runs against a fake API server, through the Java engine
    testImplementation(project(":boot-client-http-ktor-java"))
    // the tests exercise this module's kodein, koin, metro, and inara flavors too, wired up as a consumer would
    testImplementation(project(path = ":boot", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot", configuration = "koinElements"))
    testImplementation(project(path = ":boot", configuration = "inaraElements"))
    testImplementation(project(path = ":boot", configuration = "metroElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "koinElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "inaraElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "metroElements"))
    testImplementation(project(path = ":boot-client-http-ktor", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot-client-http-ktor", configuration = "koinElements"))
    testImplementation(project(path = ":boot-client-http-ktor", configuration = "inaraElements"))
    testImplementation(project(path = ":boot-client-http-ktor", configuration = "metroElements"))
    testImplementation(project(":boot-logging-log4j2"))
  }

  // the tests compose the inara flavor into a module of their own, as an application does
  configurations["testImplementation"].extendsFrom(configurations["inaraApi"])
  dependencies { "kspTest"(rootProject.libs.inara.processor) }
  sourceSets.named("test") {
    compileClasspath += sourceSets["kodein"].output + sourceSets["koin"].output + sourceSets["metro"].output + sourceSets["inara"].output
    runtimeClasspath += sourceSets["kodein"].output + sourceSets["koin"].output + sourceSets["metro"].output + sourceSets["inara"].output
  }
}

// PeriodicAppService: a coroutine loop, gated, e.g. by leadership, and traced through interceptors
project("boot-periodic") {
  configureDiVariants()
  dependencies {
    // PeriodicAppService is an AppService, and its gates ask boot's HealthLifecycleController and
    // boot-leader-common's LeaderElection
    api(project(":boot"))
    api(project(":boot-leader-common"))
    api(rootProject.libs.kotlinx.coroutines.core)
    // periodicTracing, over the OpenTelemetry API only: the SDK, if any, is the application's choice
    api(project(":boot-telemetry-otel-common"))
    // the logging API only: the application chooses the logging implementation (e.g. boot-logging-log4j2)
    implementation(rootProject.libs.bundles.log4j.api)
    "metroApi"(project(path = ":boot", configuration = "metroElements"))
    // the inara flavor requires a LeaderElectionModule and an OpenTelemetryApiModule
    "inaraApi"(project(path = ":boot-leader-common", configuration = "inaraElements"))
    "inaraApi"(project(path = ":boot-telemetry-otel-common", configuration = "inaraElements"))

    // the tests exercise this module's kodein, koin, metro, and inara flavors too, wired up as a consumer would
    testImplementation(project(path = ":boot", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot", configuration = "koinElements"))
    testImplementation(project(path = ":boot", configuration = "inaraElements"))
    testImplementation(project(path = ":boot", configuration = "metroElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "koinElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "inaraElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "metroElements"))
    // the tracing tests record spans with a real SDK. Not its Metro flavor, which would contribute the SDK to every
    // test graph, so the Metro tracing test binds the OpenTelemetry itself
    testImplementation(project(path = ":boot-telemetry-otel", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot-telemetry-otel", configuration = "koinElements"))
    testImplementation(project(path = ":boot-telemetry-otel", configuration = "inaraElements"))
    testImplementation(rootProject.libs.opentelemetry.sdk.testing)
    testImplementation(project(":boot-logging-log4j2"))
  }

  // the tests compose the inara flavor into a module of their own, as an application does
  configurations["testImplementation"].extendsFrom(configurations["inaraApi"])
  dependencies { "kspTest"(rootProject.libs.inara.processor) }
  sourceSets.named("test") {
    compileClasspath += sourceSets["kodein"].output + sourceSets["koin"].output + sourceSets["metro"].output + sourceSets["inara"].output
    runtimeClasspath += sourceSets["kodein"].output + sourceSets["koin"].output + sourceSets["metro"].output + sourceSets["inara"].output
  }
}

// persistent scheduled tasks with db-scheduler, over boot-db-jdbc's DataSource
project("boot-scheduler-db") {
  apply(plugin = "org.jetbrains.kotlin.plugin.serialization")
  configureDiVariants()
  dependencies {
    // the kodein and koin flavors bind their config with config-common's helpers
    "kodeinApi"(project(path = ":boot-config-common", configuration = "kodeinElements"))
    "koinApi"(project(path = ":boot-config-common", configuration = "koinElements"))
    // DbSchedulerService is an AppService over boot-db-jdbc's DataSource, ordered after its milestones
    api(project(":boot-db-jdbc"))
    api(rootProject.libs.db.scheduler)
    // task data as JSON, and SerializableSchedule
    api(rootProject.libs.kotlinx.serialization.json)
    // dbSchedulerTracing, over the OpenTelemetry API only: the SDK, if any, is the application's choice
    api(project(":boot-telemetry-otel-common"))
    // the scheduler needs boot-db-jdbc's DataSource, so each flavor brings boot-db-jdbc's matching flavor
    "metroApi"(project(path = ":boot", configuration = "metroElements"))
    "metroApi"(project(path = ":boot-db-jdbc", configuration = "metroElements"))
    "kodeinApi"(project(path = ":boot-db-jdbc", configuration = "kodeinElements"))
    "koinApi"(project(path = ":boot-db-jdbc", configuration = "koinElements"))
    "inaraApi"(project(path = ":boot-db-jdbc", configuration = "inaraElements"))
    "inaraApi"(project(path = ":boot-telemetry-otel-common", configuration = "inaraElements"))

    // the tests run the scheduler against real databases, migrated by Flyway with this module's migrations: SQLite,
    // and PostgreSQL and MySQL in containers, which are skipped where Docker is unavailable, outside CI
    testImplementation(project(path = ":boot", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot", configuration = "koinElements"))
    testImplementation(project(path = ":boot", configuration = "inaraElements"))
    testImplementation(project(path = ":boot", configuration = "metroElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "koinElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "inaraElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "metroElements"))
    testImplementation(project(path = ":boot-db-jdbc", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot-db-jdbc", configuration = "koinElements"))
    testImplementation(project(path = ":boot-db-jdbc", configuration = "inaraElements"))
    testImplementation(project(path = ":boot-db-jdbc", configuration = "metroElements"))
    testImplementation(project(path = ":boot-db-jdbc-sqlite", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot-db-jdbc-sqlite", configuration = "koinElements"))
    testImplementation(project(path = ":boot-db-jdbc-sqlite", configuration = "inaraElements"))
    testImplementation(project(path = ":boot-db-jdbc-sqlite", configuration = "metroElements"))
    testImplementation(project(path = ":boot-db-pool-hikari", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot-db-pool-hikari", configuration = "koinElements"))
    testImplementation(project(path = ":boot-db-pool-hikari", configuration = "inaraElements"))
    testImplementation(project(path = ":boot-db-pool-hikari", configuration = "metroElements"))
    testImplementation(project(path = ":boot-db-flyway", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot-db-flyway", configuration = "koinElements"))
    testImplementation(project(path = ":boot-db-flyway", configuration = "inaraElements"))
    testImplementation(project(path = ":boot-db-flyway", configuration = "metroElements"))
    testImplementation(project(":boot-db-jdbc-postgresql"))
    testImplementation(project(":boot-db-jdbc-mysql"))
    // the tracing tests record spans with a real SDK, with the scheduler's statements traced too, to check that an
    // execution's statements, and its completion's, belong to the execution's span. Not the SDK's Metro
    // flavor, which would contribute the SDK to every test graph, so the Metro tracing test binds the OpenTelemetry
    // itself
    testImplementation(project(path = ":boot-telemetry-otel", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot-telemetry-otel", configuration = "koinElements"))
    testImplementation(project(path = ":boot-telemetry-otel", configuration = "inaraElements"))
    testImplementation(rootProject.libs.opentelemetry.sdk.testing)
    testImplementation(project(":boot-db-jdbc-otel"))
    testImplementation(project(":boot-logging-log4j2"))
    testImplementation(rootProject.libs.testcontainers.postgresql)
    testImplementation(rootProject.libs.testcontainers.mysql)
  }

  // the tests compose the inara flavor into a module of their own, as an application does
  configurations["testImplementation"].extendsFrom(configurations["inaraApi"])
  dependencies { "kspTest"(rootProject.libs.inara.processor) }
  sourceSets.named("test") {
    compileClasspath += sourceSets["kodein"].output + sourceSets["koin"].output + sourceSets["metro"].output + sourceSets["inara"].output
    runtimeClasspath += sourceSets["kodein"].output + sourceSets["koin"].output + sourceSets["metro"].output + sourceSets["inara"].output
  }
}

project("boot-logging")

project("boot-logging-log4j2") {
  dependencies {
    api(project(":boot-logging"))
    api(rootProject.libs.bundles.log4j.api)
    // log4j-api-kotlin's logger() resolves the logger name via this::class (kotlin-reflect), so every app that
    // logs needs kotlin-reflect at runtime — independent of any DI framework. (In apps that also use ktor or
    // hoplite it arrives transitively, which is why a bare example is the first to notice it missing.)
    api(rootProject.libs.kotlin.reflect)
    implementation(platform(rootProject.libs.log4j.bom))
    implementation(rootProject.libs.bundles.log4j.impl)
    testImplementation(rootProject.libs.kotlinx.serialization.json)
  }
}

project("boot-server-http-ktor") {
  configureDiVariants()
  dependencies {
    // KtorService is an AppService, so boot is part of this module's API
    api(project(":boot"))
    api(project(":boot-config-common"))
    // engine-agnostic: the engine comes from one of the boot-server-http-ktor-<engine> modules, or the application
    api(rootProject.libs.ktor.server.core)
    // the health routes' JSON bodies
    implementation(rootProject.libs.kotlinx.serialization.json)
    // the flavors contribute KtorHealth, and its listener of its own, to boot's BootScope and app services
    "metroApi"(project(path = ":boot", configuration = "metroElements"))
    "kodeinApi"(project(path = ":boot", configuration = "kodeinElements"))
    "koinApi"(project(path = ":boot", configuration = "koinElements"))
    // the inara flavor requires boot's BootableModule, for its HealthLifecycleController and BuildInfo
    "inaraApi"(project(path = ":boot", configuration = "inaraElements"))

    // the lifecycle tests run a real server on each engine
    testImplementation(rootProject.libs.ktor.server.cio)
    testImplementation(rootProject.libs.ktor.server.netty)
    testImplementation(project(":boot-logging-log4j2"))
    // StaticConfigBinder, for the wiring tests' config
    testImplementation(project(":boot-test"))
    // the tests exercise this module's kodein, koin, metro, and inara flavors too, wired up as a consumer would
    testImplementation(project(path = ":boot", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot", configuration = "koinElements"))
    testImplementation(project(path = ":boot", configuration = "inaraElements"))
    testImplementation(project(path = ":boot", configuration = "metroElements"))
  }

  // the tests compose the inara flavor into a module of their own, as an application does
  configurations["testImplementation"].extendsFrom(configurations["inaraApi"])
  dependencies { "kspTest"(rootProject.libs.inara.processor) }
  sourceSets.named("test") {
    compileClasspath += sourceSets["kodein"].output + sourceSets["koin"].output + sourceSets["metro"].output + sourceSets["inara"].output
    runtimeClasspath += sourceSets["kodein"].output + sourceSets["koin"].output + sourceSets["metro"].output + sourceSets["inara"].output
  }
}

// Each engine module is a thin, non-generic KtorService for its engine, and brings that engine transitively. Each
// flavor provides the engine as the application's KtorServerEngine, for the listeners the other modules start
listOf(
  "cio" to rootProject.libs.ktor.server.cio,
  "netty" to rootProject.libs.ktor.server.netty,
).forEach { (engine, engineLibrary) ->
  project("boot-server-http-ktor-$engine") {
    configureDiVariants()
    dependencies {
      api(project(":boot-server-http-ktor"))
      api(engineLibrary)
      // only boot's BootScope: not boot-server-http-ktor's flavor, whose Metro bindings would then be contributed
      // to every graph with an engine
      "metroApi"(project(path = ":boot", configuration = "metroElements"))
      // the inara flavor's engine is a KtorServerEngineModule, which an Inara application composes explicitly
      "inaraApi"(project(path = ":boot-server-http-ktor", configuration = "inaraElements"))

      testImplementation(project(":boot-logging-log4j2"))
      // the tests exercise this module's kodein, koin, metro, and inara flavors too, wired up as a consumer would
      testImplementation(project(path = ":boot", configuration = "kodeinElements"))
      testImplementation(project(path = ":boot", configuration = "koinElements"))
      testImplementation(project(path = ":boot", configuration = "metroElements"))
      testImplementation(project(path = ":boot-server-http-ktor", configuration = "kodeinElements"))
      testImplementation(project(path = ":boot-server-http-ktor", configuration = "koinElements"))
      testImplementation(project(path = ":boot-server-http-ktor", configuration = "metroElements"))
    }

    configurations["testImplementation"].extendsFrom(configurations["inaraApi"])
    sourceSets.named("test") {
      compileClasspath += sourceSets["kodein"].output + sourceSets["koin"].output + sourceSets["metro"].output + sourceSets["inara"].output
      runtimeClasspath += sourceSets["kodein"].output + sourceSets["koin"].output + sourceSets["metro"].output + sourceSets["inara"].output
    }
  }
}

project("boot-telemetry-otel") {
  configureDiVariants()
  dependencies {
    // runSuspendCatching
    testImplementation(project(":boot-test"))
    // OpenTelemetryService is an AppService, and the OpenTelemetry instance is the module's whole point
    api(project(":boot"))
    api(project(":boot-config-common"))
    // withSpan and the like, and ALWAYS_SAMPLE, which this SDK's sampler honors
    api(project(":boot-telemetry-otel-common"))
    api(rootProject.libs.opentelemetry.sdk)
    // OTLP over HTTP with the JDK's own client, rather than OkHttp, which would bring its own Kotlin stdlib
    api(rootProject.libs.opentelemetry.exporter.otlp) {
      exclude(group = "io.opentelemetry", module = "opentelemetry-exporter-sender-okhttp")
    }
    runtimeOnly(rootProject.libs.opentelemetry.exporter.sender.jdk)
    api(rootProject.libs.opentelemetry.extension.kotlin)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${rootProject.libs.versions.kotlinx.coroutines.get()}")
    implementation(rootProject.libs.opentelemetry.instrumentation.runtime.telemetry)
    // puts the current trace and span ids in every log4j2 event's context data, where Bootable's layouts find them
    runtimeOnly(rootProject.libs.opentelemetry.instrumentation.log4j.context.data)
    // the logging API only: the application chooses the logging implementation (e.g. boot-logging-log4j2)
    implementation(rootProject.libs.bundles.log4j.api)
    "metroApi"(project(path = ":boot", configuration = "metroElements"))
    // the inara flavor adds its service to boot's set, reads its config, and provides the OpenTelemetryApiModule
    "inaraApi"(project(path = ":boot", configuration = "inaraElements"))
    "inaraApi"(project(path = ":boot-config-common", configuration = "inaraElements"))
    "inaraApi"(project(path = ":boot-telemetry-otel-common", configuration = "inaraElements"))

    testImplementation(rootProject.libs.opentelemetry.sdk.testing)
    // to check that log events in a span get its trace and span ids, as Bootable's layouts expect
    testImplementation(rootProject.libs.log4j.core)
    testImplementation(project(":boot-test"))
    // the tests exercise this module's kodein, koin, metro, and inara flavors too, wired up as a consumer would
    testImplementation(project(path = ":boot", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot", configuration = "koinElements"))
    testImplementation(project(path = ":boot", configuration = "inaraElements"))
    testImplementation(project(path = ":boot", configuration = "metroElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "koinElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "inaraElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "metroElements"))
    testImplementation(project(":boot-logging-log4j2"))
  }

  // the tests compose the inara flavor into a module of their own, as an application does
  configurations["testImplementation"].extendsFrom(configurations["inaraApi"])
  dependencies { "kspTest"(rootProject.libs.inara.processor) }
  sourceSets.named("test") {
    compileClasspath += sourceSets["kodein"].output + sourceSets["koin"].output + sourceSets["metro"].output + sourceSets["inara"].output
    runtimeClasspath += sourceSets["kodein"].output + sourceSets["koin"].output + sourceSets["metro"].output + sourceSets["inara"].output
  }
}

// what Bootable's telemetry shares, over the OpenTelemetry API only, for modules and applications that trace
// without depending on an SDK: with none, the API is a no-op
project("boot-telemetry-otel-common") {
  // an inara flavor only: the small module that the other inara modules require
  configureDiVariants()
  dependencies {
    api(rootProject.libs.opentelemetry.api)
    // the span's context as a coroutine context element, so withSpan's span follows its block across dispatchers
    api(rootProject.libs.opentelemetry.extension.kotlin)
    implementation(rootProject.libs.kotlinx.coroutines.core)
    // the logging API only: the application chooses the logging implementation (e.g. boot-logging-log4j2)
    implementation(rootProject.libs.bundles.log4j.api)
  }
}

// Each of these plugs into boot-telemetry-otel's SDK through its OpenTelemetryCustomizer set, or builds on its
// OpenTelemetry instance
mapOf(
  // the GCP resource detector is an SDK autoconfigure provider, so it needs the autoconfigure SPI to load
  "gcp" to listOf(
    rootProject.libs.opentelemetry.gcp.resources,
    rootProject.libs.opentelemetry.sdk.extension.autoconfigure.spi,
    rootProject.libs.google.auth.library.oauth2.http,
  ),
  "micrometer-bridge" to listOf(rootProject.libs.micrometer.core, rootProject.libs.opentelemetry.instrumentation.micrometer),
  "prometheus" to listOf(rootProject.libs.opentelemetry.exporter.prometheus, rootProject.libs.prometheus.metrics.exposition.textformats),
  // SigNoz ingests OTLP, so it needs nothing beyond the SDK's exporters
  "signoz" to emptyList(),
).forEach { (name, libraries) ->
  project("boot-telemetry-otel-$name") {
    configureDiVariants()
    dependencies {
      api(project(":boot-telemetry-otel"))
      libraries.forEach { api(it) }
      // only useful with boot-telemetry-otel's SDK, so each flavor brings its matching flavor
      "metroApi"(project(path = ":boot", configuration = "metroElements"))
      "metroApi"(project(path = ":boot-telemetry-otel", configuration = "metroElements"))
      "kodeinApi"(project(path = ":boot-telemetry-otel", configuration = "kodeinElements"))
      "koinApi"(project(path = ":boot-telemetry-otel", configuration = "koinElements"))
      "inaraApi"(project(path = ":boot-telemetry-otel", configuration = "inaraElements"))
      // the kodein and koin flavors that bind config do it with config-common's helpers
      if (name != "micrometer-bridge") {
        "kodeinApi"(project(path = ":boot-config-common", configuration = "kodeinElements"))
        "koinApi"(project(path = ":boot-config-common", configuration = "koinElements"))
      }

      testImplementation(rootProject.libs.opentelemetry.sdk.testing)
      // the tests exercise this module's kodein, koin, metro, and inara flavors too, wired up as a consumer would
      testImplementation(project(path = ":boot", configuration = "kodeinElements"))
      testImplementation(project(path = ":boot", configuration = "koinElements"))
      testImplementation(project(path = ":boot", configuration = "inaraElements"))
      testImplementation(project(path = ":boot", configuration = "metroElements"))
      testImplementation(project(path = ":boot-config-hoplite", configuration = "kodeinElements"))
      testImplementation(project(path = ":boot-config-hoplite", configuration = "koinElements"))
      testImplementation(project(path = ":boot-config-hoplite", configuration = "inaraElements"))
      testImplementation(project(path = ":boot-config-hoplite", configuration = "metroElements"))
      testImplementation(project(path = ":boot-telemetry-otel", configuration = "kodeinElements"))
      testImplementation(project(path = ":boot-telemetry-otel", configuration = "koinElements"))
      testImplementation(project(path = ":boot-telemetry-otel", configuration = "inaraElements"))
      testImplementation(project(path = ":boot-telemetry-otel", configuration = "metroElements"))
      testImplementation(project(":boot-logging-log4j2"))
    }

    // the tests compose the inara flavor into a module of their own, as an application does
    configurations["testImplementation"].extendsFrom(configurations["inaraApi"])
    dependencies { "kspTest"(rootProject.libs.inara.processor) }
    sourceSets.named("test") {
      compileClasspath += sourceSets["kodein"].output + sourceSets["koin"].output + sourceSets["metro"].output + sourceSets["inara"].output
      runtimeClasspath += sourceSets["kodein"].output + sourceSets["koin"].output + sourceSets["metro"].output + sourceSets["inara"].output
    }
  }
}

project("boot-server-http-ktor-otel") {
  configureDiVariants()
  dependencies {
    // the kodein and koin flavors bind their config with config-common's helpers
    "kodeinApi"(project(path = ":boot-config-common", configuration = "kodeinElements"))
    "koinApi"(project(path = ":boot-config-common", configuration = "koinElements"))
    api(project(":boot-server-http-ktor"))
    api(project(":boot-telemetry-otel"))
    api(rootProject.libs.opentelemetry.instrumentation.ktor)
    "metroApi"(project(path = ":boot", configuration = "metroElements"))
    "metroApi"(project(path = ":boot-telemetry-otel", configuration = "metroElements"))
    "kodeinApi"(project(path = ":boot", configuration = "kodeinElements"))
    "koinApi"(project(path = ":boot", configuration = "koinElements"))
    "kodeinApi"(project(path = ":boot-telemetry-otel", configuration = "kodeinElements"))
    "koinApi"(project(path = ":boot-telemetry-otel", configuration = "koinElements"))
    "inaraApi"(project(path = ":boot-telemetry-otel", configuration = "inaraElements"))
    // the inara flavor requires the KtorServerEngineModule the listener of its own runs on
    "inaraApi"(project(path = ":boot-server-http-ktor", configuration = "inaraElements"))

    // requests are traced and scraped through a real server, with a real Prometheus reader behind it
    testImplementation(project(":boot-server-http-ktor-cio"))
    testImplementation(project(":boot-telemetry-otel-prometheus"))
    testImplementation(rootProject.libs.opentelemetry.sdk.testing)
    testImplementation(project(path = ":boot", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot", configuration = "koinElements"))
    testImplementation(project(path = ":boot", configuration = "inaraElements"))
    testImplementation(project(path = ":boot", configuration = "metroElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "koinElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "inaraElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "metroElements"))
    testImplementation(project(path = ":boot-telemetry-otel", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot-telemetry-otel", configuration = "koinElements"))
    testImplementation(project(path = ":boot-telemetry-otel", configuration = "inaraElements"))
    testImplementation(project(path = ":boot-telemetry-otel", configuration = "metroElements"))
    testImplementation(project(path = ":boot-telemetry-otel-prometheus", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot-telemetry-otel-prometheus", configuration = "koinElements"))
    testImplementation(project(path = ":boot-telemetry-otel-prometheus", configuration = "inaraElements"))
    testImplementation(project(path = ":boot-telemetry-otel-prometheus", configuration = "metroElements"))
    testImplementation(project(":boot-logging-log4j2"))
  }

  // the tests compose the inara flavor into a module of their own, as an application does
  configurations["testImplementation"].extendsFrom(configurations["inaraApi"])
  dependencies { "kspTest"(rootProject.libs.inara.processor) }
  sourceSets.named("test") {
    compileClasspath += sourceSets["kodein"].output + sourceSets["koin"].output + sourceSets["metro"].output + sourceSets["inara"].output
    runtimeClasspath += sourceSets["kodein"].output + sourceSets["koin"].output + sourceSets["metro"].output + sourceSets["inara"].output
  }
}

// A gRPC server over grpc-java, as an AppService, for services written as coroutines, on the server bases that
// kotpb-grpc-codegen generates, over grpc-kotlin's runtime
project("boot-server-grpc") {
  configureDiVariants()
  configureGrpcProtos()
  dependencies {
    // GrpcServer is an AppService, so boot is part of this module's API
    api(project(":boot"))
    api(project(":boot-config-common"))
    // BindableService, ServerBuilder, Status: the types an application uses
    api(rootProject.libs.grpc.api)
    // the runtime of the generated stubs and server bases, AbstractCoroutineServerImpl; its grpc-stub is grpc-java
    // 1.62's, so this module's is newer, and wins
    api(rootProject.libs.grpc.kotlin.stub)
    api(rootProject.libs.grpc.stub)
    // ProtoUtils, which the generated method descriptors marshal their messages with
    api(rootProject.libs.grpc.protobuf)
    api(rootProject.libs.kotlinx.coroutines.core)
    // the server's transport, shaded, so it can't clash with an application's own Netty, such as Ktor's
    implementation(rootProject.libs.grpc.netty.shaded)
    // server reflection
    implementation(rootProject.libs.grpc.services)
    // the Kotlin DSL builders of the health service's messages
    implementation(rootProject.libs.protobuf.kotlin)
    // grpc-services' messages need protobuf-java to compile against, which grpc-java 1.x pins at 3.25: 4.x runs its
    // generated code, and an application's own, generated by protoc 4.x
    implementation(rootProject.libs.protobuf.java)
    // the caller's span, current in each call's coroutine
    implementation(rootProject.libs.opentelemetry.extension.kotlin)
    implementation(rootProject.libs.bundles.log4j.api)
    "metroApi"(project(path = ":boot", configuration = "metroElements"))
    "kodeinApi"(project(path = ":boot", configuration = "kodeinElements"))
    "koinApi"(project(path = ":boot", configuration = "koinElements"))
    // the inara flavor requires boot's BootableModule, for its HealthLifecycleController and config
    "inaraApi"(project(path = ":boot", configuration = "inaraElements"))

    testImplementation(project(":boot-test"))
    // the tests exercise this module's kodein, koin, metro, and inara flavors too, wired up as a consumer would
    testImplementation(project(path = ":boot", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot", configuration = "koinElements"))
    testImplementation(project(path = ":boot", configuration = "inaraElements"))
    testImplementation(project(path = ":boot", configuration = "metroElements"))
  }

  // the standard health service's proto, as grpc-services ships it, with its java_package moved to this module's, so
  // protoc and kotpb-grpc-codegen generate its messages, their Kotlin DSL builders, and its coroutine server base and
  // stub here, as an application generates its own, apart from grpc-services' Java classes for the same proto
  val grpcServices = configurations.detachedConfiguration(dependencies.create(rootProject.libs.grpc.services.get().toString()))
    .apply { isTransitive = false }
  val healthProto = tasks.register<Sync>("healthProto") {
    from({ zipTree(grpcServices.singleFile) }) { include("grpc/health/v1/health.proto") }
    filter { it.replace(Regex("""^option java_package = .*;$"""), """option java_package = "com.github.rocketraman.bootable.server.grpc.health";""") }
    into(layout.buildDirectory.dir("health-proto"))
  }
  (sourceSets["main"].extensions["proto"] as SourceDirectorySet).srcDir(healthProto)

  // the tests compose the inara flavor into a module of their own, as an application does
  configurations["testImplementation"].extendsFrom(configurations["inaraApi"])
  dependencies { "kspTest"(rootProject.libs.inara.processor) }
  sourceSets.named("test") {
    compileClasspath += sourceSets["kodein"].output + sourceSets["koin"].output + sourceSets["metro"].output + sourceSets["inara"].output
    runtimeClasspath += sourceSets["kodein"].output + sourceSets["koin"].output + sourceSets["metro"].output + sourceSets["inara"].output
  }
}

// traces and measures each call through the application's OpenTelemetry, as a GrpcServerCustomizer
project("boot-server-grpc-otel") {
  configureDiVariants()
  configureGrpcProtos()
  dependencies {
    api(project(":boot-server-grpc"))
    api(project(":boot-telemetry-otel"))
    api(rootProject.libs.opentelemetry.instrumentation.grpc)
    "metroApi"(project(path = ":boot", configuration = "metroElements"))
    "metroApi"(project(path = ":boot-telemetry-otel", configuration = "metroElements"))
    "kodeinApi"(project(path = ":boot", configuration = "kodeinElements"))
    "kodeinApi"(project(path = ":boot-telemetry-otel", configuration = "kodeinElements"))
    "koinApi"(project(path = ":boot", configuration = "koinElements"))
    "koinApi"(project(path = ":boot-telemetry-otel", configuration = "koinElements"))
    // the kodein and koin flavors bind their config with config-common's helpers
    "kodeinApi"(project(path = ":boot-config-common", configuration = "kodeinElements"))
    "koinApi"(project(path = ":boot-config-common", configuration = "koinElements"))
    "inaraApi"(project(path = ":boot-telemetry-otel", configuration = "inaraElements"))
    // the inara flavor adds its telemetry to the GrpcServerCustomizers of boot-server-grpc's GrpcServerModule
    "inaraApi"(project(path = ":boot-server-grpc", configuration = "inaraElements"))

    testImplementation(rootProject.libs.grpc.netty.shaded)
    testImplementation(rootProject.libs.opentelemetry.sdk.testing)
    testImplementation(project(":boot-test"))
    // the tests exercise this module's kodein, koin, metro, and inara flavors too, wired up as a consumer would, over
    // an OpenTelemetry of their own, so not boot-telemetry-otel's flavors
    testImplementation(project(path = ":boot", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot", configuration = "koinElements"))
    testImplementation(project(path = ":boot", configuration = "inaraElements"))
    testImplementation(project(path = ":boot", configuration = "metroElements"))
    testImplementation(project(path = ":boot-server-grpc", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot-server-grpc", configuration = "koinElements"))
    testImplementation(project(path = ":boot-server-grpc", configuration = "metroElements"))
  }

  // the tests compose the inara flavor into a module of their own, as an application does
  configurations["testImplementation"].extendsFrom(configurations["inaraApi"])
  dependencies { "kspTest"(rootProject.libs.inara.processor) }
  sourceSets.named("test") {
    compileClasspath += sourceSets["kodein"].output + sourceSets["koin"].output + sourceSets["metro"].output + sourceSets["inara"].output
    runtimeClasspath += sourceSets["kodein"].output + sourceSets["koin"].output + sourceSets["metro"].output + sourceSets["inara"].output
  }
}

// the gRPC snippets' greeter, generated as an application generates its own
project(":docs:snippets:common") {
  configureGrpcProtos()
}

/**
 * kotpb-grpc-codegen's artifact for protoc: its native executable where it publishes one for this platform, and
 * otherwise its jar, which the protobuf plugin runs on the JVM. The platforms are those its pinned version publishes;
 * check them at each upgrade.
 */
fun Project.kotpbGrpcCodegen(artifact: String): String {
  val nativePlatforms = setOf("linux-x86_64", "linux-aarch_64", "osx-aarch_64", "windows-x86_64")
  val platform = extensions.getByType<com.google.gradle.osdetector.OsDetector>().classifier
  return if (platform in nativePlatforms) artifact else "$artifact:jvm@jar"
}

/**
 * Generates the messages, with their Kotlin DSL builders, and the coroutine stubs and server bases, of the project's
 * protos, such as the test source set's, under src/test/proto, with protoc and kotpb-grpc-codegen, as an application
 * generates its own.
 */
fun Project.configureGrpcProtos() {
  apply(plugin = "com.google.protobuf")
  dependencies.add("testImplementation", rootProject.libs.protobuf.java)
  // the messages' Kotlin DSL builders, as an application uses them
  dependencies.add("testImplementation", rootProject.libs.protobuf.kotlin)
  extensions.configure<ProtobufExtension> {
    protoc { artifact = rootProject.libs.protobuf.protoc.get().toString() }
    plugins { create("grpckt") { artifact = kotpbGrpcCodegen(rootProject.libs.kotpb.grpc.codegen.get().toString()) } }
    generateProtoTasks {
      all().configureEach {
        plugins { create("grpckt") }
        builtins { create("kotlin") }
      }
    }
  }
}

// Helpers for unit tests: check the start plan under shuffled tiebreakers, capture logs, and set the clock. Plain
// Kotlin, so any test framework can use them. Deliberately nothing that boots an application: test a component by
// constructing it, with fakes for what it uses, rather than through the application's wiring.
project("boot-test") {
  dependencies {
    // startPlan, for the shuffled start plans
    api(project(":boot"))
    // StaticConfigBinder is a ConfigBinder
    api(project(":boot-config-common"))
    // Bootable's logging, initialized for tests, and captured: the events are Log4j2's own
    api(project(":boot-logging-log4j2"))
    api(rootProject.libs.log4j.core)
  }
}

// Collects the spans and metrics an OpenTelemetry SDK records, in memory, through boot-telemetry-otel's customizers
project("boot-test-otel") {
  configureDiVariants()
  dependencies {
    api(project(":boot-telemetry-otel"))
    api(rootProject.libs.opentelemetry.sdk.testing)
    // each flavor adds to boot-telemetry-otel's customizers, so it brings its matching flavor
    "metroApi"(project(path = ":boot", configuration = "metroElements"))
    "metroApi"(project(path = ":boot-telemetry-otel", configuration = "metroElements"))
    "kodeinApi"(project(path = ":boot-telemetry-otel", configuration = "kodeinElements"))
    "koinApi"(project(path = ":boot-telemetry-otel", configuration = "koinElements"))
    "inaraApi"(project(path = ":boot-telemetry-otel", configuration = "inaraElements"))

    // TestClock
    testImplementation(project(":boot-test"))
    // the tests exercise this module's kodein, koin, metro, and inara flavors too, wired up as a consumer would
    testImplementation(project(path = ":boot", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot", configuration = "koinElements"))
    testImplementation(project(path = ":boot", configuration = "inaraElements"))
    testImplementation(project(path = ":boot", configuration = "metroElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "koinElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "inaraElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "metroElements"))
    testImplementation(project(path = ":boot-telemetry-otel", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot-telemetry-otel", configuration = "koinElements"))
    testImplementation(project(path = ":boot-telemetry-otel", configuration = "inaraElements"))
    testImplementation(project(path = ":boot-telemetry-otel", configuration = "metroElements"))
  }

  // the tests compose the inara flavor into a module of their own, as an application does
  configurations["testImplementation"].extendsFrom(configurations["inaraApi"])
  dependencies { "kspTest"(rootProject.libs.inara.processor) }
  sourceSets.named("test") {
    compileClasspath += sourceSets["kodein"].output + sourceSets["koin"].output + sourceSets["metro"].output + sourceSets["inara"].output
    runtimeClasspath += sourceSets["kodein"].output + sourceSets["koin"].output + sourceSets["metro"].output + sourceSets["inara"].output
  }
}

/**
 * Convention for a Bootable module that ships optional DI bindings alongside its vanilla API:
 *   src/main/ -> vanilla   src/metro/ -> Metro bindings   src/inara/ -> Inara modules   src/koin/ -> Koin modules
 *   src/kodein/ -> Kodein bindings, deprecated, as Kodein itself is
 *
 * Each present flavor (src/metro, src/kodein, src/koin, src/inara) is published as its own **plain coordinate** `<module>-<flavor>`
 * (e.g. `boot-metro`) whose POM depends on the base coordinate + the DI runtime + any sibling `-<flavor>`
 * coordinates. A downstream consumer just adds `implementation("...:boot-metro")` — no attribute, capability,
 * rule, or plugin — and it resolves identically from Gradle and Maven. In-repo the same flavor is exposed as a
 * consumable configuration `<flavor>Elements` (main jar + flavor jar) that sibling modules and the examples
 * consume with `project(path = ":<module>", configuration = "<flavor>Elements")`.
 *
 * The Metro compiler plugin is applied (and Kotlin bumped to 2.3) only when src/metro exists; it applies to the
 * whole module (it can't be scoped) but its runtime is kept off the vanilla/kodein/koin artifacts via compileOnly.
 *
 * The Inara flavor runs Inara's KSP processor on its source set only (`kspInara`), which generates the builders of its
 * `@DIModule`s into the flavor jar. The consumer runs the processor too, on the application's composed `@DIModule`.
 */
fun Project.configureDiVariants() {
  val hasMetro = file("src/metro/kotlin").exists()
  val hasKodein = file("src/kodein/kotlin").exists()
  val hasKoin = file("src/koin/kotlin").exists()
  val hasInara = file("src/inara/kotlin").exists()
  if (!hasMetro && !hasKodein && !hasKoin && !hasInara) return

  val metroRuntime = "dev.zacsweers.metro:runtime:${rootProject.libs.versions.metro.get()}"
  val kodeinDi = "org.kodein.di:kodein-di:7.33.0"
  val koinCore = rootProject.libs.koin.core.get().toString()
  val inaraRuntime = rootProject.libs.inara.runtime.get().toString()

  if (hasMetro) {
    apply(plugin = "dev.zacsweers.metro")
    extensions.configure<MetroPluginExtension> { automaticallyAddRuntimeDependencies.set(false) }
    // Metro is applied module-wide, so every compilation needs its runtime at compile time (annotations or not),
    // but it stays off the published/runtime classpath of the non-metro artifacts.
    dependencies.add("compileOnly", metroRuntime)
    dependencies.add("testCompileOnly", metroRuntime)
  }

  val main = sourceSets["main"]

  fun defineFlavor(name: String) {
    val ss = sourceSets.create(name)
    // compile the flavor against this module's own main classes + main's dependencies, plus whatever sibling
    // flavor outputs the module adds to its "<name>Api" bucket (see the project(...) blocks above)
    ss.compileClasspath += main.output + main.compileClasspath
    ss.runtimeClasspath += main.output + main.runtimeClasspath

    // "<name>Api" holds the flavor's OWN published deps (DI runtime + sibling -<flavor> coordinates). It is
    // deliberately NOT extended with main's api/implementation — those reach the POM via the base coordinate.
    val apiBucket = configurations.maybeCreate("${name}Api")
    configurations["${name}Implementation"].extendsFrom(apiBucket)

    val flavorJar = tasks.register("${name}Jar", Jar::class.java) {
      from(ss.output)
      archiveBaseName.set("${project.name}-$name")
    }
    // Maven Central requires a sources and a javadoc jar for every coordinate. The flavor source sets are
    // small binding layers, so the javadoc jar is empty — the base coordinate ships the real dokka output.
    val flavorSourcesJar = tasks.register("${name}SourcesJar", Jar::class.java) {
      from(ss.allSource)
      archiveBaseName.set("${project.name}-$name")
      archiveClassifier.set("sources")
    }
    val flavorJavadocJar = tasks.register("${name}JavadocJar", Jar::class.java) {
      archiveBaseName.set("${project.name}-$name")
      archiveClassifier.set("javadoc")
    }
    // nothing else pulls the flavor jars into `build` unless an example happens to consume them, so wire them in
    // explicitly — this is what makes `build` compile every flavor source set
    tasks.named("assemble") { dependsOn(flavorJar, flavorSourcesJar, flavorJavadocJar) }

    // in-repo consumable: the flavor as a full superset — this module's main jar + flavor jar, plus both the
    // module's main deps and the flavor deps. (Externally those main deps arrive via the base coordinate; this
    // config is in-repo only and never feeds the POM, so it can be fat without polluting published metadata.)
    configurations.create("${name}Elements") {
      isCanBeResolved = false
      isCanBeConsumed = true
      extendsFrom(
        configurations["${name}Implementation"],
        configurations["api"],
        configurations["implementation"],
        configurations["runtimeOnly"],
      )
      outgoing.artifact(tasks["jar"])
      outgoing.artifact(flavorJar)
    }

    // external coordinate: <module>-<name>, a plain POM-only artifact (no Gradle Module Metadata variants)
    extensions.configure<PublishingExtension> {
      publications.create(name, MavenPublication::class.java) {
        artifactId = "${project.name}-$name"
        artifact(flavorJar)
        artifact(flavorSourcesJar)
        artifact(flavorJavadocJar)
        pom.withXml {
          val node = asNode().appendNode("dependencies")
          fun dep(g: String, a: String, v: String) {
            node.appendNode("dependency").apply {
              appendNode("groupId", g); appendNode("artifactId", a)
              appendNode("version", v); appendNode("scope", "compile")
            }
          }
          // the base coordinate carries this module's main (vanilla) classes + their transitive deps
          dep(group.toString(), project.name, version.toString())
          // the flavor's own deps: external runtimes verbatim; sibling project deps as their -<flavor> coordinate
          configurations["${name}Api"].dependencies.forEach { d ->
            when (d) {
              is ProjectDependency -> dep(group.toString(), "${d.name}-$name", version.toString())
              is ExternalModuleDependency -> dep(d.group, d.name, d.version ?: "")
              else -> {}
            }
          }
        }
      }
    }
  }

  if (hasMetro) {
    defineFlavor("metro")
    dependencies.add("metroApi", metroRuntime)
  }
  if (hasKodein) {
    defineFlavor("kodein")
    dependencies.add("kodeinApi", kodeinDi)
    // module-wide Metro plugin also processes the kodein compilation, so it needs the runtime at compile time
    if (hasMetro) dependencies.add("kodeinCompileOnly", metroRuntime)
  }
  if (hasKoin) {
    defineFlavor("koin")
    dependencies.add("koinApi", koinCore)
    // as for kodein, the module-wide Metro plugin also processes the koin compilation
    if (hasMetro) dependencies.add("koinCompileOnly", metroRuntime)
  }
  if (hasInara) {
    defineFlavor("inara")
    dependencies.add("inaraApi", inaraRuntime)
    // generate the flavor's @DIModule builders; KSP's per-source-set configurations keep it off the other flavors
    apply(plugin = "com.google.devtools.ksp")
    dependencies.add("kspInara", rootProject.libs.inara.processor.get().toString())
    // KSP adds its output to the source set, so the sources jar ships the generated builders once they exist
    tasks.named("inaraSourcesJar") { dependsOn("kspInaraKotlin") }
    // as for kodein, the module-wide Metro plugin also processes the inara compilation
    if (hasMetro) dependencies.add("inaraCompileOnly", metroRuntime)
  }
}
