import java.io.File
import java.io.Serializable
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.TimeUnit
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.plugins.JavaApplication
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.work.DisableCachingByDefault

/**
 * An HTTP request sent to the application during an [AotCacheTraining] run, once it is ready, so the training also
 * loads and links the classes of the request path, not just those of the start.
 */
data class TrainingRequest(val method: String, val url: String, val body: String? = null) : Serializable

/**
 * A JVM AOT cache training run (JEP 483 and 514, JDK 24+).
 *
 * It runs [command], which starts the application with `-XX:AOTCacheOutput=<cacheFile>`, waits for a line of its
 * output matching [readyPattern], sends [requests] [rounds] times, then stops it with SIGTERM: the JVM writes the AOT
 * cache as it exits, and the task checks that [cacheFile] is there.
 *
 * The cache is only used by a JVM of the same JDK build, with the same class path, of jars of the same sizes and
 * modification times, as the training run's; otherwise the JVM warns, and runs without it. So the command should run
 * the application as it ships: the installed distribution, or the container image.
 *
 * Unix only: the JVM writes the cache when it exits normally, and Windows has no SIGTERM to ask it to.
 */
@DisableCachingByDefault(because = "the cache is only valid for the exact files, timestamps included, it was trained with")
abstract class AotCacheTraining : DefaultTask() {
  /** The command that starts the application, with `-XX:AOTCacheOutput` set to [cacheFile] in some way. */
  @get:Input
  abstract val command: ListProperty<String>

  /** Environment variables for [command], added to the build's own. */
  @get:Input
  abstract val environment: MapProperty<String, String>

  @get:Internal
  abstract val workingDir: DirectoryProperty

  /** The files of the application that [command] runs, so the training runs again when they change. */
  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val trainedFiles: ConfigurableFileCollection

  /** A regular expression matching the line the application logs once it is ready: Bootable's `=> Running`. */
  @get:Input
  abstract val readyPattern: Property<String>

  @get:Input
  abstract val requests: ListProperty<TrainingRequest>

  @get:Input
  abstract val rounds: Property<Int>

  @get:Internal
  abstract val startTimeout: Property<Duration>

  /** How long the application may take to exit after SIGTERM, which includes the JVM writing the cache. */
  @get:Internal
  abstract val stopTimeout: Property<Duration>

  @get:OutputFile
  abstract val cacheFile: RegularFileProperty

  /** The application's output, during the training; by default, next to [cacheFile]. */
  @get:Internal
  abstract val logFile: RegularFileProperty

  init {
    readyPattern.convention("""=> Running \(""")
    rounds.convention(1)
    startTimeout.convention(Duration.ofMinutes(2))
    stopTimeout.convention(Duration.ofMinutes(2))
    workingDir.convention(project.layout.buildDirectory.dir("aot/work"))
    logFile.convention(project.layout.file(cacheFile.locationOnly.map { it.asFile.resolveSibling("${it.asFile.name}.log") }))
  }

  /** Sends a request to the application, during the training, once it is ready. */
  fun request(method: String, url: String, body: String? = null) {
    requests.add(TrainingRequest(method, url, body))
  }

