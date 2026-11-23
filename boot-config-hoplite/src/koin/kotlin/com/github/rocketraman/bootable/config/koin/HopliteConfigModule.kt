package com.github.rocketraman.bootable.config.koin

import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.hopliteConfigBinder
import org.koin.core.module.Module
import org.koin.dsl.module

/** A Koin module defining the default Hoplite [ConfigBinder]. */
fun hopliteConfigModule(): Module = module {
  single<ConfigBinder> { hopliteConfigBinder() }
}
