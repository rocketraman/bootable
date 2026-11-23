# Choosing a wiring style

Bootable’s core embeds no DI framework. Each module ships its plain artifact, and, where it has bindings for them, `-kodein`, `-koin`, `-metro`, and `-inara` artifacts too. Depend on the flavour you wire with.

*Vanilla (no DI)*

Small or single-module apps, or anyone who dislikes DI frameworks. You list your services explicitly. No extra dependencies, no annotations, no compiler plugin.

*Kodein-DI (runtime, deprecated)*

You want multibinding aggregation across modules without a compiler plugin. Kodein is an ordinary jar; wiring is runtime code.

> **Warning:** Kodein is deprecated: its authors are sunsetting it. Consider Inara, its compile-time successor, or, to stick with a style more like Kodein’s, Koin. Bootable’s Kodein bindings are deprecated too.

*Koin (runtime)*

You want a runtime DSL like Kodein’s, from an actively maintained library. Koin is an ordinary jar; wiring is runtime code. Koin has no set multibindings: Bootable collects every definition of a type, such as each `AppService`, and you give each of yours a qualifier of its own.

*Metro (compile-time)*

You want the wiring validated by the compiler: missing and duplicate bindings are build errors, with no runtime reflection. Metro is a Kotlin compiler plugin: it needs Kotlin 2.3 or later, and its Gradle plugin.

*Inara (compile-time, experimental)*

Explicit wiring, checked by the compiler. Inara uses modules declared as interfaces coupled with a simple Kotlin DSL, rather than annotations like Metro. Inara requires KSP; a service is a module member marked `@AppServices`, rather than a multibinding. Bootable’s Inara bindings are experimental, as Inara itself is.

The code on every page comes in a tab per style. The style you choose here, in the header’s **Wiring** switch, or in any tab, shows on every page of the site, and is remembered on your next visit.
