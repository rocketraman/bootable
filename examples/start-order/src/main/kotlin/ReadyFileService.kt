import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.Ordering
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.logging.log4j.kotlin.logger
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeText

data class ReadyFileConfig(val path: String)

/**
 * A ready file: it exists only while the app takes traffic, so something outside the process, such as a
 * Kubernetes `exec` readiness probe, a load balancer's health check script, or a supervisor, can tell when to
 * send requests.
 *
 * It is in [AppPhase.Announce], after the server's phase, so the file appears only once the server is listening.
 * At shutdown, the reverse order removes the file before the server stops, so traffic drains while the requests
 * in flight are still answered.
 */
class ReadyFileService(config: ReadyFileConfig) : AppService {
  private val logger = logger()
  private val path = Path.of(config.path)

  override suspend fun start() {
    withContext(Dispatchers.IO) { path.createParentDirectories().writeText("ready\n") }
    logger.info { "Ready: wrote ${path.absolute()}" }
  }

  override suspend fun shutdown() {
    if (withContext(Dispatchers.IO) { path.deleteIfExists() }) logger.info { "Not ready: removed ${path.absolute()}" }
  }

  override fun ordering() = Ordering(AppPhase.Announce)
}
