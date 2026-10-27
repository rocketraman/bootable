import com.github.rocketraman.bootable.boot.DefaultPhase
import com.github.rocketraman.bootable.boot.DefaultStartOrder
import com.github.rocketraman.bootable.boot.Phase
import com.github.rocketraman.bootable.boot.StartOrder

/** This application's own phases, which it adds to Bootable's. */
enum class AppPhase : Phase {
  /** Tells the world outside the process that the app is serving, once the server is listening. */
  Announce,
}

/**
 * Bootable's phases, plus [AppPhase.Announce] after [DefaultPhase.Serving]: none of the default phases starts
 * after the server, so an app that must act once it is listening adds a phase of its own.
 *
 * Shutdown runs the phases in reverse, so [AppPhase.Announce] also stops first, while the server still serves.
 * Everything else is [DefaultStartOrder]'s: the default phase, and each service's own ordering.
 */
object AppStartOrder : StartOrder by DefaultStartOrder {
  override val phases = DefaultStartOrder.phases + AppPhase.Announce
}
