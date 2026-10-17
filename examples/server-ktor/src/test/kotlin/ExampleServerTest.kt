import com.github.rocketraman.bootable.boot.AppStartStopLifecycleController
import com.github.rocketraman.bootable.boot.StopSignalHandlerLifecycleController
import com.github.rocketraman.bootable.boot.defaultLifecycleControllers
import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.env.Env
import com.github.rocketraman.bootable.env.EnvironmentConfig
import com.github.rocketraman.bootable.env.environment
import com.github.rocketraman.bootable.env.environmentInitializer
import de.infix.testBalloon.framework.core.testSuite
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.reflect.KClass

/**
 * A fake [ConfigBinder] — the whole point of the vanilla approach is that tests need no DI framework, no graph,
 * and no override machinery. Construct what you want to test with whatever config you want.
 */
private class TestEnvironmentConfig(override val environment: String) : EnvironmentConfig

private class FakeConfigBinder(private val env: String = "prod") : ConfigBinder {
  @Suppress("UNCHECKED_CAST")
  override fun <T : Any> configOf(prefix: String, type: KClass<T>): T = when (type) {
    EnvironmentConfig::class -> TestEnvironmentConfig(env) as T
    else -> error("no test config for $type at prefix '$prefix'")
  }
}

private val testServerConfig = ServerConfig(hostPort = "127.0.0.1:0", fakeSecret = "test-secret")

val ExampleServerTest by testSuite {
  test("constructs directly with no DI involved") {
    val server = ExampleServer(testServerConfig, HelloConfig(response = "hi"))
    assert(server.name() == "ExampleServer")
  }

  testSuite("serves") {
    test("hello") {
      testApplication {
        val server = ExampleServer(testServerConfig, HelloConfig(response = "hello from the test"))
        application { with(server) { module() } }

        val response = client.get("/hello")

        assert(response.status == HttpStatusCode.OK)
        assert(response.bodyAsText() == "hello from the test")
      }
    }

    test("the index page") {
      testApplication {
        val server = ExampleServer(testServerConfig, HelloConfig(response = "hi"))
        application { with(server) { module() } }

        assert(client.get("/").bodyAsText().contains("Hello from Ktor"))
      }
    }
  }
}

/**
 * boot-env, tested the same way: construct the initializer with a fake binder and run its lifecycle hook.
 */
val EnvironmentTest by testSuite {
  test("environmentInitializer sets the global environment from config") {
    environmentInitializer(FakeConfigBinder(env = "prod")).appInit()
    assert(environment == Env.Prod)
  }
}

val LifecycleTest by testSuite {
  test("defaultLifecycleControllers are the two built-ins") {
    val controllers = defaultLifecycleControllers()
    assert(controllers.any { it is AppStartStopLifecycleController })
    assert(controllers.any { it is StopSignalHandlerLifecycleController })
  }
}
