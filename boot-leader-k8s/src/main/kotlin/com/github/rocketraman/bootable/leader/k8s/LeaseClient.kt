package com.github.rocketraman.bootable.leader.k8s

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.time.Duration

/** A request to the Kubernetes API server that it answered with an error [status]. */
class KubernetesApiException(val status: Int, message: String) : RuntimeException(message)

/** A `coordination.k8s.io/v1` Lease. Its metadata is kept as it was read, so an update sends it back unchanged. */
@Serializable
internal data class Lease(
  val apiVersion: String = "coordination.k8s.io/v1",
  val kind: String = "Lease",
  val metadata: JsonObject,
  val spec: LeaseSpec = LeaseSpec(),
) {
  val resourceVersion: String? get() = (metadata["resourceVersion"] as? JsonPrimitive)?.contentOrNull
}

@Serializable
internal data class LeaseSpec(
  val holderIdentity: String? = null,
  val leaseDurationSeconds: Int? = null,
  /** A `MicroTime`: RFC 3339, with microseconds. See [formatMicroTime]. */
  val acquireTime: String? = null,
  val renewTime: String? = null,
  val leaseTransitions: Int? = null,
)

private val microTime = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX").withZone(ZoneOffset.UTC)

/** Formats [instant] as a Kubernetes `MicroTime`, which the API server parses with exactly six fractional digits. */
internal fun formatMicroTime(instant: kotlin.time.Instant): String =
  microTime.format(Instant.ofEpochSecond(instant.epochSeconds, instant.nanosecondsOfSecond.toLong()))

/**
 * Reads, creates, and updates one Lease, [name] in [namespace], through the API server's REST API, with [httpClient]:
 * all that leader election needs, without a Kubernetes client library. Each request authenticates with [token], read
 * for each request, and may take [requestTimeout].
 */
internal class LeaseClient(
  apiServer: String,
  private val namespace: String,
  private val name: String,
  private val token: () -> String?,
  private val httpClient: HttpClient,
  private val requestTimeout: Duration,
) {
  private val leases = "${apiServer.trimEnd('/')}/apis/coordination.k8s.io/v1/namespaces/$namespace/leases"
  private val lease = "$leases/$name"

  /** The Lease, or null if there is none. */
  suspend fun get(): Lease? {
    val (status, body) = send { httpClient.get(lease) { authenticate() } }
    return when (status) {
      HTTP_NOT_FOUND -> null
      in HTTP_OK_RANGE -> json.decodeFromString(Lease.serializer(), body)
      else -> throw apiException("GET", lease, status, body)
    }
  }

  /** Creates the Lease with [spec], or throws a [KubernetesApiException] with a 409 status if it exists. */
  suspend fun create(spec: LeaseSpec): Lease {
    val metadata = JsonObject(mapOf("name" to JsonPrimitive(name), "namespace" to JsonPrimitive(namespace)))
    val body = json.encodeToString(Lease.serializer(), Lease(metadata = metadata, spec = spec))
    return write("POST", leases) { httpClient.post(leases) { authenticate(); jsonBody(body) } }
  }

  /**
   * Replaces the Lease, which must be at its [Lease.resourceVersion], or the API server refuses it with a 409 status:
   * another instance updated it since it was read.
   */
  suspend fun update(lease: Lease): Lease {
    val body = json.encodeToString(Lease.serializer(), lease)
    return write("PUT", this.lease) { httpClient.put(this.lease) { authenticate(); jsonBody(body) } }
  }

  private suspend fun write(method: String, url: String, request: suspend () -> HttpResponse): Lease {
    val (status, body) = send(request)
    if (status !in HTTP_OK_RANGE) throw apiException(method, url, status, body)
    return json.decodeFromString(Lease.serializer(), body)
  }

  private fun HttpRequestBuilder.authenticate() {
    accept(ContentType.Application.Json)
    token()?.let { bearerAuth(it) }
  }

  private fun HttpRequestBuilder.jsonBody(body: String) {
    contentType(ContentType.Application.Json)
    setBody(body)
  }

  // the timeout is the election's own, whichever engine the client has, and whether or not it sets timeouts
  private suspend fun send(request: suspend () -> HttpResponse): Pair<Int, String> = withTimeout(requestTimeout) {
    val response = request()
    response.status.value to response.bodyAsText()
  }

  private fun apiException(method: String, url: String, status: Int, body: String): KubernetesApiException {
    // a Kubernetes Status object's message says why, e.g. which permission is missing
    val reason = runCatching { (json.parseToJsonElement(body) as JsonObject)["message"] as JsonPrimitive }
      .getOrNull()?.contentOrNull ?: body.take(MAX_ERROR_BODY)
    return KubernetesApiException(status, "$method $url: $status $reason")
  }

  companion object {
    private const val HTTP_NOT_FOUND = 404
    private val HTTP_OK_RANGE = 200..299
    private const val MAX_ERROR_BODY = 500

    private val json = Json {
      ignoreUnknownKeys = true
      encodeDefaults = true
      explicitNulls = false
    }
  }
}

/**
 * The service account's token at [path], read for each request, since Kubernetes rotates it in place, or null if
 * there is none.
 */
internal fun tokenFile(path: Path): () -> String? = { if (path.exists()) path.readText().trim().ifEmpty { null } else null }
