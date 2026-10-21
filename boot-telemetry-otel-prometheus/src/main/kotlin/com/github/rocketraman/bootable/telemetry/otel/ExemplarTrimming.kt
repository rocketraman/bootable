package com.github.rocketraman.bootable.telemetry.otel

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.metrics.data.Data
import io.opentelemetry.sdk.metrics.data.DoubleExemplarData
import io.opentelemetry.sdk.metrics.data.DoublePointData
import io.opentelemetry.sdk.metrics.data.ExponentialHistogramData
import io.opentelemetry.sdk.metrics.data.ExponentialHistogramPointData
import io.opentelemetry.sdk.metrics.data.GaugeData
import io.opentelemetry.sdk.metrics.data.HistogramData
import io.opentelemetry.sdk.metrics.data.HistogramPointData
import io.opentelemetry.sdk.metrics.data.LongExemplarData
import io.opentelemetry.sdk.metrics.data.LongPointData
import io.opentelemetry.sdk.metrics.data.MetricData
import io.opentelemetry.sdk.metrics.data.MetricDataType
import io.opentelemetry.sdk.metrics.data.PointData
import io.opentelemetry.sdk.metrics.data.SumData
import io.opentelemetry.sdk.metrics.export.CollectionRegistration
import io.opentelemetry.sdk.metrics.export.MetricReader

/**
 * Registers [delegate] with the SDK, but has it collect metrics whose exemplars carry no filtered attributes: the
 * attributes of a measurement that the metric itself doesn't keep, such as a request's `url.path` or
 * `user_agent.original`.
 *
 * OpenMetrics limits an exemplar's labels to 128 characters, and the trace and span ids take 63 of them, so the
 * request duration histograms' filtered attributes routinely exceed it. The Prometheus exporter then drops them anyway,
 * and logs a warning each time. Dropping them before it sees them gives the same exemplars, linked to their traces by
 * the trace and span ids, without the warnings, which would also use up the exporter's rate-limited log, hiding its
 * other warnings.
 */
internal class ExemplarTrimmingReader(private val delegate: MetricReader) : MetricReader by delegate {
  override fun register(registration: CollectionRegistration) {
    delegate.register(object : CollectionRegistration {
      override fun collectAllMetrics(): Collection<MetricData> =
        registration.collectAllMetrics().map { withoutFilteredAttributes(it) }
    })
  }
}

/** [metric], with no filtered attributes on its exemplars. */
internal fun withoutFilteredAttributes(metric: MetricData): MetricData {
  val data: Data<*> = when (metric.type) {
    MetricDataType.LONG_GAUGE -> Gauge(metric.longGaugeData.points.map(::TrimmedLongPoint))
    MetricDataType.DOUBLE_GAUGE -> Gauge(metric.doubleGaugeData.points.map(::TrimmedDoublePoint))
    MetricDataType.LONG_SUM -> Sum(metric.longSumData, metric.longSumData.points.map(::TrimmedLongPoint))
    MetricDataType.DOUBLE_SUM -> Sum(metric.doubleSumData, metric.doubleSumData.points.map(::TrimmedDoublePoint))
    MetricDataType.HISTOGRAM -> Histogram(metric.histogramData)
    MetricDataType.EXPONENTIAL_HISTOGRAM -> ExponentialHistogram(metric.exponentialHistogramData)
    // summaries have no exemplars
    MetricDataType.SUMMARY, null -> return metric
  }
  return TrimmedMetric(metric, data)
}

// MetricData's typed getters, such as getHistogramData, cast getData, so they see the trimmed data
private class TrimmedMetric(private val metric: MetricData, private val data: Data<*>) : MetricData {
  override fun getResource() = metric.resource
  override fun getInstrumentationScopeInfo() = metric.instrumentationScopeInfo
  override fun getName(): String = metric.name
  override fun getDescription(): String = metric.description
  override fun getUnit(): String = metric.unit
  override fun getType(): MetricDataType = metric.type
  override fun getData(): Data<*> = data
}

private class Gauge<T : PointData>(private val points: Collection<T>) : GaugeData<T> {
  override fun getPoints() = points
}

private class Sum<T : PointData>(private val sum: SumData<*>, private val points: Collection<T>) : SumData<T> {
  override fun getPoints() = points
  override fun isMonotonic() = sum.isMonotonic
  override fun getAggregationTemporality() = sum.aggregationTemporality
}

private class Histogram(private val histogram: HistogramData) : HistogramData {
  private val points: Collection<HistogramPointData> = histogram.points.map(::TrimmedHistogramPoint)
  override fun getPoints() = points
  override fun getAggregationTemporality() = histogram.aggregationTemporality
}

private class ExponentialHistogram(private val histogram: ExponentialHistogramData) : ExponentialHistogramData {
  private val points: Collection<ExponentialHistogramPointData> =
    histogram.points.map(::TrimmedExponentialHistogramPoint)
  override fun getPoints() = points
  override fun getAggregationTemporality() = histogram.aggregationTemporality
}

private class TrimmedLongPoint(point: LongPointData) : LongPointData by point {
  private val exemplars: List<LongExemplarData> = point.exemplars.map(::TrimmedLongExemplar)
  override fun getExemplars() = exemplars
}

private class TrimmedDoublePoint(point: DoublePointData) : DoublePointData by point {
  private val exemplars: List<DoubleExemplarData> = point.exemplars.map(::TrimmedDoubleExemplar)
  override fun getExemplars() = exemplars
}

private class TrimmedHistogramPoint(point: HistogramPointData) : HistogramPointData by point {
  private val exemplars: List<DoubleExemplarData> = point.exemplars.map(::TrimmedDoubleExemplar)
  override fun getExemplars() = exemplars
}

private class TrimmedExponentialHistogramPoint(point: ExponentialHistogramPointData) :
  ExponentialHistogramPointData by point {
  private val exemplars: List<DoubleExemplarData> = point.exemplars.map(::TrimmedDoubleExemplar)
  override fun getExemplars() = exemplars
}

private class TrimmedLongExemplar(exemplar: LongExemplarData) : LongExemplarData by exemplar {
  override fun getFilteredAttributes(): Attributes = Attributes.empty()
}

private class TrimmedDoubleExemplar(exemplar: DoubleExemplarData) : DoubleExemplarData by exemplar {
  override fun getFilteredAttributes(): Attributes = Attributes.empty()
}
