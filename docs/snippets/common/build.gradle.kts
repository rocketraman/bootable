plugins {
  kotlin("jvm")
}

// What the database snippets share, whichever way they are wired: the table, the repository, the config, and the
// migration. Only plain Bootable modules here; each wiring project adds the DI flavor it wires with.
dependencies {
  api(project(":boot"))
  api(project(":boot-db-exposed"))
}
