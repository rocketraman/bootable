package com.github.rocketraman.bootable.db.jdbc

import com.github.rocketraman.bootable.boot.HealthCheck
import com.github.rocketraman.bootable.boot.HealthStatus
import javax.sql.DataSource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A readiness [HealthCheck] that the database answers: it takes a connection from [dataSource], usually the pool of
 * [JdbcDataSourceService], and checks that it is valid, on a thread of `Dispatchers.IO`.
 *
 * Readiness answers within its own `checkTimeout`, whether or not this has, but the thread stays blocked until this
 * returns: [timeout] bounds the wait for the database's answer, and the pool's own connection timeout bounds taking the
 * connection, when every connection is in use, so set both low, or a slow database holds a thread per check for long.
 *
 * Opt in to it, rather than getting it with the pool: a readiness check on a shared database takes every instance out
 * of rotation at once when the database is down, which turns failing requests into refused ones. That is what you
 * want only when the application can do nothing useful without its database.
 */
class JdbcHealthCheck(
  private val dataSource: DataSource,
  private val timeout: Duration = DEFAULT_TIMEOUT,
) : HealthCheck {
  override fun name() = "jdbc"

  override suspend fun check(): HealthStatus = withContext(Dispatchers.IO) {
    dataSource.connection.use { connection ->
      // isValid takes whole seconds, and 0 means no timeout
      if (connection.isValid(timeout.inWholeSeconds.toInt().coerceAtLeast(1))) HealthStatus.Healthy
      else HealthStatus.Unhealthy("Connection from the pool is not valid")
    }
  }

  companion object {
    val DEFAULT_TIMEOUT = 2.seconds
  }
}
