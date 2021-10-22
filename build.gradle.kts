plugins {
  kotlin("jvm")
}

repositories {
  mavenCentral()
}

subprojects {
  apply {
    plugin("java-library")
    plugin("org.jetbrains.kotlin.jvm")
    plugin("maven-publish")
  }

  group = "com.github.rocketraman.bootable"
  version = "0.1"

  repositories {
    mavenCentral()
    jcenter {
      content {
        includeGroup("com.jdiazcano.cfg4k")
      }
    }
  }

  dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    api(rootProject.libs.kodein.di)
  }

  tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "11"
  }

  configurations.all {
    resolutionStrategy.eachDependency {
      if (requested.group == "org.jetbrains.kotlin") {
        useVersion(rootProject.libs.versions.kotlin.get())
        because("Use consistent Kotlin stdlib and reflect artifacts")
      }
    }
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

    implementation(kotlin("reflect"))
  }

  tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.freeCompilerArgs += listOf(
      "-Xopt-in=kotlin.time.ExperimentalTime"
    )
  }
}

project("boot-common") {
  dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${rootProject.libs.versions.kotlinx.coroutines.get()}")
  }
}

project("boot-config-cfg4k") {
  dependencies {
    implementation(project(":boot"))
    api("com.jdiazcano.cfg4k:cfg4k-core:${rootProject.libs.versions.cfg4k.get()}")
    api("com.jdiazcano.cfg4k:cfg4k-hocon:${rootProject.libs.versions.cfg4k.get()}")
    api("com.typesafe:config:${rootProject.libs.versions.config.get()}")
  }
}

project("boot-config-common") {
}

project("boot-logging-log4j2") {
  dependencies {
    api(rootProject.libs.bundles.log4j.api)
    implementation(rootProject.libs.bundles.log4j.impl)
  }
}

project("boot-server-http-ktor") {
  dependencies {
    implementation(project(":boot"))
    api(project(":boot-config-common"))
    api(rootProject.libs.ktor.server.core)
    api(rootProject.libs.ktor.server.netty)
  }
}
