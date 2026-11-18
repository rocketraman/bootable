package com.github.rocketraman.bootable.scheduler.db

import de.infix.testBalloon.framework.core.testSuite
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlin.time.Duration.Companion.minutes

@Serializable
sealed class Unnamed {
  @Serializable
  data class Child(val x: Int) : Unnamed()
}

class NotSerializable(val x: Int)

val KotlinxSerializationTaskDataSerializerTest by testSuite {
  val serializer = KotlinxSerializationTaskDataSerializer()

  test("a data class round-trips, as JSON") {
    val bytes = serializer.serialize(Reminder("ada", "hi"))!!
    assert(bytes.decodeToString() == """{"user":"ada","text":"hi"}""")
    assert(serializer.deserialize(Reminder::class.java, bytes) == Reminder("ada", "hi"))
  }

  test("no data is null") {
    assert(serializer.serialize(null) == null)
    assert(serializer.deserialize(Void::class.java, null) == null)
  }

  test("a sealed subclass carries its discriminator, read back as the sealed type or the subclass") {
    val bytes = serializer.serialize(Notice.Email("a@example.com"))!!
    assert(bytes.decodeToString() == """{"type":"email","to":"a@example.com"}""")
    assert(serializer.deserialize(Notice::class.java, bytes) == Notice.Email("a@example.com"))
    assert(serializer.deserialize(Notice.Email::class.java, bytes) == Notice.Email("a@example.com"))
  }

  test("a sealed subclass without a @SerialName is refused") {
    assert(runCatching { serializer.serialize(Unnamed.Child(1)) }.exceptionOrNull() is SerializationException)
  }

  test("a class that isn't @Serializable is refused") {
    assert(runCatching { serializer.serialize(NotSerializable(1)) }.exceptionOrNull() is SerializationException)
  }

  test("unknown keys are ignored, so data from a newer version reads") {
    val bytes = """{"user":"ada","text":"hi","added":true}""".encodeToByteArray()
    assert(serializer.deserialize(Reminder::class.java, bytes) == Reminder("ada", "hi"))
  }

  test("a persistent schedule round-trips with its data") {
    val report = Report(SerializableSchedule.Cron("0 0 8 * * MON", "Europe/Paris"), "weekly")
    val bytes = serializer.serialize(report)!!
    assert(serializer.deserialize(Report::class.java, bytes) == report)
    assert(report.schedule.toString().contains("0 0 8 * * MON"))
    val daily = Report(SerializableSchedule.DailyAt(listOf("08:00", "17:30")), "daily")
    assert(serializer.deserialize(Report::class.java, serializer.serialize(daily)!!) == daily)
    val fixed = Report(SerializableSchedule.FixedDelay(5.minutes), "fixed")
    assert(serializer.deserialize(Report::class.java, serializer.serialize(fixed)!!) == fixed)
    assert(fixed.data === fixed)
  }
}
