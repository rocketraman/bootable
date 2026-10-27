import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.Bootable
import com.github.rocketraman.bootable.boot.boot
import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.common.host
import com.github.rocketraman.bootable.config.common.port
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.config.hopliteConfigBinder

/** Vanilla wiring: the renderer and the server, which Bootable starts phase by phase, renderer first. */
fun appServices(binder: ConfigBinder): Set<AppService> {
  val serverConfig = binder.config<ServerConfig>("server")
  val renderer = SsrRenderer(binder.config("ssr"), serverConfig.localUrl())
  return setOf(
    renderer,
    TodoServer(serverConfig, TodoRepository(), renderer),
  )
}

/** The server's URL from this machine, for the renderer to call it at. */
fun ServerConfig.localUrl(): String {
  val host = host("127.0.0.1").takeUnless { it == "0.0.0.0" } ?: "127.0.0.1"
  return "http://$host:${port(8080)}"
}

fun main() = boot {
  Bootable(appServices = appServices(hopliteConfigBinder()))
}
