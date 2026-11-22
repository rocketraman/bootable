package snippets.telemetry

import com.github.rocketraman.bootable.telemetry.otel.Sample
import com.github.rocketraman.bootable.telemetry.otel.withRootSpan
import com.github.rocketraman.bootable.telemetry.otel.withSpan
import io.opentelemetry.api.OpenTelemetry
import kotlinx.coroutines.delay

class NoteIndex(openTelemetry: OpenTelemetry) {
  private val tracer = openTelemetry.getTracer("notes")

  // tag::span[]
  suspend fun rebuild(ids: List<String>) = tracer.withSpan("notes.rebuild") { span ->
    span.setAttribute("notes.count", ids.size.toLong())
    ids.forEach { index(it) }
  }
  // end::span[]

  // tag::root-span[]
  suspend fun compact() = tracer.withRootSpan("notes.compact", sample = Sample.Always) {
    rebuild(emptyList())  // its span is a child of notes.compact
  }
  // end::root-span[]

  private suspend fun index(id: String) = delay(1)
}
