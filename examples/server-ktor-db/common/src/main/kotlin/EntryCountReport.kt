import org.apache.logging.log4j.kotlin.logger

/**
 * Logs how many entries are stored: the work of a periodic loop, `entry-count`, which runs it every
 * `entries.report.interval`.
 *
 * Only the leader of the application's instances runs it, as elected through a Kubernetes Lease (`leader.kubernetes`):
 * run as several replicas, one of them logs the count, not each. Outside Kubernetes, as when run locally,
 * `leader.kubernetes.assumeLeader` skips the election.
 */
class EntryCountReport(private val entries: EntryRepository) {
  private val logger = logger()

  suspend fun report() {
    val count = entries.all().size
    logger.info { "Entry count: $count" }
  }
}
