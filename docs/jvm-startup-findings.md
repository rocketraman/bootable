# JVM startup: Metro vs Kodein (measured)

Companion to `native-image-findings.md`. Measured 2026-07-22, Temurin 21, fresh JVM per run, min of 9 (bench)
or 5 (app) runs. Benchmark source: `examples/di-bench` (generated graphs of N zero-work services sharing one
dependency, into a `Set`, resolved once).

## Isolated DI graph construction (cold JVM, DI only)

| bindings | Kodein cold | Metro cold | speedup | Kodein warm | Metro warm |
|---:|---:|---:|---:|---:|---:|
| 25 | 56.8 ms | 6.3 ms | 9.1× | 0.89 ms | 0.02 ms |
| 100 | 86.7 ms | 11.2 ms | 7.7× | 2.06 ms | 0.05 ms |
| 400 | 155.5 ms | 24.3 ms | 6.4× | 4.15 ms | 0.14 ms |

- **Cold** is the only number that matters: DI construction happens exactly once, at process start, on a cold
  JVM. There is no warm graph build in a running app (a single application-lifetime graph is built once and
  never again). Metro builds it 6–9× faster; the absolute gap grows with size (≈50 ms → ≈130 ms).
- The **warm** column is a *diagnostic only*, not a real scenario — it builds the graph a second time in the
  same JVM to separate algorithmic graph-building from class-loading. Both are sub-5 ms warm, which proves the
  entire cold gap is **class-loading + reflection initialization**, not graph-building: Kodein drags in
  `kotlin-reflect` + the kaverit type system (~50 ms fixed to load/init on first use); Metro loads a handful of
  small generated classes. (Warm construction only happens away from startup — repeated graph builds in a test
  suite, or per-request child graphs, which is not Bootable's model.)

## Real app (examples/server-ktor), full JVM cold start to `=> Running`

| | min | note |
|---|---:|---|
| Metro (3.0.0) | 421 ms | tight: 421–434 ms |
| Kodein (2.2.0) | 438 ms | noisier: 438–578 ms |

Only **~17 ms** apart — far less than the isolated ~50 ms. Reason: the surrounding stack (Hoplite config
decode, ktor, netty, log4j) loads `kotlin-reflect` **anyway**, so Metro doesn't get to skip that class-loading
cost at the app level. What's left of Kodein's penalty is just its own classes (kodein + kaverit) plus the graph
build — a small, partly-overlapping slice of a ~450 ms cold start dominated by JVM warmup and framework loading.

## Synthesis (all four measurements)

DI construction is always cold (once, at startup), so only cold rows describe a real situation. The warm row is
omitted here — it is a diagnostic, above, not a scenario that occurs in a running app.

| Scenario | Metro advantage |
|---|---|
| DI construction, cold JVM, isolated/DI-only | large: 6–9× (~50–130 ms), grows with graph size |
| **Real app, cold JVM** | **small: ~17 ms**, swamped by config/ktor/JVM startup |
| Real app, native image | negligible (~6 ms, within noise) |

**Conclusion.** The hypothesis "won't matter until the graph is significant" is *directionally* right for a
realistic app, but not for the stated reason. In an isolated DI benchmark Metro is dramatically faster cold —
and *more* so at small graphs (ratio), because Kodein's cost is mostly a fixed reflection-machinery load. But in
a real Bootable service the rest of the stack pulls in `kotlin-reflect` and dominates startup, shrinking the
difference to ~17 ms on the JVM and to noise on native. So: **do not migrate for startup.** Metro would only
deliver a visibly faster JVM start if the graph were huge (thousands of bindings) *and* the rest of the app were
reflection-free — not this stack. The real reasons to prefer Metro remain compile-time validation and
explicitness.
