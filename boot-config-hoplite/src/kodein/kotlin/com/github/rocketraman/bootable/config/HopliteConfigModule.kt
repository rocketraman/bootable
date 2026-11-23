package com.github.rocketraman.bootable.config

import org.kodein.di.DI
import org.kodein.di.bindSingleton

/** A Kodein module binding the default Hoplite [ConfigBinder]. */
@Deprecated("Kodein is deprecated: use the -inara flavor, or the -koin flavor for a DSL like Kodein's")
val hopliteConfigModule by DI.Module {
  bindSingleton<ConfigBinder> { hopliteConfigBinder() }
}

@Deprecated("Renamed to hopliteConfigModule", ReplaceWith("hopliteConfigModule"))
val configModule: DI.Module get() = hopliteConfigModule
