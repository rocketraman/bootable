package com.github.rocketraman.bootable.boot

import de.infix.testBalloon.framework.core.testSuite
import kotlinx.serialization.json.Json

private fun json(text: String) = Json.parseToJsonElement(text)

val HealthSerializationTest by testSuite {
  testSuite("HealthReport") {
    test("encodes its status as UP or DOWN, and each check by name, with an unhealthy one's reason") {
      val report = HealthReport(
        healthy = false,
        state = LifecycleState.Running,
        checks = mapOf("jdbc" to HealthStatus.Healthy, "db-scheduler" to HealthStatus.Unhealthy("paused")),
      )
      assert(
        json(Json.encodeToString(report)) == json(
          """
          {
            "status": "DOWN",
            "state": "Running",
            "checks": {"jdbc": {"status": "UP"}, "db-scheduler": {"status": "DOWN", "reason": "paused"}}
          }
          """,
        ),
      )
    }

    test("always encodes its checks, even when there are none") {
      val report = HealthReport(healthy = true, state = LifecycleState.Starting)
      assert(Json.encodeToString(report) == """{"status":"UP","state":"Starting","checks":{}}""")
    }

    test("decodes back, such as in a client of the health routes") {
      val report = HealthReport(
        healthy = false,
        state = LifecycleState.Stopping,
        checks = mapOf("jdbc" to HealthStatus.Healthy, "queue" to HealthStatus.Unhealthy("backlog")),
      )
      assert(Json.decodeFromString<HealthReport>(Json.encodeToString(report)) == report)
    }
  }

  testSuite("HealthStatus") {
    test("encodes on its own") {
      assert(Json.encodeToString<HealthStatus>(HealthStatus.Healthy) == """{"status":"UP"}""")
      assert(Json.encodeToString<HealthStatus>(HealthStatus.Unhealthy("down")) == """{"status":"DOWN","reason":"down"}""")
    }
  }

  testSuite("BuildInfo") {
    test("encodes only its fields that are set") {
      assert(Json.encodeToString(BuildInfo()) == "{}")
      assert(Json.encodeToString(BuildInfo(revision = "3f2c9e1")) == """{"revision":"3f2c9e1"}""")
      val full = BuildInfo("1.4.2", "3f2c9e1", mapOf("branch" to "main"))
      assert(
        json(Json.encodeToString(full)) ==
          json("""{"version": "1.4.2", "revision": "3f2c9e1", "attributes": {"branch": "main"}}"""),
      )
    }

    test("decodes back") {
      val full = BuildInfo("1.4.2", "3f2c9e1", mapOf("branch" to "main"))
      assert(Json.decodeFromString<BuildInfo>(Json.encodeToString(full)) == full)
    }
  }
}
