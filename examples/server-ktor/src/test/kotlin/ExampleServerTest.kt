import com.github.rocketraman.bootable.boot.AppStartStopLifecycleController
import com.github.rocketraman.bootable.boot.StopSignalHandlerLifecycleController
import com.github.rocketraman.bootable.boot.defaultLifecycleControllers
import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.env.Env
import com.github.rocketraman.bootable.env.EnvironmentConfig
import com.github.rocketraman.bootable.env.environment
import com.github.rocketraman.bootable.env.environmentInitializer
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

class ExampleServerTest {
  @Test
  fun `constructs directly with no DI involved`() {
    val server = ExampleServer(testServerConfig, HelloConfig(response = "hi"))
    assertEquals("ExampleServer", server.name())
  }

  @Test
  fun `serves hello`() = testApplication {
    val server = ExampleServer(testServerConfig, HelloConfig(response = "hello from the test"))
    application { with(server) { module() } }

    val response = client.get("/hello")

    assertEquals(HttpStatusCode.OK, response.status)
    assertEquals("hello from the test", response.bodyAsText())
  }

  @Test
  fun `serves the index page`() = testApplication {
    val server = ExampleServer(testServerConfig, HelloConfig(response = "hi"))
    application { with(server) { module() } }

    assertTrue(client.get("/").bodyAsText().contains("Hello from Ktor"))
  }
}

/**
 * boot-env, tested the same way: construct the initializer with a fake binder and run its lifecycle hook.
 */
class EnvironmentTest {
  @Test
  fun `environmentInitializer sets the global environment from config`() {
    environmentInitializer(FakeConfigBinder(env = "prod")).appInit()
    assertEquals(Env.Prod, environment)
  }
}

class LifecycleTest {
  @Test
  fun `defaultLifecycleControllers are the two built-ins`() {
    val controllers = defaultLifecycleControllers()
    assertTrue(controllers.any { it is AppStartStopLifecycleController })
    assertTrue(controllers.any { it is StopSignalHandlerLifecycleController })
  }
}
