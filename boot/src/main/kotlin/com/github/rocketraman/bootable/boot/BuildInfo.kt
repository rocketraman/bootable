package com.github.rocketraman.bootable.boot

import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config

/**
 * Which build of the application this is, so that a client, such as an end-to-end test run during a rollout, can
 * tell which version answered it, and telemetry can name the version. Every field is optional.
 *
 * Decoded from the `build` config section:
 *
 * ```
 * build {
 *   version = "1.4.2"
 *   revision = "3f2c9e1"
 *   attributes { branch = main }
 * }
 * ```
 *
 * The build, rather than the deployment, knows these values, so set them where the image is built, as environment
 * variables, which the config reads as `build.version` and `build.revision`: `BUILD_VERSION` and `BUILD_REVISION`.
 */
data class BuildInfo(
  /** The application's version, such as `1.4.2` or an image tag. */
  val version: String? = null,
  /** The source revision it was built from, such as a git commit. */
  val revision: String? = null,
  /** Anything else that identifies the build, such as its branch or build number. */
  val attributes: Map<String, String> = emptyMap(),
)

/** Decodes [BuildInfo] from the `build` config section using the given [ConfigBinder]. */
fun buildInfo(binder: ConfigBinder): BuildInfo = binder.config("build")
