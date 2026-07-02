# GraalVM native-image metadata (TEMPORARY workaround)

The reflection entries in `reachability-metadata.json` in this directory are a **temporary
workaround**, not permanent bootable-owned metadata.

They are needed only because [log4j-api-kotlin](https://github.com/apache/logging-log4j-kotlin)'s
`logger()` resolves the logger name via `this::class` (kotlin-reflect), which forces the
*calling* class to be registered for reflection. The `StopSignalHandlerLifecycleController`
signal handler runs on the OS-signal (HUP/INT/TERM) shutdown path, which the native-image
tracing agent cannot observe (the agent dies with the app). Without these entries a native
image built by a consumer fails at shutdown with:

```
KotlinReflectionInternalError: Unresolved class …
```

The proper fix belongs in log4j-api-kotlin: resolve the logger name without kotlin-reflect
(e.g. from `javaClass`) so no consumer ever needs to reflection-register its logger-owning
classes. Once bootable depends on a log4j-api-kotlin release that does this, **delete this
directory** (`reachability-metadata.json` and this README).

The genuinely bootable-owned metadata lives in the `boot-config-common` (`HostPort`) and
`boot-logging-log4j2` (`log4j2-base*.xml` resources) modules and is permanent.
