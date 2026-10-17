package com.github.rocketraman.bootable.db.exposed

import com.github.rocketraman.bootable.db.flyway.FlywayMigrationService
import com.github.rocketraman.bootable.db.jdbc.JdbcDataSourceService
import de.infix.testBalloon.framework.core.testSuite
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.Database

/** The greeting table as the migration created it, plus a column the migration didn't add. */
private object GreetingWithMoodTable : Table("greeting") {
  val id = integer("id")
  val text = text("text")
  val mood = text("mood")
  override val primaryKey = PrimaryKey(id)
}

private object MissingTable : Table("missing") {
  val id = integer("id")
  override val primaryKey = PrimaryKey(id)
}

/** A database migrated by Flyway from src/test/resources/db/migration. */
private suspend fun withMigratedDatabase(block: suspend (Database) -> Unit) = withDataSource { database ->
  FlywayMigrationService(database.dataSource).start()
  block(exposedDatabase(database.dataSource))
}

private fun failure(service: SchemaVerificationService) = runCatching { service.start() }.exceptionOrNull()

val SchemaVerificationServiceTest by testSuite {
  test("passes when the schema matches the tables") {
    withMigratedDatabase { db ->
      SchemaVerificationService(db, setOf(GreetingTable, NoteTable)).start()
    }
  }

  test("fails on a missing table, listing the SQL that creates it") {
    withMigratedDatabase { db ->
      val e = failure(SchemaVerificationService(db, setOf(GreetingTable, MissingTable)))
      assert(e is SchemaVerificationException)
      e as SchemaVerificationException
      assert(e.statements.single().startsWith("CREATE TABLE IF NOT EXISTS missing"))
      assert("CREATE TABLE IF NOT EXISTS missing" in e.message.orEmpty())
    }
  }

  test("fails on a missing column, listing the SQL that adds it") {
    withMigratedDatabase { db ->
      val e = failure(SchemaVerificationService(db, setOf(GreetingWithMoodTable)))
      assert(e is SchemaVerificationException)
      e as SchemaVerificationException
      assert(e.statements.single() == "ALTER TABLE greeting ADD mood TEXT NOT NULL")
    }
  }

  test("only warns when set to WARN") {
    withMigratedDatabase { db ->
      SchemaVerificationService(db, setOf(MissingTable), SchemaVerification.WARN).start()
    }
  }

  test("checks nothing when set to OFF") {
    withDataSource { database ->
      val db = exposedDatabase(database.dataSource)
      // the pool is closed, so any check would fail
      database.shutdown()
      SchemaVerificationService(db, setOf(MissingTable), SchemaVerification.OFF).start()
    }
  }

  test("checks nothing when no tables are registered") {
    withDataSource { database ->
      val db = exposedDatabase(database.dataSource)
      database.shutdown()
      SchemaVerificationService(db, emptySet()).start()
    }
  }

  test("starts after the pool and migrations, and before services at the default priority") {
    val priority = SchemaVerificationService.PRIORITY
    assert(priority < FlywayMigrationService.PRIORITY && priority < JdbcDataSourceService.PRIORITY && priority > 0)
  }
}
