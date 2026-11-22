package snippets.background

import com.github.rocketraman.bootable.client.http.ktor.CioClientEngine
import com.github.rocketraman.bootable.client.http.ktor.KtorClientEngine
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides

// tag::wire[]
// boot-leader-k8s-metro contributes the LeaderElection, and boot-client-http-ktor-metro the HTTP clients it calls
// the API server with; the application chooses their engine.
@ContributesTo(BackgroundScope::class)
interface HttpClientEngineBindings {
  @Provides fun ktorClientEngine(): KtorClientEngine = CioClientEngine()
}
// end::wire[]
