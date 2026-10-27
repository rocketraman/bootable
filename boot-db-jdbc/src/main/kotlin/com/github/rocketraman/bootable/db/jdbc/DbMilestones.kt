package com.github.rocketraman.bootable.db.jdbc

import com.github.rocketraman.bootable.boot.Milestone
import com.github.rocketraman.bootable.boot.milestone

/**
 * The database's milestones, so database modules can order themselves against a role rather than against each
 * other: a migration module provides [SchemaMigrated], and a module that checks the schema starts after it,
 * whichever migration tool the application uses, if any.
 */
object DbMilestones {
  /** The connection pool has started and validated a connection. Provided by [JdbcDataSourceService]. */
  val DataSourceReady: Milestone = milestone("db.dataSource.ready")

  /** The schema has been migrated. Provided by a migration module, such as `boot-db-flyway`'s. */
  val SchemaMigrated: Milestone = milestone("db.schema.migrated")
}
