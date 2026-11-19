import com.github.kagkarlsson.scheduler.task.helper.RecurringTask
import com.github.kagkarlsson.scheduler.task.helper.Tasks
import com.github.rocketraman.bootable.scheduler.db.DbSchedulerTaskProvider
import com.github.rocketraman.bootable.scheduler.db.fixedDelay
import kotlinx.coroutines.runBlocking
import org.apache.logging.log4j.kotlin.logger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** The settings of the entry digest, from the `entries.digest` config section. */
data class EntryDigestConfig(val interval: Duration = 1.minutes)

/**
 * Logs a digest of the stored entries: the work of a recurring db-scheduler task, `entry-digest`, which runs it every
 * `entries.digest.interval`.
 *
 * Unlike the entry count's in-process loop, it needs no leader: db-scheduler keeps the task's next execution in the
 * database, and runs each on whichever of the application's instances locks its row first, so once across them all.
 */
class EntryDigest(private val entries: EntryRepository, private val config: EntryDigestConfig) :
  DbSchedulerTaskProvider {
  private val logger = logger()

  /** The db-scheduler task. */
  val task: RecurringTask<Void> = Tasks.recurring("entry-digest", fixedDelay(config.interval)).execute { _, _ ->
    // db-scheduler runs its tasks on threads of its own
    val names = runBlocking { entries.all().keys }
    logger.info { "Entry digest: ${names.size} entries: ${names.joinToString()}" }
  }

  override fun tasks() = listOf(task)
}
