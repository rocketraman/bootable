enableFeaturePreview("VERSION_CATALOGS")

pluginManagement {
  plugins {
    repositories {
      gradlePluginPortal()
      mavenCentral()
    }
    resolutionStrategy {
      eachPlugin {
        if (requested.id.id.startsWith("org.jetbrains.kotlin.")) useVersion("1.5.31")
      }
    }
  }
}

rootProject.name = "bootable"

include("boot")
include("boot-common")
include("boot-config-common")
include("boot-config-cfg4k")
include("boot-env")
include("boot-logging-log4j2")
include("boot-server-http-ktor")
