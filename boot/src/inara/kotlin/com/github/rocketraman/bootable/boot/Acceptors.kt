package com.github.rocketraman.bootable.boot

/**
 * Accepts the [AppService]s the application runs. Inara has no multibindings, so a module's builder takes one and adds
 * its own services to it. `BootableModule` is one.
 */
interface AppServiceAcceptor {
  /** Adds [provider]'s service. It is called when the services are first read, not now. */
  fun addAppService(provider: () -> AppService)
}

/** Accepts [LifecycleController]s, as [AppServiceAcceptor] does services. `BootableModule` is one. */
interface LifecycleControllerAcceptor {
  /** Adds [provider]'s controller. It is called when the controllers are first read, not now. */
  fun addLifecycleController(provider: () -> LifecycleController)
}
