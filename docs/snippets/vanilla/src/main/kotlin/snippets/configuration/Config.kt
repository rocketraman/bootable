package snippets.configuration

import com.github.rocketraman.bootable.config.common.HostPort
import com.sksamuel.hoplite.Secret

// tag::classes[]
data class GreetingConfig(
  val message: String,
  val apiKey: Secret,                  // masked in the config report
  val retries: Int = 3,                // optional, with its default
)

data class ServerConfig(
  override val hostPort: String,       // ":8080", "localhost:8080", or "localhost"
) : HostPort
// end::classes[]
