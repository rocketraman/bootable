# Native-image footprint: Metro vs Kodein (measured)

Measured on the `examples/server-ktor` app, GraalVM CE 24.0.2, `fallback=false`, identical
except the DI framework (Kodein 7.25 on `master` / v2.2.0 vs Metro 1.3.2 on `proto/metro-di` / v3.0.0).

| Metric | Kodein (2.2.0) | Metro (3.0.0) | Delta |
|---|---|---|---|
| Native binary | 73,600,072 B | 73,337,928 B | −262,144 B (−0.36%) |
| Cold start (`=> Running`) | ~46 ms | ~40 ms | within noise |
| `boot` reachability-metadata.json | 1070 B, 5 entries | 1070 B, 5 entries | **identical** |

## Conclusion: the "smaller native metadata" thesis is NOT supported at this scale

- The `boot` module's bundled `reachability-metadata.json` is **byte-identical** between the two. Its 5
  entries are for signal-handling reflection (`sun.misc.Signal`, `StopSignalHandler`) and `Bootable` — **not**
  DI wiring. Neither Kodein nor Metro contributed DI reflection metadata here.
- `kotlin-reflect` is dragged onto **both** classpaths transitively (ktor, hoplite, kotlinx). Removing it from
  the `boot` module (a real Metro win) is invisible at the app level because the wider stack pulls it in anyway.
- Native-image already AOT-compiles graph construction, so Kodein's runtime-reflective graph build and Metro's
  generated graph both cost ~nothing at startup. **Metro's startup advantage is a JVM-mode effect that native
  compilation largely erases.**

## What this means

- Do not sell the Metro migration on native footprint or native cold-start — measured difference is negligible.
- Metro's real, still-valid wins are **compile-time graph validation** and **no runtime graph construction**
  (helps JVM-mode cold start and correctness), not native-image size.
- A native footprint win would only show up in a minimal app whose *entire* stack is reflection-free — not one
  built on ktor + hoplite + log4j, where those dominate the image and pull in reflection regardless.
