package snippets.http

import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.BootScope
import com.github.rocketraman.bootable.boot.BootableGraph
import com.github.rocketraman.bootable.client.http.ktor.CioClientEngine
import com.github.rocketraman.bootable.client.http.ktor.HttpClientTelemetryCustomizer
import com.github.rocketraman.bootable.client.http.ktor.KtorClientEngine
import com.github.rocketraman.bootable.config.ConfigBinderProvider
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.server.http.ktor.KtorHealth
import com.github.rocketraman.bootable.server.http.ktor.KtorTelemetry
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import io.ktor.client.request.*
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor

/** A traced application, a graph of its own: its server installs the telemetry. */
sealed interface TracedHttpScope

@ContributesTo(TracedHttpScope::class)
interface TracedServerBindings : ConfigBinderProvider {
  @Provides fun serverConfig(): ServerConfig = config("server")

  @Provides fun ktorClientEngine(): KtorClientEngine = CioClientEngine()

  // tag::server[]
  // KtorTelemetry is contributed by boot-server-http-ktor-otel-metro
  @Provides @IntoSet
  fun server(config: ServerConfig, health: KtorHealth, telemetry: KtorTelemetry): AppService =
    TracedGreetingServer(config, health, telemetry)

  // the scrape's and the probes' own listeners, if their hostPort is set, are contributed too, on the engine that
  // boot-server-http-ktor-cio-metro contributes
  // end::server[]

  // tag::client[]
  // boot-client-http-ktor-otel-metro adds the instrumentation to every client; this only changes its span names
  @Provides @IntoSet
  fun clientSpanNames(): HttpClientTelemetryCustomizer = HttpClientTelemetryCustomizer { builder ->
    // name each span for the host it calls, such as GET api.github.com
    builder.spanNameExtractor { SpanNameExtractor<HttpRequestData> { "${it.method.value} ${it.url.host}" } }
  }
  // end::client[]
}

@DependencyGraph(TracedHttpScope::class, additionalScopes = [BootScope::class])
interface TracedHttpGraph : BootableGraph
