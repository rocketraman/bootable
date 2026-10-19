# Agent notes

## Running the examples

- The examples (`:examples:*:run`) are long-running services: `boot` waits for a shutdown signal, so `run` never
  exits. Run them under a timeout and look for the expected log line, e.g.
  `timeout 90 ./gradlew -q :examples:wiring-metro:run 2>&1 | grep 'greeting:'`. Don't chain several `run` tasks in one
  Gradle invocation; the first one blocks the rest.

## Documentation

Use asciidoc.
Use one-sentence-per-line convention.

## Database tests

The `boot-db-jdbc-postgresql` and `boot-db-jdbc-mysql` tests run real servers with Testcontainers.
They are skipped where Docker is unavailable, but not on CI, where `CI` is set.
With rootless Podman, run them with `DOCKER_HOST=unix:///run/user/$(id -u)/podman/podman.sock TESTCONTAINERS_RYUK_DISABLED=true`.
