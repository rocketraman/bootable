package com.github.rocketraman.bootable.boot

import com.github.rocketraman.bootable.common.Ignore
import kotlinx.coroutines.CoroutineScope

/**
 * Like [AppService], for a service that runs work in the background: its start gets a [CoroutineScope] to launch that
 * work in.
 *
 * The scope is the service's own, a child of the application's, on `Dispatchers.Default`. It lives until the service
 * has stopped: after [shutdown] returns, Bootable cancels it and waits for its coroutines, so the service needn't
 * track, cancel or join them itself. A service that wants its work to finish gracefully, rather than be cancelled,
 * waits for it in [shutdown].
 *
 * If a coroutine in the scope fails, the service has failed: its other coroutines are cancelled, and the application
 * stops, in order, with exit code 1. A service that notices outside its scope, such as in a request handler, that it
 * can't go on, fails its scope with `scope.launch { throw e }`.
 */
interface AdvancedAppService: AppService {
  @Deprecated("AdvancedAppService requires override of start(scope: CoroutineScope)",
    level = DeprecationLevel.HIDDEN)
  override suspend fun start() { Ignore }

  suspend fun start(scope: CoroutineScope) { Ignore }
}
