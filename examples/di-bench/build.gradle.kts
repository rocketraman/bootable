plugins {
  kotlin("jvm")
  id("dev.zacsweers.metro")
  application
}

kotlin {
  jvmToolchain(25)
}

dependencies {
  implementation("org.kodein.di:kodein-di:7.25.0")
  implementation(libs.koin.core)
}

application { mainClass.set("bench.MainKt") }
