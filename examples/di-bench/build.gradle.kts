import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
  kotlin("jvm")
  id("dev.zacsweers.metro")
  application
}

kotlin {
  jvmToolchain(21)
  compilerOptions {
    apiVersion.set(KotlinVersion.KOTLIN_2_3)
    languageVersion.set(KotlinVersion.KOTLIN_2_3)
  }
}

dependencies {
  implementation("org.kodein.di:kodein-di:7.25.0")
}

application { mainClass.set("bench.MainKt") }
