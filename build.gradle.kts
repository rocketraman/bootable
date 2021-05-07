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
    plugin("maven-publish")
  }

  group = "com.github.rocketraman.bootable"
  version = "0.1"

  repositories {
    mavenCentral()
    jcenter()
  }

  dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    api("org.kodein.di:kodein-di:7.5.0")
  }

  tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "11"
  }

  java {
    withSourcesJar()
  }

  configure<PublishingExtension> {
    publications {
      create<MavenPublication>("mavenJava") {
        from(components["java"])
      }
    }
  }
}

project("boot") {
  dependencies {
    implementation(project(":boot-common"))
    // todo for now, we depend on boot-logging-log4j2, later we could use service loaders to discover implementations at runtime
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
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Libs.Kotlinx.Coroutines.version}")
  }
}

project("boot-config-cfg4k") {
  dependencies {
    implementation(project(":boot"))
    api("com.jdiazcano.cfg4k:cfg4k-core:0.9.5")
    api("com.jdiazcano.cfg4k:cfg4k-hocon:0.9.5")
    api("com.typesafe:config:1.3.3")
  }
}

project("boot-config-common") {
}

project("boot-logging-log4j2") {
  dependencies {
    api("org.apache.logging.log4j:log4j-api-kotlin:1.0.0")
    api("org.apache.logging.log4j:log4j-api:${Libs.Log4j2.version}")
    implementation("org.apache.logging.log4j:log4j-iostreams:${Libs.Log4j2.version}")
    implementation("org.apache.logging.log4j:log4j-core:${Libs.Log4j2.version}")
    implementation("org.apache.logging.log4j:log4j-jul:${Libs.Log4j2.version}")
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:${Libs.Log4j2.version}")
  }
}

project("boot-server-http-ktor") {
  dependencies {
    implementation(project(":boot"))
    api(project(":boot-config-common"))
    api("io.ktor:ktor-server-core:${Libs.Ktor.version}")
    api("io.ktor:ktor-server-netty:${Libs.Ktor.version}")
  }
}
