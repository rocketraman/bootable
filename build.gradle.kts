plugins {
  kotlin("jvm") version Libs.Kotlin.version
}

repositories {
  mavenCentral()
}

subprojects {
  apply {
    plugin("java-library")
    plugin("org.jetbrains.kotlin.jvm")
    plugin("maven")
  }

  group = "com.github.rocketraman"
  version = "0.1"

  repositories {
    mavenCentral()
  }

  dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    api("org.kodein.di:kodein-di:7.5.0")
  }

  tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "11"
  }
}

project("boot") {
  dependencies {
    implementation(project(":boot-common"))
    // todo for now, we depend on boot-logging-log4j2, later we could change this module to use slf4j or JDK logging
    //  instead and then people can choose their own logging frameworks
    implementation(project(":boot-logging-log4j2"))

    implementation(kotlin("reflect", Libs.Kotlin.version))
  }

  tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.freeCompilerArgs += listOf(
      "-Xopt-in=kotlin.time.ExperimentalTime"
    )
  }
}

project("boot-common") {
  dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Libs.Kotlin.version}")
  }
}

project("boot-logging-log4j2") {
  dependencies {
    api("org.apache.logging.log4j:log4j-api-kotlin:1.0.0")
    api("org.apache.logging.log4j:log4j-iostreams:${Libs.Log4j2.version}")
    api("org.apache.logging.log4j:log4j-api:${Libs.Log4j2.version}")
  }
}
