plugins {
  kotlin("jvm")
  id("org.jetbrains.dokka") version "1.6.10"
  signing
  `maven-publish`
}

repositories {
  mavenCentral()
}

subprojects {
  apply {
    plugin("java-library")
    plugin("org.jetbrains.kotlin.jvm")
    plugin("org.jetbrains.dokka")
    plugin("signing")
    plugin("maven-publish")
  }

  group = "com.github.rocketraman.bootable"
  version = "0.7-SNAPSHOT"

  repositories {
    mavenCentral()
  }

  dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    api(rootProject.libs.kodein.di)
  }

  tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = JavaVersion.VERSION_11.toString()
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
    toolchain {
      languageVersion.set(JavaLanguageVersion.of(11))
    }
  }

  kotlin {
    jvmToolchain {
      (this as JavaToolchainSpec).languageVersion.set(JavaLanguageVersion.of(11))
    }
  }

  val dokkaHtml by tasks.getting(org.jetbrains.dokka.gradle.DokkaTask::class)

  val javadocJar: TaskProvider<Jar> by tasks.registering(Jar::class) {
    dependsOn(dokkaHtml)
    archiveClassifier.set("javadoc")
    from(dokkaHtml.outputDirectory)
  }

  publishing {
    publications {
      create<MavenPublication>("mavenCentral") {
        artifact(javadocJar)
        from(components["java"])
        pom {
          name.set(project.name)
          description.set("Simple opinionated microservice runtime")
          url.set("https://github.com/rocketraman/bootable")
          licenses {
            license {
              name.set("The Apache License, Version 2.0")
              url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            }
          }
          developers {
            developer {
              id.set("rocketraman")
              name.set("Raman Gupta")
              email.set("rocketraman@gmail.com")
            }
          }
          scm {
            connection.set("scm:git:git@github.com:rocketraman/bootable.git")
            developerConnection.set("scm:git:ssh://github.com:rocketraman/bootable.git")
            url.set("https://github.com/rocketraman/bootable")
          }
        }
      }
    }
    repositories {
      maven {
        url = uri("https://oss.sonatype.org/service/local/staging/deploy/maven2")
        credentials {
          username = project.findProperty("sonatypeUser") as? String
          password = project.findProperty("sonatypePassword") as? String
        }
      }
    }
  }

  signing {
    useGpgCmd()
    sign(publishing.publications["mavenCentral"])
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
    // com.jdiazcano.cfg4k -> com.github.rocketraman.cfg4k temporarily
    // see https://github.com/jdiazcano/cfg4k/issues/67
    api("com.github.rocketraman.cfg4k:cfg4k-core:${rootProject.libs.versions.cfg4k.get()}")
    api("com.github.rocketraman.cfg4k:cfg4k-hocon:${rootProject.libs.versions.cfg4k.get()}")
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

tasks.dokkaHtmlMultiModule.configure {
  outputDirectory.set(buildDir.resolve("apidocs"))
}
