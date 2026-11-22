package snippets.telemetry

import com.github.rocketraman.bootable.boot.Bootable
import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.server.http.ktor.KtorTelemetry
import com.github.rocketraman.bootable.server.http.ktor.KtorTelemetryConfig
import com.github.rocketraman.bootable.telemetry.otel.GcpTelemetry
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryService
import com.github.rocketraman.bootable.telemetry.otel.PrometheusTelemetry
import com.github.rocketraman.bootable.telemetry.otel.SigNozTelemetry
import com.github.rocketraman.bootable.telemetry.otel.openTelemetryMeterRegistry
import com.github.rocketraman.bootable.telemetry.otel.telemetryConfig
import io.ktor.server.cio.CIO
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics
import java.util.concurrent.Executors

fun prometheusApp(binder: ConfigBinder): Bootable {
  // tag::prometheus[]
  val prometheus = PrometheusTelemetry(binder.config("telemetry.prometheus"))
  val ktorConfig = binder.config<KtorTelemetryConfig>("telemetry.ktor")
  val telemetry = OpenTelemetryService(
    telemetryConfig(binder),
    listOf(prometheus, ktorConfig.excludeScrapeFromTraces()),  // registers its reader; no traces of the scrapes
  )
  val ktorTelemetry = KtorTelemetry(telemetry.openTelemetry, setOf(prometheus), ktorConfig)

  return Bootable(
    setOf(
      telemetry,
      StatusServer(binder.config("server"), ktorTelemetry),  // serves the scrape at /metrics
      ktorTelemetry.listener(CIO),                           // or on a port of its own, with telemetry.ktor.hostPort
    ),
  )
  // end::prometheus[]
}

fun sigNozTelemetry(binder: ConfigBinder): OpenTelemetryService =
  // tag::signoz[]
  OpenTelemetryService(
    telemetryConfig(binder),
    listOf(SigNozTelemetry(binder.config("telemetry.signoz"))),
  )
  // end::signoz[]

fun gcpTelemetry(binder: ConfigBinder): OpenTelemetryService =
  // tag::gcp[]
  OpenTelemetryService(
    telemetryConfig(binder),
    listOf(GcpTelemetry(binder.config("telemetry.gcp"))),
  )
  // end::gcp[]

fun micrometerBridge(telemetry: OpenTelemetryService) {
  // tag::micrometer-bridge[]
  val registry: MeterRegistry = openTelemetryMeterRegistry(telemetry.openTelemetry)
  // e.g. a library's Micrometer binder: the executor's metrics, exported with the rest
  val executor = ExecutorServiceMetrics.monitor(registry, Executors.newFixedThreadPool(4), "work")
  // end::micrometer-bridge[]
  executor.shutdown()
}
