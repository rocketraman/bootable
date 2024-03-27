import com.github.rocketraman.bootable.config.common.HostPort

data class ServerConfig(
  override val hostPort: String,
  val fakeSecret: String,
) : HostPort
