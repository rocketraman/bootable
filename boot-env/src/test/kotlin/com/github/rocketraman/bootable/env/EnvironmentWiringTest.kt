package com.github.rocketraman.bootable.env

import com.github.rocketraman.bootable.boot.BootableModule
import com.github.rocketraman.bootable.boot.LifecycleController
import com.github.rocketraman.bootable.boot.ReadinessConfig
import com.github.rocketraman.bootable.boot.bootableModule
import com.github.rocketraman.bootable.boot.koin.bootableModule as koinBootableModule
import com.github.rocketraman.bootable.boot.setOfLifecycleControllers
import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.ConfigModule
import com.github.rocketraman.bootable.env.koin.environmentModule as koinEnvironmentModule
import de.infix.testBalloon.framework.core.testSuite
import org.kodein.di.inara.Binding
import org.kodein.di.inara.DIModule
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.reflect.KClass

/** Config with an `env` section for the test environment, and the default readiness that `Health` reads. */
private object EnvConfigBinder : ConfigBinder {
  override fun <T : Any> configOf(prefix: String, type: KClass<T>): T = type.javaObjectType.cast(
    when {
      prefix == "env" && type == EnvironmentConfig::class -> object : EnvironmentConfig {
        override val environment = "test"
      }
      prefix == "health.readiness" && type == ReadinessConfig::class -> ReadinessConfig()
      else -> error("unexpected config $prefix")
    }
  )
}

/** An application's module, composed as an Inara application composes it. */
@DIModule(composed = true)
interface InaraTestModule : ConfigModule, BootableModule, EnvironmentModule

val EnvironmentWiringTest by testSuite {
  test("the environment config from the env section, and its initializer a lifecycle controller, in inara") {
    val module = InaraTestModule(
      configModule = ConfigModule(configBinder = Binding.value(EnvConfigBinder)),
      bootableModule = bootableModule(),
      environmentModule = environmentModule(),
    )
    assert(module.environmentConfig.environment == "test")
    assert(module.environmentInitializer in module.setOfLifecycleControllers())
    module.environmentInitializer.appInit()
    assert(environment == Env.Test)
  }

  test("the environment config from the env section, and its initializer a lifecycle controller, in koin") {
    val koin = koinApplication {
      modules(koinBootableModule(), koinEnvironmentModule(), module { single<ConfigBinder> { EnvConfigBinder } })
    }.koin
    try {
      assert(koin.get<EnvironmentConfig>().environment == "test")
      val initializer = koin.getAll<LifecycleController>().filterIsInstance<EnvironmentInitializer>().single()
      initializer.appInit()
      assert(environment == Env.Test)
    } finally {
      koin.close()
    }
  }
}
