import com.github.rocketraman.bootable.boot.StartOrderException
import com.github.rocketraman.bootable.boot.startPlan
import com.github.rocketraman.bootable.config.hopliteConfigBinder
import de.infix.testBalloon.framework.core.testSuite
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.readText

/** The start plan is computed from the services' orderings; nothing is started, so no server listens. */
val AppTest by testSuite {
  test("announces readiness after the server starts, and withdraws it before the server stops") {
    val plan = startPlan(appServices(hopliteConfigBinder(report = false)), AppStartOrder).map { it::class }

    assert(plan == listOf(HelloServer::class, ReadyFileService::class))
  }

  test("fails the boot without the custom start order, which has the Announce phase") {
    val problems = try {
      startPlan(appServices(hopliteConfigBinder(report = false)))
      emptyList()
    } catch (e: StartOrderException) {
      e.problems
    }

    val problem = problems.single()
    assert(problem.startsWith("ReadyFileService is in phase Announce, which is not one of the start order's phases"))
    assert(problem.endsWith("Pass Bootable a StartOrder that includes Announce."))
  }

  test("the ready file exists only between start and shutdown") {
    val path = createTempDirectory("start-order").resolve("nested/ready")
    val service = ReadyFileService(ReadyFileConfig(path.toString()))

    assert(!path.exists())
    service.start()
    assert(path.readText() == "ready\n")
    service.shutdown()
    assert(!path.exists())
  }
}
