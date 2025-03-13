rootProject.name = "bootable"

dependencyResolutionManagement {
  @Suppress("UnstableApiUsage")
  repositories {
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
    mavenCentral()
  }
}

include("boot")
include("boot-common")
include("boot-config-common")
include("boot-config-cfg4k")
include("boot-config-hoplite")
include("boot-env")
include("boot-logging")
include("boot-logging-log4j2")
include("boot-server-http-ktor")
