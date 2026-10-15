package com.github.rocketraman.bootable.config

import org.kodein.di.DI
import org.kodein.di.bindSingleton

/** A Kodein module binding the default Hoplite [ConfigBinder]. */
val configModule = DI.Module("configModule") {
  bindSingleton<ConfigBinder> { hopliteConfigBinder() }
}
