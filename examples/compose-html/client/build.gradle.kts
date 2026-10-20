plugins {
  kotlin("multiplatform")
  kotlin("plugin.compose")
}

// The browser client: Compose HTML, compiled to JavaScript, calling the server's API with the shared models.
kotlin {
  js {
    browser {
      commonWebpackConfig {
        outputFileName = "client.js"
      }
    }
    binaries.executable()
  }

  sourceSets {
    jsMain {
      dependencies {
        implementation(project(":examples:compose-html:shared"))

        implementation(libs.compose.html.core)
        implementation(libs.compose.runtime)

        implementation(libs.ktor.client.js)
        implementation(libs.ktor.client.content.negotiation)
        implementation(libs.ktor.serialization.kotlinx.json)
      }
    }
  }
}

// The production bundle, for the server to serve; see the server's build.
val browserDistribution = configurations.create("browserDistribution") {
  isCanBeResolved = false
}

artifacts {
  val distribution = tasks.named("jsBrowserDistribution")
  add(browserDistribution.name, distribution.map { it.outputs.files.singleFile }) {
    builtBy(distribution)
  }
}
