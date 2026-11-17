package com.github.rocketraman.bootable.telemetry.otel

import com.google.auth.oauth2.GoogleCredentials
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.contrib.gcp.resource.GCPResourceProvider
import io.opentelemetry.sdk.resources.Resource
import java.net.URI

/**
 * Google Cloud settings. Every field is optional.
 *
 * Decoded from the `telemetry.gcp` config section:
 *
 * ```
 * telemetry {
 *   sdk {
 *     # Cloud Trace and Cloud Monitoring, without a collector
 *     otlp { endpoint = "https://telemetry.googleapis.com" }
 *   }
 *   gcp {
 *     projectId = my-project
 *   }
 * }
 * ```
 */
data class GcpTelemetryConfig(
  /**
   * Whether to describe the application with the resource attributes of the GCP platform it runs on, such as the
   * Cloud Run service and revision, or the GKE cluster, namespace, and pod. Off GCP, nothing is added.
   */
  val detectResource: Boolean = true,
  /**
   * Whether to authenticate OTLP export with Google's application default credentials. Unset, export is authenticated
   * when it goes to Google, i.e. to a `googleapis.com` endpoint, and not when it goes to a collector.
   */
  val authenticate: Boolean? = null,
  /**
   * The project that authenticated export is billed and quota-checked against, sent as `x-goog-user-project`.
   * Defaults to the credentials' own quota project, then to the project the application runs in.
   */
  val projectId: String? = null,
)

/**
 * Connects the application's telemetry to Google Cloud. Add it to `OpenTelemetryService`'s customizers:
 *
 * ```
 * val telemetry = OpenTelemetryService(telemetryConfig(binder), listOf(GcpTelemetry(binder.config("telemetry.gcp"))))
 * ```
 *
 * It adds the GCP platform's attributes to the resource, using OpenTelemetry's GCP resource detector, and authenticates
 * OTLP export to Google's OTLP endpoint, `https://telemetry.googleapis.com`, which writes traces to Cloud Trace and
 * metrics to Cloud Monitoring. The service account needs the roles to write both (`roles/cloudtrace.agent` and
 * `roles/monitoring.metricWriter`), and the project needs the Telemetry API enabled.
 *
 * [detect] and [credentials] are the platform detection and Google credentials to use; they default to
 * OpenTelemetry's GCP detector and the application default credentials. Credentials are loaded, and fail, when the
 * SDK is built, only if export is authenticated.
 */
class GcpTelemetry(
  private val config: GcpTelemetryConfig = GcpTelemetryConfig(),
  private val detect: () -> Attributes = { GCPResourceProvider().attributes },
  private val credentials: () -> GoogleCredentials = {
    GoogleCredentials.getApplicationDefault().createScoped(CLOUD_PLATFORM_SCOPE)
  },
) : OpenTelemetryCustomizer {

  private var detectedProjectId: String? = null
  private val loadedCredentials: GoogleCredentials by lazy(credentials)

  override fun customizeResource(resource: Resource): Resource {
    if (!config.detectResource) return resource
    val detected = detect()
    detectedProjectId = detected.get(CLOUD_ACCOUNT_ID)
    // the application's own resource wins over what is detected
    return Resource.create(detected).merge(resource)
  }

  override fun exportHeaders(endpoint: String): (() -> Map<String, String>)? =
    if (authenticates(endpoint)) authHeaders() else null

  /** Whether export to [endpoint] is authenticated with Google credentials. */
  fun authenticates(endpoint: String): Boolean =
    config.authenticate ?: URI(endpoint).host.orEmpty().let { it == GOOGLEAPIS || it.endsWith(".$GOOGLEAPIS") }

  /**
   * The headers for each export request: a fresh access token, refreshed by the credentials as it nears expiry, and
   * the quota project.
   */
  private fun authHeaders(): () -> Map<String, String> {
    // load now, so missing credentials stop the application at boot rather than failing every export
    val credentials = loadedCredentials
    return {
      val headers = credentials.requestMetadata.mapValues { (_, values) -> values.joinToString(",") }
      val projectId = config.projectId ?: credentials.quotaProjectId ?: detectedProjectId
      if (projectId == null || headers.keys.any { it.equals(USER_PROJECT_HEADER, ignoreCase = true) }) headers
      else headers + (USER_PROJECT_HEADER to projectId)
    }
  }

  override fun toString() = "GcpTelemetry($config)"

  private companion object {
    const val CLOUD_PLATFORM_SCOPE = "https://www.googleapis.com/auth/cloud-platform"
    const val GOOGLEAPIS = "googleapis.com"
    const val USER_PROJECT_HEADER = "x-goog-user-project"
    val CLOUD_ACCOUNT_ID: AttributeKey<String> = AttributeKey.stringKey("cloud.account.id")
  }
}
