package com.github.rocketraman.bootable.db.exposed

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.UuidTable

/** Matches the greeting table that src/test/resources/db/migration creates. */
object GreetingTable : Table("greeting") {
  val id = integer("id")
  val text = text("text")
  override val primaryKey = PrimaryKey(id)
}

/** Matches the note table that src/test/resources/db/migration creates: a Kotlin `Uuid` key, and a foreign key. */
object NoteTable : UuidTable("note") {
  val greeting = integer("greeting_id").references(GreetingTable.id)
  val body = text("body")
}
