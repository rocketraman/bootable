package snippets.environment

import com.github.rocketraman.bootable.env.Env
import com.github.rocketraman.bootable.env.environment

// tag::use[]
/** The environment, as a tag on the application's metrics. */
fun environmentTag(): String = when (val env = environment) {
  Env.Dev -> "dev"
  Env.Test -> "test"
  Env.Prod -> "prod"
  is Env.Custom -> env.name
}
// end::use[]
