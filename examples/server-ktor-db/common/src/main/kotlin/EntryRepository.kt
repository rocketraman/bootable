import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.db.jdbc.JdbcDataSourceService
import javax.sql.DataSource

/**
 * Creates the `entry` table when the application starts.
 *
 * Its priority is just below [JdbcDataSourceService.PRIORITY], so it runs after the pool has started (and validated
 * its connection), and before the server, which starts last. A real application would use migrations instead.
 */
class EntrySchema(private val dataSource: DataSource) : AppService {
  override fun start() {
    dataSource.connection.use { connection ->
      connection.createStatement().use {
        it.execute("CREATE TABLE IF NOT EXISTS entry (name TEXT PRIMARY KEY, value TEXT NOT NULL)")
      }
    }
  }

  override fun priority() = JdbcDataSourceService.PRIORITY - 1
}

/** Reads and writes entries with plain JDBC, using connections from the pool. */
class EntryRepository(private val dataSource: DataSource) {
  fun all(): Map<String, String> = dataSource.connection.use { connection ->
    connection.prepareStatement("SELECT name, value FROM entry ORDER BY name").use { statement ->
      statement.executeQuery().use { rs ->
        buildMap {
          while (rs.next()) put(rs.getString("name"), rs.getString("value"))
        }
      }
    }
  }

  fun get(name: String): String? = dataSource.connection.use { connection ->
    connection.prepareStatement("SELECT value FROM entry WHERE name = ?").use { statement ->
      statement.setString(1, name)
      statement.executeQuery().use { rs -> if (rs.next()) rs.getString("value") else null }
    }
  }

  fun put(name: String, value: String) {
    dataSource.connection.use { connection ->
      connection.prepareStatement(
        "INSERT INTO entry (name, value) VALUES (?, ?) ON CONFLICT (name) DO UPDATE SET value = excluded.value"
      ).use { statement ->
        statement.setString(1, name)
        statement.setString(2, value)
        statement.executeUpdate()
      }
    }
  }

  /** Returns whether there was an entry to delete. */
  fun delete(name: String): Boolean = dataSource.connection.use { connection ->
    connection.prepareStatement("DELETE FROM entry WHERE name = ?").use { statement ->
      statement.setString(1, name)
      statement.executeUpdate() > 0
    }
  }
}
