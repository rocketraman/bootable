import com.github.rocketraman.bootable.boot.AppService
import com.github.rocketraman.bootable.boot.Bootable
import com.github.rocketraman.bootable.boot.boot
import com.github.rocketraman.bootable.config.ConfigBinder
import com.github.rocketraman.bootable.config.config
import com.github.rocketraman.bootable.config.hopliteConfigBinder

/** Vanilla wiring: the server is the only service, and Bootable starts and stops it. */
fun appServices(binder: ConfigBinder): Set<AppService> = setOf(
  TodoServer(binder.config("server"), TodoRepository()),
)

fun main() = boot {
  Bootable(appServices = appServices(hopliteConfigBinder()))
}
