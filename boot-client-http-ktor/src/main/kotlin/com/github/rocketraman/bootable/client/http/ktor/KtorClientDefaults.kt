package com.github.rocketraman.bootable.client.http.ktor

/**
 * Default settings for named clients, contributed by a module rather than configured by the application: e.g. a
 * module that calls a platform API can default its client's trust store to the platform's mounted CA certificate, so
 * the application needn't configure it.
 *
 * Each contributed name is a named client of the [KtorClientService], whether or not the application configures it.
 * The application's own `http.client.named.<name>` settings win over the contributed ones, field by field, as they do
 * over `http.client.default` (so the application's `tls` replaces the contributed `tls` whole), and what both leave
 * unset comes from `http.client.default`.
 *
 * [clients] is called once, when the service is built, so a contributor can decide then what to contribute, e.g.
 * only when the file it points at exists. Two contributors of the same name fail the service's construction.
 *
 * Add one to the service's defaults, or to its set binding (Metro `@IntoSet`, Inara `@NamedClientDefaults`), or define
 * one with a qualifier of its own (Koin).
 */
fun interface KtorClientDefaults {
  /** The default settings of the named clients this contributes, by name. */
  fun clients(): Map<String, KtorClientConfig>
}
