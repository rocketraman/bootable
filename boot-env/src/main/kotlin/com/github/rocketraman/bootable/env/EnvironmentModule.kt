package com.github.rocketraman.bootable.env

import org.kodein.di.DI
import org.kodein.di.instance

val environmentModule by lazy {
  DI.Module("environmentModule") {
    onReady {
      // EnvironmentConfig is provided by the platform-specific module
      environment = Env.fromString(instance<EnvironmentConfig>().environment)
    }
  }
}
