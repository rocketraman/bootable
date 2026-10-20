plugins {
  kotlin("multiplatform")
  kotlin("plugin.compose")
  alias(libs.plugins.kilua)
}

// The Kilua app, compiled to JavaScript and bundled twice: for the browser, and, since webpack.config.ssr.d exists, for
// Node.js, where the server's renderer runs it to render pages.
kotlin {
  js {
    useEsModules()
    browser {
      commonWebpackConfig {
        outputFileName = "main.bundle.js"
      }
    }
    binaries.executable()
    compilerOptions {
      target.set("es2015")
    }
  }

  sourceSets {
    jsMain {
      dependencies {
        implementation(project(":examples:kilua:shared"))

        implementation(libs.kilua)
        implementation(libs.kilua.ssr)

        implementation(libs.ktor.client.js)
        implementation(libs.ktor.client.content.negotiation)
        implementation(libs.ktor.serialization.kotlinx.json)
      }
    }
  }
}

// The production bundles, for the server to serve and to render with; see the server's build.
val browserDistribution = configurations.create("browserDistribution") {
  isCanBeResolved = false
}
val ssrBundle = configurations.create("ssrBundle") {
  isCanBeResolved = false
}

artifacts {
  val distribution = tasks.named("jsBrowserDistribution")
  add(browserDistribution.name, distribution.map { it.outputs.files.singleFile }) {
    builtBy(distribution)
  }
  // the Node.js bundle and index.html, zipped by jsArchiveSSR, which the Kilua plugin registers only after evaluation
  add(ssrBundle.name, layout.buildDirectory.file("libs/ssr.zip")) {
    builtBy("jsArchiveSSR")
  }
}
