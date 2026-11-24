# ORM (Exposed)

> An Exposed Database over the pool, with transactions and a check of the schema.

`boot-db-exposed` gives the application an Exposed `Database` over the pool, and checks the schema against the application’s tables when it starts.

## The Database

`ExposedDatabaseService` owns the `Database`, configured by `ExposedConfig` from `db.exposed`: the default isolation level, read-only, retries, query timeout, and so on; see [all the settings](https://rocketraman.github.io/bootable/bootable/next/api/boot-db-exposed/com.github.rocketraman.bootable.db.exposed/-exposed-config/index.html). The settings apply to that `Database` only. On shutdown, before the pool closes, it unregisters the `Database` from Exposed’s process-wide registry.

For Exposed settings `ExposedConfig` doesn’t cover, add an `ExposedConfigCustomizer` to its set binding.

## Transactions

`db.withTransaction { … }` runs a suspending transaction on the `Database`'s dispatcher, `Dispatchers.IO` by default. Called inside another, it joins it, so several calls make one unit of work.

```kotlin
class NoteRepository(private val db: Database) {
  suspend fun add(text: String): Int = db.withTransaction {
    NoteTable.insert { it[NoteTable.text] = text }[NoteTable.id]
  }

  suspend fun count(): Long = db.withTransaction {
    NoteTable.selectAll().count()
  }
}
```

> **Warning:** Don’t call Exposed’s own `transaction { … }` from a coroutine, such as a Ktor route. It blocks the thread it’s called on until the transaction ends, while its JDBC statements wait on the database, rather than running them on the `Database`'s dispatcher. Blocking a Ktor route’s thread holds up the other requests that thread serves. Use `withTransaction` instead, which suspends.

## Schema verification

`SchemaVerificationService` checks, after the migrations, that the schema matches the Exposed tables in its `Set<Table>`. If it doesn’t, it fails the boot, listing the SQL the schema needs, or only warns, with `schemaVerification = WARN`.

```kotlin
object NoteTable : Table("note") {
  val id = integer("id").autoIncrement()
  val text = text("text")
  override val primaryKey = PrimaryKey(id)
}
```

How a table gets into the set depends on the wiring style; see [Wire](https://rocketraman.github.io/bootable/bootable/next/database/index.html.md#_wire) on the Database page.

Exposed logs SQL statements at DEBUG on its `Exposed` logger.

## Reference

- [`ExposedDatabaseService`](https://rocketraman.github.io/bootable/bootable/next/api/boot-db-exposed/com.github.rocketraman.bootable.db.exposed/-exposed-database-service/index.html)
- [`ExposedConfig`](https://rocketraman.github.io/bootable/bootable/next/api/boot-db-exposed/com.github.rocketraman.bootable.db.exposed/-exposed-config/index.html)
- [`SchemaVerificationService`](https://rocketraman.github.io/bootable/bootable/next/api/boot-db-exposed/com.github.rocketraman.bootable.db.exposed/-schema-verification-service/index.html)
- [`withTransaction`](https://rocketraman.github.io/bootable/bootable/next/api/boot-db-exposed/com.github.rocketraman.bootable.db.exposed/with-transaction.html)
