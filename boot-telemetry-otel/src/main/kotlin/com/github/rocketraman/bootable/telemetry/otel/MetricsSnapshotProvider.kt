package com.github.rocketraman.bootable.telemetry.otel

/**
 * The application's current metrics, rendered for a metrics backend that pulls them rather than having them pushed:
 * something, such as Prometheus, scrapes them over HTTP. Implemented by a module such as
 * `boot-telemetry-otel-prometheus`, and served by an HTTP integration such as `boot-server-http-ktor-otel`, so the
 * server need not know which backend it serves.
 */
fun interface MetricsSnapshotProvider {
  /**
   * The current metrics, in the format that best matches [accept], the scrape request's `Accept` header (`null` when
   * it has none).
   */
  fun snapshot(accept: String?): MetricsSnapshot
}

/** The metrics as a scrape response: the [body], in the format named by [contentType]. */
class MetricsSnapshot(val contentType: String, val body: ByteArray)
