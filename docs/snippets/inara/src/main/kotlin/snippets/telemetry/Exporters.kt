package snippets.telemetry

import com.github.rocketraman.bootable.boot.AppServices
import com.github.rocketraman.bootable.boot.BootableModule
import com.github.rocketraman.bootable.boot.bootableModule
import com.github.rocketraman.bootable.config.ConfigModule
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.config.hopliteConfigModule
import com.github.rocketraman.bootable.server.http.ktor.KtorServerEngineModule
import com.github.rocketraman.bootable.server.http.ktor.KtorTelemetryModule
import com.github.rocketraman.bootable.server.http.ktor.ktorServerCioEngineModule
import com.github.rocketraman.bootable.server.http.ktor.ktorTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.GcpTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.MicrometerBridgeModule
import com.github.rocketraman.bootable.telemetry.otel.OpenTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.PrometheusTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.SigNozTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.gcpTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.micrometerBridgeModule
import com.github.rocketraman.bootable.telemetry.otel.openTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.prometheusTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.sigNozTelemetryModule
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics
import org.kodein.di.inara.Binding
import org.kodein.di.inara.DIModule
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

// tag::prometheus[]
@DIModule(composed = true)
interface PrometheusAppModule :
  ConfigModule, BootableModule, OpenTelemetryModule, PrometheusTelemetryModule,
  KtorServerEngineModule, KtorTelemetryModule {
  val statusServerConfig: StatusServerConfig
  @AppServices val statusServer: StatusServer  // serves the scrape at /metrics
}

fun prometheusAppModule() = PrometheusAppModule(
  configModule = hopliteConfigModule(),
  bootableModule = bootableModule(),
  openTelemetryModule = openTelemetryModule(),
  prometheusTelemetryModule = prometheusTelemetryModule(),  // registers its reader, and provides the scrape
  ktorTelemetryModule = ktorTelemetryModule(),              // KtorTelemetry, which serves it; no traces of the scrapes
  // or on a port of its own, telemetry.ktor.hostPort, with the listener ktorTelemetryModule adds, on this engine
  ktorServerEngineModule = ktorServerCioEngineModule(),
  statusServerConfig = config("server"),
  statusServer = Binding.single { StatusServer(statusServerConfig, ktorTelemetry) },
)
// end::prometheus[]

// tag::signoz[]
@DIModule(composed = true)
interface SigNozAppModule : ConfigModule, BootableModule, OpenTelemetryModule, SigNozTelemetryModule

fun sigNozAppModule() = SigNozAppModule(
  configModule = hopliteConfigModule(),
  bootableModule = bootableModule(),
  openTelemetryModule = openTelemetryModule(),
  sigNozTelemetryModule = sigNozTelemetryModule(),  // from telemetry.signoz
)
// end::signoz[]

// tag::gcp[]
@DIModule(composed = true)
interface GcpAppModule : ConfigModule, BootableModule, OpenTelemetryModule, GcpTelemetryModule

fun gcpAppModule() = GcpAppModule(
  configModule = hopliteConfigModule(),
  bootableModule = bootableModule(),
  openTelemetryModule = openTelemetryModule(),
  gcpTelemetryModule = gcpTelemetryModule(),  // from telemetry.gcp
)
// end::gcp[]

// tag::micrometer-bridge[]
@DIModule(composed = true)
interface MicrometerBridgeAppModule : ConfigModule, BootableModule, OpenTelemetryModule, MicrometerBridgeModule {
  val workExecutor: ExecutorService
}

fun micrometerBridgeAppModule() = MicrometerBridgeAppModule(
  configModule = hopliteConfigModule(),
  bootableModule = bootableModule(),
  openTelemetryModule = openTelemetryModule(),
  micrometerBridgeModule = micrometerBridgeModule(),  // a meterRegistry member, over the application's OpenTelemetry
  // e.g. a library's Micrometer binder: the executor's metrics, exported with the rest
  workExecutor = Binding.single {
    ExecutorServiceMetrics.monitor(meterRegistry, Executors.newFixedThreadPool(4), "work")
  },
)
// end::micrometer-bridge[]
