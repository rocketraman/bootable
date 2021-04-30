package com.github.rocketraman.bootable.boot

import com.github.rocketraman.bootable.common.Ignore

interface AppService {
  /**
   * The service name, which defaults to the simple java class name.
   */
  fun name(): String = javaClass.simpleName

  fun start() { Ignore }

  fun shutdown() { Ignore }

  /**
   * The startup/shutdown priority of the service. Services with higher priority are started first, and shut down
   * last. Services default to a priority of 0, which means by default startup and shutdown order are undefined.
   * Generally services that offer external APIs should have a low priority so they are started last, and shut down
   * first, and services that offer internal capabilities (database connections, etc.) should have a high priority
   * so they are started first, and shut down last.
   */
  fun priority() = 0
}
