package com.github.rocketraman.bootable.test

import de.infix.testBalloon.framework.core.testSuite
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.message.StringMapMessage

val LogCaptureTest by testSuite {
  test("captures the events logged meanwhile, at Bootable's level") {
    val log = LogManager.getLogger("capture.test")
    log.info("before")
    val logs = captureLogs("capture.test") {
      log.info("stored {}", "greeting")
      log.warn("slow", IllegalStateException("boom"))
      // below Bootable's default level, INFO
      log.debug("hidden")
    }
    log.info("after")

    assert(logs.messages() == listOf("stored greeting", "slow"))
    assert(logs.messages(Level.WARN) == listOf("slow"))
    assert(logs.single(Level.WARN).thrown?.message == "boom")
    assert(logs.events.all { it.loggerName == "capture.test" })
  }

  test("captures a logger's descendants") {
    val logs = captureLogs("capture") {
      LogManager.getLogger("capture.child").info("from a child")
      LogManager.getLogger("elsewhere").info("from elsewhere")
    }
    assert(logs.messages() == listOf("from a child"))
  }

  test("has the structured fields of a map message") {
    val logs = captureLogs("capture.fields") {
      LogManager.getLogger("capture.fields").info(StringMapMessage().with("message", "access").with("http.status", "200"))
    }
    val event = logs.single(Level.INFO) { it.fields["message"] == "access" }
    assert(event.fields["http.status"] == "200")
  }

  test("lowers the level while capturing, and restores it after") {
    val log = LogManager.getLogger("capture.debug")
    val logs = captureLogs("capture.debug", level = Level.DEBUG) {
      log.debug("visible")
    }
    assert(logs.messages(Level.DEBUG) == listOf("visible"))
    assert(!log.isDebugEnabled)
  }

  test("a single event that doesn't match says what was captured") {
    val logs = captureLogs("capture.single") {
      LogManager.getLogger("capture.single").info("one")
      LogManager.getLogger("capture.single").info("two")
    }
    val failure = runCatching { logs.single(Level.INFO) }.exceptionOrNull()
    assert(failure is AssertionError && "2 did" in failure.message!! && "INFO capture.single: two" in failure.message!!)
  }
}
