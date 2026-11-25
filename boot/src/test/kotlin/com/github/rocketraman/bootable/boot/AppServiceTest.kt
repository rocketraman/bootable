package com.github.rocketraman.bootable.boot

import de.infix.testBalloon.framework.core.testSuite

private class Unnamed : AppService

private class NamedByProperty : AppService {
  override val name = "property"
}

private class NamedByFunction : AppService {
  @Suppress("OVERRIDE_DEPRECATION")
  override fun name() = "function"
}

val AppServiceTest by testSuite {
  test("the name defaults to the simple class name") {
    assert(Unnamed().name == "Unnamed")
  }

  test("a service overrides the name property") {
    assert(NamedByProperty().name == "property")
  }

  test("a service that overrides the deprecated name function keeps its name") {
    assert(NamedByFunction().name == "function")
  }
}
