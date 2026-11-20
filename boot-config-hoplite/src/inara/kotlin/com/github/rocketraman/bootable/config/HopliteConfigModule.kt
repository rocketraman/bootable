@file:JvmName("HopliteConfigModuleInara")

package com.github.rocketraman.bootable.config

import org.kodein.di.inara.Binding

/**
 * A [ConfigModule] that decodes config with the default [hopliteConfigBinder]. To pass Hoplite options, build the
 * [ConfigModule] yourself:
 *
 * ```
 * ConfigModule(configBinder = Binding.single { hopliteConfigBinder(report = false) })
 * ```
 */
fun HopliteConfigModule(): ConfigModule = ConfigModule(configBinder = Binding.single { hopliteConfigBinder() })
