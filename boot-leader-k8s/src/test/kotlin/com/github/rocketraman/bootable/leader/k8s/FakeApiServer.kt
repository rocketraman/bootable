package com.github.rocketraman.bootable.leader.k8s

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * A Kubernetes API server that serves only Leases, in memory, as the real one does for leader election: it refuses an
 * update at a stale `resourceVersion`, or a create of a Lease that exists, with a 409, and a request without one of
 * [tokens] with a 401.
 */
class FakeApiServer(private val tokens: Set<String>) : AutoCloseable {
  private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
  // a thread per request, so a stalled request holds up no other
  private val executor = Executors.newCachedThreadPool()
  private val leases = ConcurrentHashMap<String, JsonObject>()
  private val versions = AtomicLong()

  /** Tokens whose requests fail with a 503, as if the instance had lost its connection to the API server. */
  val unavailableTo: MutableSet<String> = ConcurrentHashMap.newKeySet()

  /** Tokens whose requests hang, unanswered, while they're in this set, as if the network had stalled. */
  val stalledFor: MutableSet<String> = ConcurrentHashMap.newKeySet()

  /** Tokens whose requests are refused with a 403, as if the service account lacked the permission. */
  val forbidden: MutableSet<String> = ConcurrentHashMap.newKeySet()

  /** Each request: its method, path, and token. */
  val requests: MutableList<Triple<String, String, String?>> = CopyOnWriteArrayList()

  val url: String get() = "http://127.0.0.1:${server.address.port}"

  init {
    server.createContext("/apis/coordination.k8s.io/v1/namespaces/") { exchange ->
      try {
        handle(exchange)
      } catch (e: Exception) {
        exchange.respond(500, status(500, e.toString()))
      }
    }
    server.executor = executor
    server.start()
  }

  /** The Lease [name] in [namespace], as the server holds it. */
  fun lease(namespace: String, name: String): JsonObject? = leases["$namespace/$name"]

  /** Puts a Lease, as if another instance had created it. */
  fun putLease(namespace: String, name: String, spec: JsonObject) {
    leases["$namespace/$name"] = buildJsonObject {
      put("apiVersion", "coordination.k8s.io/v1")
      put("kind", "Lease")
      put("metadata", metadata(namespace, name))
      put("spec", spec)
    }
  }

  private fun metadata(namespace: String, name: String) = buildJsonObject {
    put("name", name)
    put("namespace", namespace)
    put("uid", "uid-$name")
    put("resourceVersion", versions.incrementAndGet().toString())
  }

  private fun handle(exchange: HttpExchange) {
    val token = exchange.requestHeaders.getFirst("Authorization")?.removePrefix("Bearer ")
    val path = exchange.requestURI.path
    requests += Triple(exchange.requestMethod, path, token)
    when {
      token !in tokens -> return exchange.respond(401, status(401, "Unauthorized"))
      token in forbidden -> return exchange.respond(403, status(403, "leases is forbidden"))
      token in unavailableTo -> return exchange.respond(503, status(503, "unavailable"))
    }
    while (token in stalledFor) Thread.sleep(10)
    // /apis/coordination.k8s.io/v1/namespaces/{namespace}/leases[/{name}]
    val parts = path.removePrefix("/apis/coordination.k8s.io/v1/namespaces/").split('/')
    val namespace = parts[0]
    val name = parts.getOrNull(2)
    val body = exchange.requestBody.readBytes().decodeToString()
    synchronized(this) {
      when (exchange.requestMethod) {
        "GET" -> leases["$namespace/$name"]?.let { exchange.respond(200, it) }
          ?: exchange.respond(404, status(404, "leases \"$name\" not found"))
        "POST" -> {
          val lease = json.parseToJsonElement(body).jsonObject
          val leaseName = lease["metadata"]!!.jsonObject["name"]!!.jsonPrimitive.content
          if (leases.containsKey("$namespace/$leaseName")) {
            exchange.respond(409, status(409, "leases \"$leaseName\" already exists"))
          } else {
            val created = JsonObject(lease + ("metadata" to metadata(namespace, leaseName)))
            leases["$namespace/$leaseName"] = created
            exchange.respond(201, created)
          }
        }
        "PUT" -> {
          val current = leases["$namespace/$name"]
            ?: return exchange.respond(404, status(404, "leases \"$name\" not found"))
          val lease = json.parseToJsonElement(body).jsonObject
          val version = lease["metadata"]!!.jsonObject["resourceVersion"]?.jsonPrimitive?.content
          if (version != current["metadata"]!!.jsonObject["resourceVersion"]!!.jsonPrimitive.content) {
            exchange.respond(409, status(409, "the object has been modified"))
          } else {
            val metadata = JsonObject(
              lease["metadata"]!!.jsonObject + ("resourceVersion" to JsonPrimitive(versions.incrementAndGet().toString()))
            )
            val updated = JsonObject(lease + ("metadata" to metadata))
            leases["$namespace/$name"] = updated
            exchange.respond(200, updated)
          }
        }
        else -> exchange.respond(405, status(405, "method not allowed"))
      }
    }
  }

  private fun status(code: Int, message: String) = buildJsonObject {
    put("kind", "Status")
    put("status", "Failure")
    put("message", message)
    put("code", code)
  }

  private fun HttpExchange.respond(code: Int, body: JsonObject) {
    val bytes = body.toString().encodeToByteArray()
    responseHeaders.add("Content-Type", "application/json")
    sendResponseHeaders(code, bytes.size.toLong())
    responseBody.use { it.write(bytes) }
  }

  override fun close() {
    stalledFor.clear()
    server.stop(0)
    executor.shutdownNow()
  }

  companion object {
    private val json = Json
  }
}
