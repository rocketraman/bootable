package snippets.telemetry

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.config.ConfigBinderProvider
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.server.http.ktor.KtorTelemetry
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@ContributesTo(TelemetryScope::class)
interface PrometheusBindings : ConfigBinderProvider {
  // tag::prometheus[]
  // boot-telemetry-otel-prometheus-metro and boot-server-http-ktor-otel-metro contribute the reader, the scrape, and
  // the KtorTelemetry that serves it
  @Provides @IntoSet
  fun statusServer(telemetry: KtorTelemetry): AppService = StatusServer(config("server"), telemetry)  // at /metrics

  // or on a port of its own, telemetry.ktor.hostPort, on the engine boot-server-http-ktor-cio-metro contributes
  // end::prometheus[]
}

@ContributesTo(TelemetryScope::class)
interface MicrometerBridgeUse {
  // tag::micrometer-bridge[]
  // boot-telemetry-otel-micrometer-bridge-metro contributes the MeterRegistry; e.g. a library's Micrometer binder, the
  // executor's metrics, exported with the rest
  @Provides @SingleIn(TelemetryScope::class)
  fun workExecutor(registry: MeterRegistry): ExecutorService =
    ExecutorServiceMetrics.monitor(registry, Executors.newFixedThreadPool(4), "work")
  // end::micrometer-bridge[]
}
