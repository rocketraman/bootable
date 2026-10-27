import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.DefaultPhase
import com.github.rocketraman.bootable.boot.Ordering
import com.github.rocketraman.bootable.db.jdbc.DbMilestones
import kotlinx.coroutines.runBlocking
import org.apache.logging.log4j.kotlin.logger

/**
 * Stores a few sample entries when the store is empty, so the index page has something to show on a first run.
 * Restarting keeps whatever is stored; delete every entry to have them seeded again.
 *
 * It needs the `entry` table, so it starts after [DbMilestones.SchemaMigrated], in the
 * [DefaultPhase.Initialization] phase, the one that provides it. That also puts it ahead of the server, in the
 * later [DefaultPhase.Serving] phase, so no request sees the store before it is seeded. It names a milestone rather
 * than the migration service: it doesn't care which migration module, if any, creates the table.
 */
class SeedEntriesService(private val entries: EntryRepository) : AppService {
  private val logger = logger()

  override fun start() = runBlocking {
    if (entries.all().isNotEmpty()) return@runBlocking
    for ((name, value) in SEED_ENTRIES) entries.put(name, value)
    logger.info { "Seeded ${SEED_ENTRIES.size} sample entries" }
  }

  override fun ordering() = Ordering(DefaultPhase.Initialization, after = setOf(DbMilestones.SchemaMigrated))

  companion object {
    val SEED_ENTRIES = mapOf(
      "greeting" to "Hello, world!",
      "framework" to "Bootable",
    )
  }
}
