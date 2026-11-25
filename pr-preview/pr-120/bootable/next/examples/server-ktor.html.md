# Ktor server

> A small Ktor server on Netty, with typed config, run by Bootable and buildable as a GraalVM native image.

`server-ktor` is a Ktor server with three routes, wired with plain Kotlin and run by Bootable. It shows the least an HTTP application needs: a `KtorService` with its own routes, typed config for it, and logging. It is also the repository’s GraalVM native-image smoke test, relying only on the native-image metadata bundled in the Bootable jars.

## What it uses

- `boot-server-http-ktor-netty`, for `NettyKtorService`, the server on Ktor’s Netty engine; see [Server (Ktor)](https://rocketraman.github.io/bootable/bootable/next/http/server.html.md).
- `boot-config-hoplite`, for the `server` and `hello` config sections; see [Configuration](https://rocketraman.github.io/bootable/bootable/next/configuration/index.html.md).
- `boot-logging-log4j2`, for the log; see [Logging (Log4j2)](https://rocketraman.github.io/bootable/bootable/next/logging/index.html.md).

The server is an ordinary class, constructed in `main`:

```kotlin
class ExampleServer(config: ServerConfig, private val hello: HelloConfig) : NettyKtorService("test", config) {
  override fun Application.module() {
    routing {
      get("/hello") { call.respond(HttpStatusCode.OK, hello.response) }
      // ...
    }
  }
}

fun main() = boot {
  val binder = hopliteConfigBinder()
  Bootable(
    appServices = setOf(
      ExampleServer(binder.config("server"), binder.config("hello")),
    )
  )
}
```

`ServerConfig` implements `HostPort`, which is how a `KtorService` finds the address to listen on, from `server.host-port` in `src/main/resources/application.conf`.

## Run it

From the repository root:

```shell
./gradlew :examples:server-ktor:run
```

It serves [http://localhost:8000](http://localhost:8000):

- `/` is an HTML index, with links to the other two routes.
- `/hello` answers with `hello.response` from the config, `world!`.
- `/die` answers, then fails the server’s scope: Bootable logs that the service failed, stops every service, and exits with status 1.

## Build a native image

The project applies GraalVM’s native build plugin, so with a GraalVM JDK, `nativeCompile` builds an executable:

```shell
./gradlew :examples:server-ktor:nativeCompile
examples/server-ktor/build/native/nativeCompile/server-ktor
```

The build fails on a missing reflection or resource registration, rather than falling back to the JVM, so a clean run, and a clean shutdown on SIGTERM, shows the bundled metadata is enough.

## Source

[`examples/server-ktor`](https://github.com/rocketraman/bootable/tree/master/examples/server-ktor)
