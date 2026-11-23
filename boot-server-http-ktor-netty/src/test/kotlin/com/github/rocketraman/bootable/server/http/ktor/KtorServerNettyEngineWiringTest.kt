@file:Suppress("DEPRECATION") // the deprecated Kodein flavor is still tested

package com.github.rocketraman.bootable.server.http.ktor

import com.github.rocketraman.bootable.boot.BootScope
import com.github.rocketraman.bootable.server.http.ktor.koin.ktorServerNettyEngineModule as koinKtorServerNettyEngineModule
import de.infix.testBalloon.framework.core.testSuite
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.createGraph
import io.ktor.server.netty.*
import org.kodein.di.DI
import org.kodein.di.direct
import org.kodein.di.instance
import org.koin.dsl.koinApplication

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

  test("ktorServerNettyEngineModule provides Netty as the KtorServerEngine, in koin") {
    val koin = koinApplication { modules(koinKtorServerNettyEngineModule()) }.koin
    try {
      assert(koin.get<KtorServerEngine>().factory === Netty)
    } finally {
      koin.close()
    }
  }

  test("KtorServerNettyEngineBindings provides Netty as the KtorServerEngine, in metro") {
    assert(createGraph<KtorServerNettyEngineGraph>().engine.factory === Netty)
  }

  test("ktorServerNettyEngineModule() provides Netty as the KtorServerEngine, in inara") {
    assert(ktorServerNettyEngineModule().ktorServerEngine?.factory === Netty)
  }
}
