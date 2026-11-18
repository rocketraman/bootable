plugins {
  kotlin("jvm")
  alias(libs.plugins.jib)
  // for AotCacheTraining: this project has no application plugin, so the plugin adds no aotCache task of its own
  id("bootable.aot-cache")
}

kotlin {
  jvmToolchain(25)
}

// The vanilla app, as a container image with its JVM AOT cache in it.
//
// The cache is only used by the same JDK build, with the same class path, of jars of the same sizes and modification
// times, as the training run's. So the training runs in a container of the image itself: jibBuildTar builds it,
// without a cache, the container runtime loads it, and aotCache runs it once, with the cache's directory mounted
// from the build. Jib's images are reproducible, with every file's modification time fixed, so the final image, which
// jibDockerBuild (or jib) builds with the cache as one more layer, has the same jars as the one the cache was trained
// in, on the same base image.
dependencies {
  implementation(project(":examples:server-ktor-db:vanilla"))
}

// the container runtime's command: docker, or podman, with -PcontainerCli=podman
val containerCli = providers.gradleProperty("containerCli").orElse("docker")
val imageName = "bootable-examples/server-ktor-db-jib"
val mainClassName = "AppKt"
val cacheDir = "/app/aot"
val cacheName = "server-ktor-db.aot"
// the image's JVM flags, but for the cache's
val appJvmFlags = listOf<String>()
// the final image's extra layer: empty for the training image, and the cache for the final one
val aotLayer = layout.buildDirectory.dir("aot-layer")
// where the training container writes the cache, mounted at cacheDir
val trainedCacheDir = layout.buildDirectory.dir("aot/cache")

jib {
  from {
    // a JRE is enough for both the training and the application; the training's JDK build is the image's own.
    // Pinned by digest, so the JDK build, and with it the cache, only changes when the digest does.
    image = "eclipse-temurin:25-jre@sha256:945247f9d092703e0c6be92243ef3c51640229e23ab34d353c8dcdc73e6cd4e6"
  }
  to {
    image = imageName
  }
  container {
    mainClass = mainClassName
    jvmFlags = appJvmFlags + "-XX:AOTCache=$cacheDir/$cacheName"
    // the application's own classes as a jar, rather than Jib's default /app/classes and /app/resources: the JVM
    // can't use an AOT cache with a class path that has a directory in it, other than an empty one
    containerizingMode = "packaged"
    ports = listOf("8001")
    // the config's database path is relative to the working directory, /, which the server can't write
    environment = mapOf("DB_JDBC_PATH" to "/tmp/entries.db")
  }
  extraDirectories {
    // build/aot-layer/app/aot/server-ktor-db.aot, as /app/aot/server-ktor-db.aot, in the final image
    paths {
      path {
        setFrom(aotLayer.get().asFile.toPath())
        into = "/"
      }
    }
  }
  dockerClient {
    // Jib takes a path, not a command, for any executable but its default, docker
    executable = containerCli.map { cli ->
      if (cli == "docker" || File(cli).isAbsolute) cli
      else System.getenv("PATH").split(File.pathSeparator).map { File(it, cli) }.firstOrNull { it.canExecute() }?.path ?: cli
    }.get()
  }
}

// the image without a fresh cache, in a tar, for the training run
val trainingImageTar = layout.buildDirectory.file("jib-image.tar")

val loadTrainingImage = tasks.register<Exec>("loadTrainingImage") {
  description = "Loads the image that jibBuildTar built into the container runtime, for the AOT cache training"
  dependsOn(tasks.jibBuildTar)
  inputs.file(trainingImageTar)
  commandLine(containerCli.get(), "load", "--input", trainingImageTar.get().asFile.path)
}

// the training container's port for the server, on the host
val trainingPort = 18002

val aotCache = tasks.register<AotCacheTraining>("aotCache") {
  group = "application"
  description = "Runs the image once, in a container, to write its JVM AOT cache"
  dependsOn(loadTrainingImage)
  // the image's content: Jib's tar is reproducible, so this reruns only when the image changes
  trainedFiles.from(trainingImageTar)
  cacheFile = trainedCacheDir.map { it.file(cacheName) }
  logFile = layout.buildDirectory.file("aot/$cacheName.log")
  // The image's own entrypoint, but for -XX:AOTCacheOutput in place of -XX:AOTCache: the JVM takes only one of
  // them. SIGTERM to the container runtime's client goes on to the JVM, which writes the cache into the mounted
  // directory as it exits.
  command.addAll(containerCli.map { cli ->
    listOf(
      cli, "run", "--rm",
      "--publish", "127.0.0.1:$trainingPort:8001",
      // Z: relabelled for SELinux, where it's enabled
      "--volume", "${trainedCacheDir.get().asFile}:$cacheDir:Z",
      // an OTLP endpoint, as in production, so the cache has the SDK's OTLP exporters: the container's own
      // localhost, where nothing listens
      "--env", "OTEL_EXPORTER_OTLP_ENDPOINT=http://127.0.0.1:4318",
      "--entrypoint", "java",
      imageName,
    ) + appJvmFlags + listOf(
      "-XX:AOTCacheOutput=$cacheDir/$cacheName",
      "-cp", "@/app/jib-classpath-file",
      mainClassName,
    )
  })
  val url = "http://127.0.0.1:$trainingPort"
  request("PUT", "$url/entries/trained", "by the AOT training run")
  request("GET", "$url/entries/trained")
  request("GET", "$url/entries")
  request("GET", "$url/")
  request("GET", "$url/metrics")
  request("GET", "$url/health/ready")
  request("DELETE", "$url/entries/trained")
  rounds = 20
}

// the training image has no cache, so its tar, and the training, only change when the application does
tasks.jibBuildTar {
  // Jib needs the directory, if empty
  doFirst { aotLayer.get().asFile.run { deleteRecursively(); mkdirs() } }
}

val aotCacheLayer = tasks.register<Sync>("aotCacheLayer") {
  description = "Puts the AOT cache in the final image's extra layer"
  from(aotCache)
  into(aotLayer.map { it.dir(cacheDir.removePrefix("/")) })
  // after jibBuildTar, which empties it
  mustRunAfter(tasks.jibBuildTar)
}

// the images with the cache: jibDockerBuild into the container runtime, jib to a registry
tasks.jibDockerBuild { dependsOn(aotCacheLayer) }
tasks.jib { dependsOn(aotCacheLayer) }
