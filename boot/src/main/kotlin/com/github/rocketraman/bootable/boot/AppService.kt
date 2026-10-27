package com.github.rocketraman.bootable.boot

import com.github.rocketraman.bootable.common.Ignore

/**
 * An application service, with a lifecycle.
 *
 * Passing implementations of AppService to [Bootable] (directly, or via a DI multibinding such as Kodein's
 * `bindAppService`) registers them with the boot system, which starts them up and shuts them down in the order of
 * their [ordering].
 */
interface AppService {
  /**
   * The service name, which defaults to the simple java class name.
   */
  fun name(): String = javaClass.simpleName

  fun start() { Ignore }

  fun shutdown() { Ignore }

  /**
   * Where this service starts relative to the others: its [Phase], and the [Milestone]s it provides and waits
   * for. Services start phase by phase, and stop in the reverse order. The default is the [StartOrder]'s default
   * phase, [DefaultPhase.Application] unless the app changes it, with no milestones, which suits most of an
   * application's own services.
   *
   * Services that their orderings leave unordered may start in any order (see [StartOrder.Tiebreaker]): if the
   * order between them matters, declare a milestone.
   */
  fun ordering(): Ordering = Ordering()
}
