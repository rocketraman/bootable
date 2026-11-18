// Build logic for the examples: plugins they apply, kept free of anything specific to this repository, so a
// downstream application can copy a plugin's file into its own buildSrc or build-logic.
plugins {
  `kotlin-dsl`
}

gradlePlugin {
  plugins {
    register("aotCache") {
      id = "bootable.aot-cache"
      implementationClass = "AotCachePlugin"
    }
  }
}
