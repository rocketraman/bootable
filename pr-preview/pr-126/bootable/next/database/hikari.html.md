# Connection pool (HikariCP)

> HikariCP, configured from db.pool, with its metrics through OpenTelemetry.

`boot-db-pool-hikari` supplies the pool, `HikariConnectionPoolFactory`, configured by `HikariPoolConfig` from `db.pool`. The pool opens no connection until the application starts, when `boot-db-jdbc` validates one.

## Settings

Every setting is optional, and keeps HikariCP’s default when unset, except `maximumPoolSize`. That defaults to the driver’s hint, where it has one: SQLite’s is a single connection. Setting it above the hint is allowed, but logs a warning.

```hocon
db.pool {
  poolName = notes              (1)
  maximumPoolSize = 20          (2)
  connectionTimeout = 5s        (3)
  leakDetectionThreshold = 30s  (4)
}
```

- (1) Names the pool in logs, JMX, and its [metrics](https://rocketraman.github.io/bootable/bootable/next/database/hikari.html.md#metrics).
- (2) Sizes the pool to what the database and the application’s load call for.
- (3) Fails a request that can’t get a connection within this time, rather than after HikariCP’s 30 seconds.
- (4) Logs a possible leak: a connection out of the pool for longer than this.

The others include `minimumIdle`, `idleTimeout`, `maxLifetime`, `keepaliveTime`, `validationTimeout`, `connectionInitSql`, `transactionIsolation`, and `registerMbeans`: see [all the settings](https://rocketraman.github.io/bootable/bootable/next/api/boot-db-pool-hikari/com.github.rocketraman.bootable.db.jdbc/-hikari-pool-config/index.html).

For a HikariCP setting `HikariPoolConfig` doesn’t cover, add a `HikariConfigCustomizer` to its set binding (Kodein `inBindSet`, Koin a definition with a qualifier of its own, Metro `@IntoSet`), or, with vanilla wiring, pass one to `HikariConnectionPoolFactory`. It runs after the typed settings are applied.

## Metrics

`boot-db-pool-hikari-otel`, in place of `boot-db-pool-hikari`, records the pool’s metrics through OpenTelemetry, such as `db.client.connections.usage` and `db.client.connections.wait_time`. Each carries the pool’s name, as `pool.name`, so set `poolName`. [Telemetry](https://rocketraman.github.io/bootable/bootable/next/database/index.html.md#telemetry) shows how to wire it.

It sets the pool’s `metricsTrackerFactory`. A tracker factory set by a customizer that runs before it still receives every event; one set by a customizer that runs after it replaces it.

## Reference

- [`HikariPoolConfig`](https://rocketraman.github.io/bootable/bootable/next/api/boot-db-pool-hikari/com.github.rocketraman.bootable.db.jdbc/-hikari-pool-config/index.html): the settings
- [`HikariConnectionPoolFactory`](https://rocketraman.github.io/bootable/bootable/next/api/boot-db-pool-hikari/com.github.rocketraman.bootable.db.jdbc/-hikari-connection-pool-factory/index.html): the pool
- [`HikariConfigCustomizer`](https://rocketraman.github.io/bootable/bootable/next/api/boot-db-pool-hikari/com.github.rocketraman.bootable.db.jdbc/-hikari-config-customizer/index.html): for the other settings
- [`hikariPoolMetrics`](https://rocketraman.github.io/bootable/bootable/next/api/boot-db-pool-hikari-otel/com.github.rocketraman.bootable.db.jdbc/hikari-pool-metrics.html): the metrics
