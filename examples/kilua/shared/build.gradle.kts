import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  kotlin("multiplatform")
  kotlin("plugin.serialization")
}

// The API's models and rules, compiled for both sides: the JVM for the server, JavaScript for the browser client.
kotlin {
  jvmToolchain(25)

  jvm {
    compilerOptions {
      // 17, like the rest of the build; this example's server targets 25, so it could be newer, but needn't be
      jvmTarget.set(JvmTarget.JVM_17)
    }
  }
  js {
    browser()
  }

  sourceSets {
    commonMain {
      dependencies {
        api(libs.kotlinx.serialization.core)
      }
    }
  }
}
