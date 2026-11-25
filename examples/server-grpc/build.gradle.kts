plugins {
  kotlin("jvm")
  alias(libs.plugins.protobuf)
  application
}

kotlin {
  jvmToolchain(25)
}

dependencies {
  implementation(project(":boot"))
  implementation(project(":boot-config-hoplite"))
  implementation(project(":boot-logging-log4j2"))
  implementation(project(":boot-server-grpc"))

  // the messages, with protoc's own Kotlin builders; boot-server-grpc brings the runtime of the generated server base
  implementation(libs.protobuf.kotlin)

  testImplementation(project(":boot-test"))
}

// kotpb-grpc-codegen's native executable, on the platforms it publishes one for; elsewhere, its jar, on the JVM
val kotpbNativePlatforms = setOf("linux-x86_64", "linux-aarch_64", "osx-aarch_64", "windows-x86_64")
val kotpbGrpcCodegen = libs.kotpb.grpc.codegen.get().toString()

// protoc, with its built-in Kotlin builders, and kotpb-grpc-codegen, for the coroutine server base and client stub
protobuf {
  protoc { artifact = libs.protobuf.protoc.get().toString() }
  plugins {
    create("grpckt") {
      artifact = if (osdetector.classifier in kotpbNativePlatforms) kotpbGrpcCodegen else "$kotpbGrpcCodegen:jvm@jar"
    }
  }
  generateProtoTasks {
    all().configureEach {
      plugins { create("grpckt") }
      builtins { create("kotlin") }
    }
  }
}

application {
  mainClass.set("AppKt")
}
