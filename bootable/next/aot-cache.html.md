# Faster start: the JVM AOT cache

> Start a Bootable application faster from a JVM AOT cache, trained by a Gradle task, in a Jib image, or in a Dagger pipeline.

On JDK 24 and later, an AOT cache ([JEP 483](https://openjdk.org/jeps/483), with the one-step training of [JEP 514](https://openjdk.org/jeps/514) in JDK 25) holds the classes an application loaded and linked during a training run, so the next start reads them from the cache instead of loading, verifying and linking them again. A Bootable application’s start loads thousands of classes, from Log4j, Hoplite, the database and telemetry libraries, and Ktor, so it gains a lot from one. See Spring’s [guide to the AOT cache](https://docs.spring.io/spring-framework/reference/integration/aot-cache.html) for more on how it works.

## Producing the cache

The training run starts the application with `-XX:AOTCacheOutput=<file>`, and the JVM writes the cache when the application exits. The `bootable.aot-cache` plugin in [build-logic](https://github.com/rocketraman/bootable/tree/master/build-logic) adds an `aotCache` task to an application, which does that with the installed distribution (`installDist`), by its start script, on the project’s Java toolchain:

- it starts the application, and waits for Bootable’s `=> Running` line, which it logs once every service has started;
- it sends the application the requests given to it, if any, so the cache also covers the request path, not just the start;
- it stops the application with SIGTERM, so it shuts down as it would in production, and the JVM writes the cache as it exits.

```kotlin
plugins {
  application
  id("bootable.aot-cache")
}

tasks.aotCache {
  environment.put("SERVER_HOSTPORT", "127.0.0.1:18001")
  request("PUT", "http://127.0.0.1:18001/entries/trained", "by the AOT training run")
  request("GET", "http://127.0.0.1:18001/entries/trained")
  rounds = 20
}
```

Train with the configuration the application runs with in production, as the cache only holds the classes the training loaded. Telemetry is the usual difference: with an OTLP endpoint, the OpenTelemetry SDK builds its OTLP exporters, and loads about 760 classes that a training run without one leaves out of the cache. So the examples' training runs set `OTEL_EXPORTER_OTLP_ENDPOINT` to an address nothing listens on: the training exports nothing, and logs a failed export as it stops. The Jib image below, started with an endpoint, loaded 914 classes from outside a cache trained without one, and 144 from outside one trained with one, as many as it does without an endpoint.

The plugin is one file, `AotCache.kt`, with no dependencies but Gradle’s own, so an application outside this repository can copy it into its own `buildSrc` or build logic. The task type, `AotCacheTraining`, runs any command that starts the application with `-XX:AOTCacheOutput`, such as a container, as the Jib example below does. The training needs a JVM that writes the cache as it exits, on SIGTERM, so it runs on Linux and macOS, not Windows.

```shell
./gradlew :examples:server-ktor-db:vanilla:aotCache
```

writes `examples/server-ktor-db/vanilla/build/aot/vanilla.aot`, and the application’s output during the training next to it, in `vanilla.aot.log`. The cache is about 66 MB for that example, or 60 MB for a training run that only starts it.

## Running with the cache

Run the application with `-XX:AOTCache=<file>`, on the same JDK, from the same installed directory:

```shell
cd examples/server-ktor-db/vanilla
JAVA_OPTS=-XX:AOTCache=build/aot/vanilla.aot build/install/vanilla/bin/vanilla
```

The JVM only uses the cache with the JDK build that wrote it, and the same class path, of the same jars, which it checks by their sizes and modification times. On any difference, it warns, such as `The AOT cache was created by a different version or build of HotSpot`, or `This file is not the one used while building the AOT cache`, and runs without the cache. So train with the application as it ships, and ship them together:

- the installed directory can move, but a copy of it has to keep the files' modification times, as `cp -a`, `rsync -a` or a tar archive of it do; a rerun of `installDist` writes new ones, so `aotCache` runs again after it;
- in a container image, train in a container of the image itself, as the Jib example below does.

`-XX:AOTMode=on` makes the JVM fail to start rather than run without the cache, which shows a mismatch at once. `-Xlog:aot` logs whether the JVM opened the cache, and `-Xlog:class+load` logs each class that came from it with `source: shared objects file`.

## The gain

On the vanilla [examples/server-ktor-db](https://github.com/rocketraman/bootable/tree/master/examples/server-ktor-db) app, on JDK 25, the median of 7 starts:

|  | Without the cache | With the cache |
| --- | --- | --- |
| From launch to `=> Running` | 721 ms | 307 ms |
| From `boot` to `=> Running` | 690 ms | 229 ms |
| From `boot` to the services starting | 485 ms | 160 ms |
| The first `PUT /entries/{name}` | 44 ms | 12 ms |

The cache held 8,217 of the 8,368 classes the application loaded through its start and those first requests. A cache from a training run that only starts the application starts it as fast, but leaves the first request at 47 ms: the training’s requests are what cover the request path.

## In a container image, with Jib

[examples/server-ktor-db/jib](https://github.com/rocketraman/bootable/tree/master/examples/server-ktor-db/jib) builds the vanilla app into a container image, with [Jib](https://github.com/GoogleContainerTools/jib), with its AOT cache in it:

1. `jibBuildTar` builds the image without a cache, and the container runtime loads it;
2. `aotCache` runs that image once, in a container, with `-XX:AOTCacheOutput` in place of the image’s `-XX:AOTCache`, and the cache’s directory mounted from the build, so the training runs on the image’s own JDK, with its class path, `-cp @/app/jib-classpath-file`;
3. `jibDockerBuild`, or `jib` to push to a registry, builds the final image, with the cache as one more layer, at `/app/aot/server-ktor-db.aot`, and `-XX:AOTCache` in its JVM flags.

Jib’s images are reproducible, with every file’s modification time fixed, so the final image’s jars are the same, to the JVM, as those the cache was trained with. Two settings make the cache usable at all: `containerizingMode = "packaged"`, which puts the application’s own classes in a jar, as the JVM won’t use a cache with a non-empty directory on the class path, as Jib’s default `/app/classes` is; and the training’s entrypoint, the image’s own but for the cache’s flag, as the JVM takes only one of `-XX:AOTCache` and `-XX:AOTCacheOutput`.

```shell
./gradlew :examples:server-ktor-db:jib:jibDockerBuild -PcontainerCli=podman
podman run --rm -p 8001:8001 bootable-examples/server-ktor-db-jib
```

`containerCli` is the container runtime’s command, for loading and running the training image and for `jibDockerBuild`: `docker` by default, or `podman`. The cache adds a 68 MB layer to the image, which used 9,406 of the 9,550 classes the application had loaded once it was running, and started from launch to `=> Running` in a median of 399 ms with the cache, and 896 ms without it, over 7 starts, each in a new container. With an OTLP endpoint, it started in 400 ms with the cache, as `boot-telemetry-otel` creates the exporters' HTTP clients on their first export, after the start.

The JVM’s choice of garbage collector, and so part of what the cache holds, depends on the container’s memory and CPUs: train with the same limits the application runs with.

## In a CI pipeline, with Dagger

The `server-ktor-db` [Dagger](https://dagger.io) module, in `.dagger/modules/server-ktor-db/main.dang`, builds the Jib image above with its cache as a CI pipeline, in containers, with nothing on the host but the `dagger` CLI and a container runtime. It is written in [Dang](https://docs.dagger.io/reference/sdks/dang), Dagger’s own language, and needs the Dagger 1.0 beta. It is one file, with no generated code and no package manager.

Dagger needs a container runtime: Docker, or Podman with its socket enabled. `mise install` provides the Dagger CLI. The module needs the 1.0 beta, which the stable CLI downloads and runs when `DAGGER_X_RELEASE` names it; `mise.toml` sets that variable. Without mise, install a stable CLI of 0.21.3 or later, and export `DAGGER_X_RELEASE=v1.0.0-beta.28`. Bootable sends no traces to Dagger Cloud unless you provide your own `DAGGER_CLOUD_TOKEN` or run `dagger login`.

```bash
dagger check
dagger api call server-ktor-db image export --path=build/server-ktor-db.tar
podman load -i build/server-ktor-db.tar
```

`dagger check` runs every function marked `@check`, which here is `check`. The other functions are called by name, as `dagger api call server-ktor-db <function>`.

| Function | What it does |
| --- | --- |
| `check` | The pull request check: builds the image, and runs `verify`. Publishes nothing. |
| `jib-image` | The Jib image, without a cache, as a tarball, from Gradle’s `jibBuildTar`, built in `eclipse-temurin:25-jdk`, with Gradle’s user home, and its build cache, and Jib’s base image cache, in cache volumes. |
| `runtime` | `jib-image`, imported as a container. |
| `aot-cache` | The cache, from a training run of `runtime`: the app started as the image’s entrypoint does, but with `-XX:AOTCacheOutput` in place of its `-XX:AOTCache`, then `=> Running`, the training requests, and SIGTERM. `--requests` and `--rounds` change the requests. |
| `image` | `runtime`, with the cache where its entrypoint’s `-XX:AOTCache` looks for it, at `/app/aot/server-ktor-db.aot`. `image export --path=<file>.tar` writes it to disk. |
| `verify` | Starts `image`, and fails unless at least `--min-percent`, by default 90, of the classes it loads come from the cache. `verify stdout` says how many. |
| `env` | The build container before Gradle has run. `dagger api call server-ktor-db env terminal` opens a shell in it. |
| `dev` | `image`, on port 8001, exporting its traces and metrics over OTLP to the module’s `collector` setting, which `dagger.toml` wires to the `otel-lgtm` module’s `otel-stack`. `--tracing=false` exports nothing, and starts no collector. |

The `otel-lgtm` module, in `.dagger/modules/otel-lgtm/main.dang`, runs Grafana’s `otel-lgtm` stack, to demo the traces and metrics: an OpenTelemetry collector, Prometheus, Tempo, Loki and Grafana, in one container. It isn’t part of Bootable, so it is a module of its own, and `dagger.toml` wires its `otel-stack` into `server-ktor-db`'s `collector` setting, as `dag://otel-lgtm/otel-stack`. `mise.toml` has a task for each way to run the example, each on port 8001:

| Task | What it runs |
| --- | --- |
| `mise run demo` | `dagger start server-ktor-db/dev otel-lgtm/otel-stack`: the app, exporting to the stack, and the stack, tunneling the app’s port, 8001, Grafana’s, 3000, and the collector’s, 4318, to `localhost`. |
| `mise run demo-without-tracing` | `dagger api call server-ktor-db dev --tracing=false up`: the app alone, exporting nothing. `dagger start` can’t pass a function’s arguments, so this calls `dev` with `--tracing=false`, and `up` tunnels its port. |

`dagger start` doesn’t show the services' own output, such as the app’s log: its view shows only the tunnels and the ports' health checks. `dagger start --progress logs` prints it, under the function that defines each service, such as `ServerKtorDb.dev`, and `mise run demo — --progress logs` passes the flag on.

Jib builds the image, without a container runtime, and the module trains the cache in a container of it, then adds the cache to that same container, so the JDK build, and the jars' sizes and modification times, are the training’s by construction. The image is defined once, in the Jib example’s build, for both Gradle and Dagger: its base image, JVM flags, class path, port and environment. The Jib build pins its base image, the JRE, by digest, so the JDK build only changes when the digest does, and the cache is trained again with it; `dagger.lock` pins the build’s JDK image.

Dagger sets `OTEL_*` variables in every container, for its own tracing, and forwards what they collect to Dagger Cloud when the CLI is logged in to it. The module unsets them before it starts the app, which would otherwise export its own telemetry there, and sets an OTLP endpoint of its own, on the container’s localhost, where nothing listens, so the cache has the OTLP exporters' classes, as in the Gradle examples.

Its image used 9,404 of the 9,561 classes it loaded from its cache, and started from launch to `=> Running` in a median of 386 ms with the cache, and 900 ms without it, over 7 starts, each in a new container.

## Which to use

- The `aotCache` task, for an application shipped as its installed distribution, or to try the cache locally: it trains on the build’s own JDK toolchain, and needs nothing else.
- Jib, for a container image built by Gradle, without a Dockerfile: the training needs a container runtime, to run the image once.
- Dagger, for a CI pipeline that runs the same way on a laptop and on any CI service, where the build, the training and the image are each a cached, containerized step: it builds the same Jib image, so the host needs only Dagger and a container runtime, not a JDK, and the training runs on the image’s own JDK by construction.
