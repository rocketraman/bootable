import com.vanniktech.maven.publish.JavaLibrary
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SourcesJar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.dokka)
  alias(libs.plugins.dokka.javadoc)
  signing
  alias(libs.plugins.maven.publish) apply false
}

val signingRequired: String by project

dependencies {
  dokka(project(":boot"))
  dokka(project(":boot-common"))
  dokka(project(":boot-config-common"))
  dokka(project(":boot-config-cfg4k"))
  dokka(project(":boot-config-hoplite"))
  dokka(project(":boot-env"))
  dokka(project(":boot-logging"))
  dokka(project(":boot-logging-log4j2"))
  dokka(project(":boot-server-http-ktor"))
}

allprojects {
  group = "com.github.rocketraman.bootable"
  version = "2.2.0-SNAPSHOT"
}

subprojects {
  apply {
    plugin("java-library")
    plugin("org.jetbrains.kotlin.jvm")
    plugin("org.jetbrains.dokka")
    plugin("org.jetbrains.dokka-javadoc")
    plugin("signing")
    plugin("com.vanniktech.maven.publish")
  }

  dependencies {
    implementation(platform(rootProject.libs.kotlin.bom))
    api(rootProject.libs.kodein.di)
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
    // target 11 for max compatibility
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }

  kotlin {
    jvmToolchain(21)
    compilerOptions {
      // target 11 for max compatibility
      jvmTarget.set(JvmTarget.JVM_11)
      apiVersion.set(KotlinVersion.KOTLIN_2_1)
      languageVersion.set(KotlinVersion.KOTLIN_2_1)
    }
  }

  configure<MavenPublishBaseExtension> {
    configure(
      JavaLibrary(
        javadocJar = JavadocJar.Dokka("dokkaGeneratePublicationHtml"),
        sourcesJar = SourcesJar.Sources(),
      )
    )
    // uploads to the Central Portal and releases automatically once validation passes
    publishToMavenCentral(automaticRelease = true)
    if (signingRequired.toBoolean()) {
      signAllPublications()
    }
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

  // vanniktech's signAllPublications() delegates to the signing plugin; keep using the local gpg agent
  if (signingRequired.toBoolean()) {
    signing {
      useGpgCmd()
    }
  }
}

project("boot") {
  dependencies {
    implementation(project(":boot-common"))
    // todo for now, we depend on boot-logging-log4j2, later we could use service loaders to discover implementations at runtime
    implementation(project(":boot-logging-log4j2"))

    implementation(rootProject.libs.kotlin.reflect)
  }

  kotlin {
    compilerOptions {
      optIn.add("kotlin.time.ExperimentalTime")
    }
  }
}

project("boot-common") {
  dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${rootProject.libs.versions.kotlinx.coroutines.get()}")
  }
}

project("boot-config-cfg4k") {
  dependencies {
    api(project(":boot-config-common"))
    api(rootProject.libs.cfg4k.core)
    api(rootProject.libs.cfg4k.hocon)
    api(rootProject.libs.typesafe.config)

    implementation(project(":boot"))
  }
}

project("boot-config-hoplite") {
  dependencies {
    api(project(":boot-config-common"))
    api(rootProject.libs.bundles.hoplite)

    implementation(project(":boot"))
  }
}

project("boot-config-common") {
}

project("boot-env") {
  dependencies {
    api(project(":boot-config-common"))
    implementation(project(":boot"))
  }
}

project("boot-logging")

project("boot-logging-log4j2") {
  dependencies {
    api(project(":boot-logging"))
    api(rootProject.libs.bundles.log4j.api)
    implementation(platform(rootProject.libs.log4j.bom))
    implementation(rootProject.libs.bundles.log4j.impl)
  }
}

project("boot-server-http-ktor") {
  dependencies {
    implementation(project(":boot"))
    api(project(":boot-config-common"))
    api(rootProject.libs.ktor.server.netty)
  }
}
