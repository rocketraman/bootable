package com.github.rocketraman.bootable.env

import com.github.rocketraman.bootable.config.bindConfig
import org.kodein.di.DI
import org.kodein.di.instance

val environmentConfigModule by lazy {
  DI.Module("environmentConfigModule") {
    bindConfig<EnvironmentConfig>("env")
  }
}
