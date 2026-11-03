package com.github.rocketraman.bootable.config

/**
 * A config value to keep out of logs, exception messages, and config reports, such as a password, an API key, or an
 * auth header: its [toString] is masked, so a config class that holds it can print itself safely. Read the value
 * with [value].
 *
 * This is for Bootable's own modules, such as the JDBC and telemetry modules, whose config classes depend only on
 * boot-config-common, not on a config library, so they can't declare a secret with the library's own type, such as
 * Hoplite's `Secret`. boot-config-hoplite decodes it from a string or a number, and masks it in its config report.
 *
 * Bootable doesn't aim to make config libraries interchangeable, so an application's own config classes should use
 * its config library's type for secrets, such as Hoplite's `Secret`, rather than this.
 */
data class Masked(val value: String) {
  override fun toString() = "****"
}
