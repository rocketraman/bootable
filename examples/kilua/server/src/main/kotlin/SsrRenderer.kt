import com.github.rocketraman.bootable.boot.AppService
import dev.kilua.ssr.SsrEngine
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.apache.logging.log4j.kotlin.logger
import java.net.ServerSocket
import kotlin.time.Duration.Companion.seconds

data class SsrConfig(
  /** The Node.js executable to run the renderer with. */
  val node: String,
)

/**
 * Kilua's server-side renderer, run by Kilua's [SsrEngine] as a Bootable service: the client, bundled for Node.js by the
 * Kilua Gradle plugin as `ssr.zip`, run as a child process that renders a page for each path it's asked for.
 *
 * `SsrEngine` starts Node.js as soon as it's constructed, so this constructs it in [start], which returns once the
 * renderer has rendered a page. `SsrEngine` has no way to stop Node.js, though: it only stops it from a JVM shutdown
 * hook, so there's nothing for a [shutdown] to do, and Node.js runs until the JVM exits.
 *
 * Bootable starts services in priority order, highest first, and stops them in reverse. This one has the default
 * priority, 0, so it starts before the [TodoServer], whose `KtorService` priority is `Int.MIN_VALUE`: the server never
 * serves a page without a renderer to render it.
 */
class SsrRenderer(
  private val config: SsrConfig,
  /** The server's URL, which the client, rendering in Node.js, calls the API with. */
  private val serverUrl: String,
) : AppService {
  private val logger = logger()

  private lateinit var engine: SsrEngine

  override fun start() {
    // without it, SsrEngine only logs a warning, and then serves empty pages
    checkNotNull(javaClass.getResource("/ssr.zip")) { "No ssr.zip on the classpath: the build copies it from the client" }

    // the renderer is only ever called from here, so any free port will do
    val port = ServerSocket(0).use { it.localPort }
    engine = SsrEngine(nodeExecutable = config.node, port = port, rpcUrlPrefix = serverUrl)

    // SsrEngine starts Node.js in the background; rendering a page here means this service has only started once the
    // renderer works. SsrEngine waits for Node.js to start forever, so give up if it never does.
    runBlocking {
      withTimeout(30.seconds) { engine.getSsrContent("/?revision=-1") }
    }
    logger.info { "Renderer listening on port $port" }
  }

  /**
   * Returns `index.html` with the page at [path] rendered into it, or as it is, for the client to render in the browser,
   * if the renderer couldn't render it.
   */
  suspend fun render(path: String): String = engine.getSsrContent(path)
}