  @TaskAction
  fun train() {
    if (System.getProperty("os.name").startsWith("Windows")) {
      throw GradleException("AOT cache training stops the application with SIGTERM, which Windows doesn't have")
    }
    val cache = cacheFile.get().asFile
    cache.delete()
    cache.parentFile.mkdirs()
    val dir = workingDir.get().asFile.apply { mkdirs() }

    val log = logFile.get().asFile.apply { parentFile.mkdirs() }
    logger.info("AOT cache training: {} with {}, output in {}", command.get(), environment.get(), log)
    // The output goes to a file, not a pipe: the JVM writes the cache as it exits, in a child JVM that shares its
    // output, and with a pipe read by this task, the shutdown's output was lost, and the child never wrote the cache.
    val process = ProcessBuilder(command.get())
      .directory(dir)
      .redirectErrorStream(true)
      .redirectOutput(log)
      .apply { environment().putAll(this@AotCacheTraining.environment.get()) }
      .start()

    fun fail(message: String): Nothing {
      process.destroyForcibly()
      val tail = log.readLines().takeLast(40).joinToString("\n")
      throw GradleException("$message; the application's output is in $log, and ends with:\n$tail")
    }

    try {
      val pattern = Regex(readyPattern.get())
      val deadline = System.nanoTime() + startTimeout.get().toNanos()
      log.bufferedReader().use { output ->
        while (true) {
          val line = output.readLine()
          if (line == null) {
            if (!process.isAlive) fail("The application exited (${process.exitValue()}) before it was ready")
            if (System.nanoTime() > deadline) fail("The application wasn't ready within ${startTimeout.get()}")
            Thread.sleep(50)
          } else if (pattern.containsMatchIn(line)) {
            break
          }
        }
      }
      logger.lifecycle("The application is ready; training with ${requests.get().size} request(s), ${rounds.get()} time(s)")
      sendRequests(::fail)

      // SIGTERM: the JVM runs its shutdown hooks, so the application stops as it would in production, then writes
      // the cache
      process.destroy()
      if (!process.waitFor(stopTimeout.get().toMillis(), TimeUnit.MILLISECONDS)) {
        fail("The application didn't exit within ${stopTimeout.get()} of SIGTERM")
      }
    } finally {
      if (process.isAlive) process.destroyForcibly()
    }

    if (!cache.isFile) fail("The application exited (${process.exitValue()}), but wrote no AOT cache at $cache")
    logger.lifecycle("AOT cache: $cache (${cache.length() / (1024 * 1024)} MB)")
  }

  private fun sendRequests(fail: (String) -> Nothing) {
    if (requests.get().isEmpty()) return
    val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
    repeat(rounds.get()) {
      for (request in requests.get()) {
        val publisher = request.body?.let { HttpRequest.BodyPublishers.ofString(it) } ?: HttpRequest.BodyPublishers.noBody()
        val response = try {
          client.send(
            HttpRequest.newBuilder(URI(request.url)).method(request.method, publisher).timeout(Duration.ofSeconds(30)).build(),
            HttpResponse.BodyHandlers.discarding(),
          )
        } catch (e: Exception) {
          fail("Training request ${request.method} ${request.url} failed: $e")
        }
        if (response.statusCode() >= 400) {
          fail("Training request ${request.method} ${request.url} returned ${response.statusCode()}")
        }
      }
    }
  }
}

/**
 * Adds an `aotCache` task to an application (the `application` plugin): an [AotCacheTraining] run of the installed
 * distribution (`installDist`), by its start script, on the project's Java toolchain, so the cache matches the
 * application as it ships.
 *
 * Run the installed application with the cache with `JAVA_OPTS=-XX:AOTCache=build/aot/<name>.aot`. The cache also
 * matches a copy of the installed directory that keeps the files' modification times, such as `cp -a`, `rsync -a` or
 * a tar archive of it, on the same JDK build.
 */
class AotCachePlugin : Plugin<Project> {
  override fun apply(project: Project) = with(project) {
    pluginManager.withPlugin("application") {
      val application = extensions.getByType(JavaApplication::class.java)
      val toolchains = extensions.getByType(JavaToolchainService::class.java)
      val launcher = toolchains.launcherFor(extensions.getByType(JavaPluginExtension::class.java).toolchain)
      val installDist = tasks.named("installDist", Sync::class.java)
      val installDir = installDist.map { it.destinationDir }
      val applicationName = provider { application.applicationName }

      tasks.register("aotCache", AotCacheTraining::class.java) {
        group = "application"
        description = "Runs the installed application once, to write its JVM AOT cache"
        trainedFiles.from(installDist)
        // the JVM checks the jars' modification times against the cache, so a copy of the same jars with new times,
        // such as a rerun of installDist, needs a new training run
        inputs.property("installedFileTimestamps", installDir.map { dir ->
          dir.walkTopDown().filter { it.isFile }.map { "${it.relativeTo(dir)}:${it.lastModified()}" }.sorted().toList()
        })
        cacheFile.convention(layout.buildDirectory.file(applicationName.map { "aot/$it.aot" }))
        command.add(installDir.zip(applicationName) { dir, name -> File(dir, "bin/$name").path })
        environment.put("JAVA_HOME", launcher.map { it.metadata.installationPath.asFile.path })
        // locationOnly: the path, without the dependency on this task that its output carries
        environment.put("JAVA_OPTS", cacheFile.locationOnly.map { "-XX:AOTCacheOutput=${it.asFile.path}" })
      }
      Unit
    }
  }
}
