package com.github.rocketraman.bootable.server.http.ktor

import com.github.rocketraman.bootable.boot.AdvancedAppService
import com.github.rocketraman.bootable.config.common.HostPort
import de.infix.testBalloon.framework.core.testSuite
import io.ktor.server.application.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

val NettyKtorServiceTest by testSuite {
  test("runs on Netty through the service lifecycle") {
    val service = object : NettyKtorService("hello", HostPort("127.0.0.1", 0)) {
      override fun Application.module() {
        routing { get("/hello") { call.respondText("hello") } }
      }
    }
    val appService: AdvancedAppService = service
    appService.start {}
    try {
      assert(service.server.engine is NettyApplicationEngine)
      val port = service.server.engine.resolvedConnectors().single().port
      val request = HttpRequest.newBuilder(URI("http://127.0.0.1:$port/hello")).build()
      val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
      assert(response.statusCode() == 200)
      assert(response.body() == "hello")
    } finally {
      appService.shutdown()
    }
  }
}
