package snippets.database

import com.github.rocketraman.bootable.db.jdbc.JdbcTelemetryConfigurer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides

// The -metro tracing modules contribute their wrapper and customizer on their own; a JdbcTelemetryConfigurer is only
// needed to change the instrumentation's defaults.
@ContributesTo(AppScope::class)
interface DatabaseTracingBindings {
  // tag::tracing[]
  @Provides
  fun jdbcTelemetryConfigurer(): JdbcTelemetryConfigurer =
    JdbcTelemetryConfigurer { setDataSourceInstrumenterEnabled(true) }  // a span for each connection taken, too
  // end::tracing[]
}
