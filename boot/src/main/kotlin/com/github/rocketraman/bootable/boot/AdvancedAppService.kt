package com.github.rocketraman.bootable.boot

import com.github.rocketraman.bootable.common.Ignore

interface AdvancedAppService: AppService {
  @Deprecated("AdvancedAppService requires override of start(die: () -> Unit)",
    level = DeprecationLevel.HIDDEN)
  override fun start() { Ignore }

  fun start(die: () -> Unit) { Ignore }
}
