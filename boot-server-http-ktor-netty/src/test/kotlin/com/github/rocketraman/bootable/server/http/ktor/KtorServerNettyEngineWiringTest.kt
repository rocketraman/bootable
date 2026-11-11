package com.github.rocketraman.bootable.server.http.ktor

import com.github.rocketraman.bootable.boot.BootScope
import de.infix.testBalloon.framework.core.testSuite
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.createGraph
import io.ktor.server.netty.*
import org.kodein.di.DI
import org.kodein.di.direct
import org.kodein.di.instance

sealed interface KtorServerNettyEngineTestScope

@DependencyGraph(KtorServerNettyEngineTestScope::class, additionalScopes = [BootScope::class])
interface KtorServerNettyEngineGraph {
  val engine: KtorServerEngine
}

val KtorServerNettyEngineWiringTest by testSuite {
  test("ktorServerNettyEngineModule provides Netty as the KtorServerEngine, in kodein") {
    val di = DI { import(ktorServerNettyEngineModule) }.direct
    assert(di.instance<KtorServerEngine>().factory === Netty)
  }

  test("KtorServerNettyEngineBindings provides Netty as the KtorServerEngine, in metro") {
    assert(createGraph<KtorServerNettyEngineGraph>().engine.factory === Netty)
  }

  test("ktorServerNettyEngineModule() provides Netty as the KtorServerEngine, in inara") {
    assert(ktorServerNettyEngineModule().ktorServerEngine?.factory === Netty)
  }
}
