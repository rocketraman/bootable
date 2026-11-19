rootProject.name = "bootable"

pluginManagement {
  // plugins for the examples, such as bootable.aot-cache
  includeBuild("build-logic")
}

dependencyResolutionManagement {
  @Suppress("UnstableApiUsage")
  repositories {
/*
    // for the hoplite version built from github/rocketraman/hoplite
    maven {
      url = uri("https://maven.pkg.github.com/rocketraman/hoplite")
      credentials {
        val githubUser: String by settings
        val githubToken: String by settings
        username = githubUser
        password = githubToken
      }
      mavenContent {
        includeGroup("com.sksamuel.hoplite")
      }
    }
*/
    mavenCentral()
    // Compose's runtime, which the compose-html example's client depends on
    google {
      mavenContent {
        includeGroupByRegex("""androidx\..*""")
      }
    }
  }
}

include("boot")
include("boot-client-http-ktor")
include("boot-client-http-ktor-cio")
include("boot-client-http-ktor-java")
include("boot-client-http-ktor-otel")
include("boot-common")
include("boot-config-common")
include("boot-config-hoplite")
include("boot-db-exposed")
include("boot-db-flyway")
include("boot-db-jdbc")
include("boot-db-jdbc-mysql")
include("boot-db-jdbc-otel")
include("boot-db-jdbc-postgresql")
include("boot-db-jdbc-sqlite")
include("boot-db-pool-hikari")
include("boot-db-pool-hikari-otel")
include("boot-env")
include("boot-leader")
include("boot-leader-k8s")
include("boot-logging")
include("boot-logging-log4j2")
include("boot-periodic")
include("boot-periodic-otel")
include("boot-scheduler-db")
include("boot-server-http-ktor")
include("boot-server-http-ktor-cio")
include("boot-server-http-ktor-netty")
include("boot-server-http-ktor-otel")
include("boot-telemetry-otel")
include("boot-telemetry-otel-gcp")
include("boot-telemetry-otel-micrometer-bridge")
include("boot-telemetry-otel-prometheus")
include("boot-telemetry-otel-signoz")

include(":examples:compose-html:client")
include(":examples:compose-html:server")
include(":examples:compose-html:shared")
include(":examples:di-bench")
include(":examples:htmx")
include(":examples:kilua:client")
include(":examples:kilua:server")
include(":examples:kilua:shared")
include(":examples:server-ktor")
include(":examples:server-ktor-db:common")
include(":examples:server-ktor-db:jib")
include(":examples:server-ktor-db:kodein")
include(":examples:server-ktor-db:metro")
include(":examples:server-ktor-db:vanilla")
include(":examples:start-order")
include(":examples:wiring-kodein")
include(":examples:wiring-metro")
include(":examples:wiring-vanilla")
