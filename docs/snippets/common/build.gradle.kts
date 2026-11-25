plugins {
  kotlin("jvm")
}

// What the snippets share, whichever way they are wired: the database snippets' table, repository, config, and
// migration, and the gRPC snippets' greeter service, generated from src/main/proto by the root build's
// configureGrpcProtos. Only plain Bootable modules here; each wiring project adds the DI flavor it wires with.
dependencies {
  api(project(":boot"))
  api(project(":boot-db-exposed"))
  api(project(":boot-server-grpc"))
  // the generated messages' Kotlin DSL builders
  api(libs.protobuf.kotlin)
}
