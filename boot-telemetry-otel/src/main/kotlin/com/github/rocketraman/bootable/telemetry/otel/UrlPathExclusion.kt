package com.github.rocketraman.bootable.telemetry.otel

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.trace.data.LinkData
import io.opentelemetry.sdk.trace.samplers.Sampler
import io.opentelemetry.sdk.trace.samplers.SamplingResult

/**
 * An [OpenTelemetryCustomizer] that records no trace for a server request to any of [paths], such as a metrics scrape
 * or a health check, which would otherwise fill the traces with requests nobody looks at. A request is matched by its
 * `url.path`, exactly. Other requests are sampled as before.
 */
fun excludeUrlPathsFromTraces(paths: Set<String>): OpenTelemetryCustomizer = object : OpenTelemetryCustomizer {
  override fun customizeSampler(sampler: Sampler): Sampler = UrlPathExcludingSampler(sampler, paths)

  override fun toString() = "excludeUrlPathsFromTraces($paths)"
}

private val URL_PATH = AttributeKey.stringKey("url.path")

private class UrlPathExcludingSampler(private val delegate: Sampler, private val paths: Set<String>) : Sampler {
  override fun shouldSample(
    parentContext: Context,
    traceId: String,
    name: String,
    spanKind: SpanKind,
    attributes: Attributes,
    parentLinks: List<LinkData>,
  ): SamplingResult {
    // only a server span that starts a trace here: one continuing a caller's trace keeps the caller's decision
    val excluded = spanKind == SpanKind.SERVER &&
      !Span.fromContext(parentContext).spanContext.isValid &&
      attributes.get(URL_PATH) in paths
    return if (excluded) SamplingResult.drop()
    else delegate.shouldSample(parentContext, traceId, name, spanKind, attributes, parentLinks)
  }

  override fun getDescription() = "UrlPathExcluding{paths=$paths, delegate=${delegate.description}}"
}
