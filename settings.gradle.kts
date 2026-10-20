rootProject.name = "bootable"

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
include("boot-common")
include("boot-config-common")
include("boot-config-hoplite")
include("boot-db-exposed")
include("boot-db-flyway")
include("boot-db-jdbc")
include("boot-db-jdbc-mysql")
include("boot-db-jdbc-postgresql")
include("boot-db-jdbc-sqlite")
include("boot-db-pool-hikari")
include("boot-env")
include("boot-logging")
include("boot-logging-log4j2")
include("boot-server-http-ktor")

include(":examples:compose-html:client")
include(":examples:compose-html:server")
include(":examples:compose-html:shared")
include(":examples:di-bench")
include(":examples:server-ktor")
include(":examples:server-ktor-db:common")
include(":examples:server-ktor-db:kodein")
include(":examples:server-ktor-db:metro")
include(":examples:server-ktor-db:vanilla")
include(":examples:wiring-kodein")
include(":examples:wiring-metro")
include(":examples:wiring-vanilla")
