package com.github.rocketraman.bootable.boot

import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config

/**
 * How Bootable orders services, from the `boot.service.order` config section. Every setting has a default, so the
 * section is optional:
 *
 * ```
 * boot.service.order {
 *   tiebreaker {
 *     type = Shuffled  # or ByName, the default
 *     seed = 42        # optional: replays a logged shuffle
 *   }
 * }
 * ```
 *
 * or, as environment variables, `BOOT_SERVICE_ORDER_TIEBREAKER_TYPE=Shuffled` and
 * `BOOT_SERVICE_ORDER_TIEBREAKER_SEED=42`. The `type` is the [StartOrder.Tiebreaker] subtype's name, and the other
 * keys are its properties.
 *
 * Set them like any other config, e.g. `type = Shuffled` in `application-local.conf` or test config, where a
 * surprise is cheap, and the default in production, so it starts in the same order on every run.
 */
data class ServiceOrderConfig(
  /** How services the start order leaves tied are started. */
  val tiebreaker: StartOrder.Tiebreaker = StartOrder.Tiebreaker.ByName,
)

/** Decodes [ServiceOrderConfig] from the `boot.service.order` config section using the given [ConfigBinder]. */
fun serviceOrderConfig(binder: ConfigBinder): ServiceOrderConfig = binder.config("boot.service.order")
