package snippets.telemetry

import com.github.rocketraman.bootable.server.http.ktor.ktorServerCioEngineModule
import com.github.rocketraman.bootable.boot.bindAppService
import com.github.rocketraman.bootable.boot.bootableModule
import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.config.hopliteConfigModule
import com.github.rocketraman.bootable.server.http.ktor.KtorTelemetry
import com.github.rocketraman.bootable.server.http.ktor.ktorTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.gcpTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.micrometerBridgeModule
import com.github.rocketraman.bootable.telemetry.otel.openTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.prometheusTelemetryModule
import com.github.rocketraman.bootable.telemetry.otel.sigNozTelemetryModule
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import org.kodein.di.instance
import org.kodein.di.new
import org.kodein.di.singleton
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

fun prometheusDI() = DI {
  import(bootableModule)
  import(hopliteConfigModule)
  // tag::prometheus[]
  import(openTelemetryModule)
  import(prometheusTelemetryModule)  // registers its reader, and provides the scrape
  import(ktorTelemetryModule)        // KtorTelemetry, which serves it; no traces of the scrapes

  bindSingleton { instance<ConfigBinder>().config<StatusServerConfig>("server") }
  bindAppService { singleton { new(::StatusServer) } }                   // serves the scrape at /metrics
  import(ktorServerCioEngineModule)  // or on a port of its own, telemetry.ktor.hostPort, on this engine
  // end::prometheus[]
}

fun sigNozDI() = DI {
  import(bootableModule)
  import(hopliteConfigModule)
  // tag::signoz[]
  import(openTelemetryModule)
  import(sigNozTelemetryModule)  // from telemetry.signoz
  // end::signoz[]
}

fun gcpDI() = DI {
  import(bootableModule)
  import(hopliteConfigModule)
  // tag::gcp[]
  import(openTelemetryModule)
  import(gcpTelemetryModule)  // from telemetry.gcp
  // end::gcp[]
}

fun micrometerBridgeDI() = DI {
  import(bootableModule)
  import(hopliteConfigModule)
  // tag::micrometer-bridge[]
  import(openTelemetryModule)
  import(micrometerBridgeModule)  // a MeterRegistry binding, over the application's OpenTelemetry

  // e.g. a library's Micrometer binder: the executor's metrics, exported with the rest
  bindSingleton<ExecutorService> { ExecutorServiceMetrics.monitor(instance(), Executors.newFixedThreadPool(4), "work") }
  // end::micrometer-bridge[]
}
