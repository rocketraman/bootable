package com.github.rocketraman.bootable.boot

import com.github.rocketraman.bootable.common.Ignore

/**
 * Like [AppService], with the additional capability that implementations receive a `die` callback on the
 * `start` call. The callback can be used to indicate to the system that a service has died in such a way that
 * the entire system must be shutdown.
 */
interface AdvancedAppService: AppService {
  @Deprecated("AdvancedAppService requires override of start(die: () -> Unit)",
    level = DeprecationLevel.HIDDEN)
  override fun start() { Ignore }

  fun start(die: () -> Unit) { Ignore }
}
