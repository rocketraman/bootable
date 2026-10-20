import com.vanniktech.maven.publish.JavaLibrary
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SourcesJar
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.powerassert.gradle.PowerAssertGradleExtension
import dev.zacsweers.metro.gradle.MetroPluginExtension
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Jar

plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.dokka)
  alias(libs.plugins.dokka.javadoc)
  alias(libs.plugins.metro) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.kotlin.power.assert) apply false
  alias(libs.plugins.kotlin.serialization) apply false
  alias(libs.plugins.testBalloon) apply false
  signing
  alias(libs.plugins.maven.publish) apply false
}

val signingRequired: String by project

dependencies {
  dokka(project(":boot"))
  dokka(project(":boot-common"))
  dokka(project(":boot-config-common"))
  dokka(project(":boot-config-hoplite"))
  dokka(project(":boot-db-exposed"))
  dokka(project(":boot-db-flyway"))
  dokka(project(":boot-db-jdbc"))
  dokka(project(":boot-db-jdbc-mysql"))
  dokka(project(":boot-db-jdbc-postgresql"))
  dokka(project(":boot-db-jdbc-sqlite"))
  dokka(project(":boot-db-pool-hikari"))
  dokka(project(":boot-env"))
  dokka(project(":boot-logging"))
  dokka(project(":boot-logging-log4j2"))
  dokka(project(":boot-server-http-ktor"))
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
    // Bootable embeds no DI framework — callers wire the components with vanilla Kotlin, Kodein, Metro, etc.
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
    jvmToolchain(21)
    compilerOptions {
      // target 17 for max compatibility
      jvmTarget.set(JvmTarget.JVM_17)
      apiVersion.set(KotlinVersion.KOTLIN_2_1)
      languageVersion.set(KotlinVersion.KOTLIN_2_1)
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
// app, not an artifact, so it gets none of the publish / signing / dokka machinery.
configure(subprojects.filter { !it.path.startsWith(":examples") }) {
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
  configureDiVariants()
  dependencies {
    implementation(project(":boot-common"))
    // todo for now, we depend on boot-logging-log4j2, later we could use service loaders to discover implementations at runtime
    implementation(project(":boot-logging-log4j2"))
    // note: no DI framework — Bootable's wiring is plain Kotlin (kotlin-reflect is still needed at runtime
    // for log4j-api-kotlin logging, and comes transitively via boot-logging-log4j2)
  }

  kotlin {
    compilerOptions {
      optIn.add("kotlin.time.ExperimentalTime")
    }
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
    api(project(":boot-config-common"))
    api(rootProject.libs.bundles.hoplite)

    implementation(project(":boot"))
    // the metro flavor contributes to boot's BootScope, and re-exposes config-common's metro flavor
    // (ConfigBinderProvider) so consumers of boot-config-hoplite-metro get the config helper too. In-repo these
    // resolve the sibling metro source sets; in the published POM they map to the -metro coordinates.
    "metroApi"(project(path = ":boot", configuration = "metroElements"))
    "metroApi"(project(path = ":boot-config-common", configuration = "metroElements"))
  }
}

project("boot-config-common") {
  configureDiVariants()
}

project("boot-db-jdbc") {
  configureDiVariants()
  dependencies {
    api(project(":boot"))
    api(project(":boot-config-common"))
    "metroApi"(project(path = ":boot", configuration = "metroElements"))

    // a real driver, so the lifecycle tests run against a real database
    testImplementation(rootProject.libs.sqlite.jdbc)
  }
}

project("boot-db-pool-hikari") {
  configureDiVariants()
  dependencies {
    api(project(":boot-db-jdbc"))
    api(rootProject.libs.hikari)
    // the logging API only: the application chooses the logging implementation (e.g. boot-logging-log4j2)
    implementation(rootProject.libs.bundles.log4j.api)
    // the pool is only useful with boot-db-jdbc's service, so each flavor brings boot-db-jdbc's matching flavor
    "metroApi"(project(path = ":boot", configuration = "metroElements"))
    "metroApi"(project(path = ":boot-db-jdbc", configuration = "metroElements"))
    "kodeinApi"(project(path = ":boot-db-jdbc", configuration = "kodeinElements"))

    testImplementation(rootProject.libs.sqlite.jdbc)
    // the tests exercise this module's kodein and metro flavors too, wired up as a consumer would
    testImplementation(project(path = ":boot", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot", configuration = "metroElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "metroElements"))
    testImplementation(project(path = ":boot-db-jdbc", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot-db-jdbc", configuration = "metroElements"))
    testImplementation(project(":boot-logging-log4j2"))
  }

  sourceSets.named("test") {
    compileClasspath += sourceSets["kodein"].output + sourceSets["metro"].output
    runtimeClasspath += sourceSets["kodein"].output + sourceSets["metro"].output
  }
}

project("boot-db-jdbc-sqlite") {
  configureDiVariants()
  dependencies {
    api(project(":boot-db-jdbc"))
    // the driver is the point of this module: depending on it brings the right JDBC driver transitively
    api(rootProject.libs.sqlite.jdbc)
    // SQLite migrations need no extra Flyway database module — flyway-core supports SQLite directly
    "metroApi"(project(path = ":boot", configuration = "metroElements"))
    "metroApi"(project(path = ":boot-db-jdbc", configuration = "metroElements"))
    "kodeinApi"(project(path = ":boot-db-jdbc", configuration = "kodeinElements"))

    // the tests exercise this module's kodein and metro flavors too, wired up as a consumer would
    testImplementation(project(path = ":boot", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot", configuration = "metroElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "metroElements"))
    testImplementation(project(path = ":boot-db-jdbc", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot-db-jdbc", configuration = "metroElements"))
    // the driver settings only matter through a real pool
    testImplementation(project(path = ":boot-db-pool-hikari", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot-db-pool-hikari", configuration = "metroElements"))
    testImplementation(project(":boot-logging-log4j2"))
  }

  sourceSets.named("test") {
    compileClasspath += sourceSets["kodein"].output + sourceSets["metro"].output
    runtimeClasspath += sourceSets["kodein"].output + sourceSets["metro"].output
  }
}

project("boot-db-jdbc-mysql") {
  configureDiVariants()
  dependencies {
    api(project(":boot-db-jdbc"))
    // the driver is the point of this module: depending on it brings the right JDBC driver transitively
    api(rootProject.libs.mysql.connector.j)
    // Flyway needs its MySQL database module for MySQL migrations; harmless without boot-db-flyway
    runtimeOnly(rootProject.libs.flyway.mysql)
    "metroApi"(project(path = ":boot", configuration = "metroElements"))
    "metroApi"(project(path = ":boot-db-jdbc", configuration = "metroElements"))
    "kodeinApi"(project(path = ":boot-db-jdbc", configuration = "kodeinElements"))

    // the tests exercise this module's kodein and metro flavors too, wired up as a consumer would
    testImplementation(project(path = ":boot", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot", configuration = "metroElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "metroElements"))
    testImplementation(project(path = ":boot-db-jdbc", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot-db-jdbc", configuration = "metroElements"))
    // the driver settings only matter through a real pool, and Flyway proves its database module is there
    testImplementation(project(path = ":boot-db-pool-hikari", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot-db-pool-hikari", configuration = "metroElements"))
    testImplementation(project(":boot-db-flyway"))
    testImplementation(project(":boot-logging-log4j2"))
    // a real MySQL in a container; those tests are skipped where Docker is unavailable, outside CI
    testImplementation(rootProject.libs.testcontainers.mysql)
  }

  sourceSets.named("test") {
    compileClasspath += sourceSets["kodein"].output + sourceSets["metro"].output
    runtimeClasspath += sourceSets["kodein"].output + sourceSets["metro"].output
  }
}

project("boot-db-jdbc-postgresql") {
  configureDiVariants()
  dependencies {
    api(project(":boot-db-jdbc"))
    // the driver is the point of this module: depending on it brings the right JDBC driver transitively
    api(rootProject.libs.postgresql)
    // Flyway needs its PostgreSQL database module for PostgreSQL migrations; harmless without boot-db-flyway
    runtimeOnly(rootProject.libs.flyway.database.postgresql)
    "metroApi"(project(path = ":boot", configuration = "metroElements"))
    "metroApi"(project(path = ":boot-db-jdbc", configuration = "metroElements"))
    "kodeinApi"(project(path = ":boot-db-jdbc", configuration = "kodeinElements"))

    // the tests exercise this module's kodein and metro flavors too, wired up as a consumer would
    testImplementation(project(path = ":boot", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot", configuration = "metroElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "metroElements"))
    testImplementation(project(path = ":boot-db-jdbc", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot-db-jdbc", configuration = "metroElements"))
    // the driver settings only matter through a real pool, and Flyway proves its database module is there
    testImplementation(project(path = ":boot-db-pool-hikari", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot-db-pool-hikari", configuration = "metroElements"))
    testImplementation(project(":boot-db-flyway"))
    testImplementation(project(":boot-logging-log4j2"))
    // a real PostgreSQL in a container; those tests are skipped where Docker is unavailable, outside CI
    testImplementation(rootProject.libs.testcontainers.postgresql)
  }

  sourceSets.named("test") {
    compileClasspath += sourceSets["kodein"].output + sourceSets["metro"].output
    runtimeClasspath += sourceSets["kodein"].output + sourceSets["metro"].output
  }
}

project("boot-db-exposed") {
  configureDiVariants()
  dependencies {
    api(project(":boot-db-jdbc"))
    api(rootProject.libs.bundles.exposed)
    // the logging API only: the application chooses the logging implementation (e.g. boot-logging-log4j2)
    implementation(rootProject.libs.bundles.log4j.api)
    // Exposed needs boot-db-jdbc's DataSource, so each flavor brings boot-db-jdbc's matching flavor
    "metroApi"(project(path = ":boot", configuration = "metroElements"))
    "metroApi"(project(path = ":boot-db-jdbc", configuration = "metroElements"))
    "kodeinApi"(project(path = ":boot-db-jdbc", configuration = "kodeinElements"))

    // the tests exercise this module's kodein and metro flavors too, wired up as a consumer would, against a real
    // SQLite database, with Flyway creating the schema that is verified
    testImplementation(project(path = ":boot", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot", configuration = "metroElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "metroElements"))
    testImplementation(project(path = ":boot-db-jdbc", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot-db-jdbc", configuration = "metroElements"))
    testImplementation(project(path = ":boot-db-jdbc-sqlite", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot-db-jdbc-sqlite", configuration = "metroElements"))
    testImplementation(project(path = ":boot-db-pool-hikari", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot-db-pool-hikari", configuration = "metroElements"))
    testImplementation(project(path = ":boot-db-flyway", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot-db-flyway", configuration = "metroElements"))
    testImplementation(project(":boot-logging-log4j2"))
  }

  sourceSets.named("test") {
    compileClasspath += sourceSets["kodein"].output + sourceSets["metro"].output
    runtimeClasspath += sourceSets["kodein"].output + sourceSets["metro"].output
  }
}

project("boot-db-flyway") {
  configureDiVariants()
  dependencies {
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

    // the tests exercise this module's kodein and metro flavors too, wired up as a consumer would, against a real
    // SQLite database
    testImplementation(project(path = ":boot", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot", configuration = "metroElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot-config-hoplite", configuration = "metroElements"))
    testImplementation(project(path = ":boot-db-jdbc", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot-db-jdbc", configuration = "metroElements"))
    testImplementation(project(path = ":boot-db-jdbc-sqlite", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot-db-jdbc-sqlite", configuration = "metroElements"))
    testImplementation(project(path = ":boot-db-pool-hikari", configuration = "kodeinElements"))
    testImplementation(project(path = ":boot-db-pool-hikari", configuration = "metroElements"))
    testImplementation(project(":boot-logging-log4j2"))
  }

  sourceSets.named("test") {
    compileClasspath += sourceSets["kodein"].output + sourceSets["metro"].output
    runtimeClasspath += sourceSets["kodein"].output + sourceSets["metro"].output
  }
}

project("boot-env") {
  configureDiVariants()
  dependencies {
    api(project(":boot-config-common"))
    implementation(project(":boot"))
    // the metro flavor contributes to boot's BootScope (in-repo: boot's metro source set; POM: boot-metro)
    "metroApi"(project(path = ":boot", configuration = "metroElements"))
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
  }
}

project("boot-server-http-ktor") {
  dependencies {
    implementation(project(":boot"))
    api(project(":boot-config-common"))
    api(rootProject.libs.ktor.server.netty)
  }
}

/**
 * Convention for a Bootable module that ships optional DI bindings alongside its vanilla API:
 *   src/main/ -> vanilla   src/metro/ -> Metro bindings   src/kodein/ -> Kodein bindings
 *
 * Each present flavor (src/metro, src/kodein) is published as its own **plain coordinate** `<module>-<flavor>`
 * (e.g. `boot-metro`) whose POM depends on the base coordinate + the DI runtime + any sibling `-<flavor>`
 * coordinates. A downstream consumer just adds `implementation("...:boot-metro")` — no attribute, capability,
 * rule, or plugin — and it resolves identically from Gradle and Maven. In-repo the same flavor is exposed as a
 * consumable configuration `<flavor>Elements` (main jar + flavor jar) that sibling modules and the examples
 * consume with `project(path = ":<module>", configuration = "<flavor>Elements")`.
 *
 * The Metro compiler plugin is applied (and Kotlin bumped to 2.3) only when src/metro exists; it applies to the
 * whole module (it can't be scoped) but its runtime is kept off the vanilla/kodein artifacts via compileOnly.
 */
fun Project.configureDiVariants() {
  val hasMetro = file("src/metro/kotlin").exists()
  val hasKodein = file("src/kodein/kotlin").exists()
  if (!hasMetro && !hasKodein) return

  val metroRuntime = "dev.zacsweers.metro:runtime:1.3.2"
  val kodeinDi = "org.kodein.di:kodein-di:7.25.0"

  if (hasMetro) {
    apply(plugin = "dev.zacsweers.metro")
    extensions.configure<MetroPluginExtension> { automaticallyAddRuntimeDependencies.set(false) }
    // Metro is applied module-wide, so every compilation needs its runtime at compile time (annotations or not),
    // but it stays off the published/runtime classpath of the non-metro artifacts.
    dependencies.add("compileOnly", metroRuntime)
    dependencies.add("testCompileOnly", metroRuntime)
    extensions.configure<KotlinJvmProjectExtension> {
      compilerOptions {
        apiVersion.set(KotlinVersion.KOTLIN_2_3)
        languageVersion.set(KotlinVersion.KOTLIN_2_3)
      }
    }
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
}
