package snippets.testing

import com.github.rocketraman.bootable.boot.AppService
import io.opentelemetry.api.OpenTelemetry
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

data class Session(val id: String, val user: String, val expiresAt: Instant)

// tag::component[]
/** Opens sessions that expire after [ttl], logging and tracing each one, and counting them. */
class Sessions(private val clock: Clock, openTelemetry: OpenTelemetry, private val ttl: Duration = 30.minutes) {
  private val log = logger()
  private val tracer = openTelemetry.getTracer("sessions")
  private val opened = openTelemetry.getMeter("sessions").counterBuilder("sessions.opened").build()
  private val sessions = ConcurrentHashMap<String, Session>()

  fun open(user: String): Session {
    val span = tracer.spanBuilder("open session").startSpan()
    try {
      val session = Session("${user}-${sessions.size}", user, clock.now() + ttl)
      sessions[session.id] = session
      opened.add(1)
      log.info { "Opened session ${session.id}" }
      return session
    } finally {
      span.end()
    }
  }

  fun isValid(session: Session): Boolean = clock.now() < session.expiresAt

  /** Forgets the expired sessions. */
  fun reap() {
    sessions.values.removeIf { !isValid(it) }
  }
}
// end::component[]

/** Reaps the expired sessions every minute, while the application runs. */
class SessionReaper(private val sessions: Sessions) : AppService {
  private val executor = Executors.newSingleThreadScheduledExecutor()

  override suspend fun start() {
    executor.scheduleAtFixedRate(sessions::reap, 1, 1, TimeUnit.MINUTES)
  }

  override suspend fun shutdown() {
    executor.shutdown()
  }
}
