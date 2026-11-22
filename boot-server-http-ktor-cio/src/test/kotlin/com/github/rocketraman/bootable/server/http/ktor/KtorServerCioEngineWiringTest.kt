package com.github.rocketraman.bootable.server.http.ktor

import com.github.rocketraman.bootable.boot.BootScope
import de.infix.testBalloon.framework.core.testSuite
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.createGraph
import io.ktor.server.cio.*
import org.kodein.di.DI
import org.kodein.di.direct
import org.kodein.di.instance

sealed interface KtorServerCioEngineTestScope

@DependencyGraph(KtorServerCioEngineTestScope::class, additionalScopes = [BootScope::class])
interface KtorServerCioEngineGraph {
  val engine: KtorServerEngine
}

val KtorServerCioEngineWiringTest by testSuite {
  test("ktorServerCioEngineModule provides CIO as the KtorServerEngine, in kodein") {
    val di = DI { import(ktorServerCioEngineModule) }.direct
    assert(di.instance<KtorServerEngine>().factory === CIO)
  }

  test("KtorServerCioEngineBindings provides CIO as the KtorServerEngine, in metro") {
    assert(createGraph<KtorServerCioEngineGraph>().engine.factory === CIO)
  }

  test("ktorServerCioEngineModule() provides CIO as the KtorServerEngine, in inara") {
    assert(ktorServerCioEngineModule().ktorServerEngine?.factory === CIO)
  }
}
