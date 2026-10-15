# Agent notes

## Running the examples

- The examples (`:examples:*:run`) are long-running services: `boot` waits for a shutdown signal, so `run` never
  exits. Run them under a timeout and look for the expected log line, e.g.
  `timeout 90 ./gradlew -q :examples:wiring-metro:run 2>&1 | grep 'greeting:'`. Don't chain several `run` tasks in one
  Gradle invocation; the first one blocks the rest.
- If startup fails with a Hoplite `ConfigException: ... Unresolved substitution` whose value is shell code, the
  environment contains exported bash functions (`BASH_FUNC_*%%`, e.g. from Lmod or `export -f`).
  `hopliteConfigBinder()` reads every environment variable, and Hoplite tries to resolve the `${...}` inside them.
  Strip them before running:

  ```bash
  env $(env | grep -o '^BASH_FUNC_[^=]*' | sed 's/^/-u /') ./gradlew -q :examples:wiring-metro:run
  ```
