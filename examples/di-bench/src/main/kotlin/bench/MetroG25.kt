package bench

import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.AppScope

@SingleIn(AppScope::class)
@DependencyGraph(AppScope::class)
interface MetroG25 {
  val all: Set<Svc>
  @Provides @SingleIn(AppScope::class) fun common(): Common = Common()
  @Provides @IntoSet fun s0(c: Common): Svc = Svc0(c)
  @Provides @IntoSet fun s1(c: Common): Svc = Svc1(c)
  @Provides @IntoSet fun s2(c: Common): Svc = Svc2(c)
  @Provides @IntoSet fun s3(c: Common): Svc = Svc3(c)
  @Provides @IntoSet fun s4(c: Common): Svc = Svc4(c)
  @Provides @IntoSet fun s5(c: Common): Svc = Svc5(c)
  @Provides @IntoSet fun s6(c: Common): Svc = Svc6(c)
  @Provides @IntoSet fun s7(c: Common): Svc = Svc7(c)
  @Provides @IntoSet fun s8(c: Common): Svc = Svc8(c)
  @Provides @IntoSet fun s9(c: Common): Svc = Svc9(c)
  @Provides @IntoSet fun s10(c: Common): Svc = Svc10(c)
  @Provides @IntoSet fun s11(c: Common): Svc = Svc11(c)
  @Provides @IntoSet fun s12(c: Common): Svc = Svc12(c)
  @Provides @IntoSet fun s13(c: Common): Svc = Svc13(c)
  @Provides @IntoSet fun s14(c: Common): Svc = Svc14(c)
  @Provides @IntoSet fun s15(c: Common): Svc = Svc15(c)
  @Provides @IntoSet fun s16(c: Common): Svc = Svc16(c)
  @Provides @IntoSet fun s17(c: Common): Svc = Svc17(c)
  @Provides @IntoSet fun s18(c: Common): Svc = Svc18(c)
  @Provides @IntoSet fun s19(c: Common): Svc = Svc19(c)
  @Provides @IntoSet fun s20(c: Common): Svc = Svc20(c)
  @Provides @IntoSet fun s21(c: Common): Svc = Svc21(c)
  @Provides @IntoSet fun s22(c: Common): Svc = Svc22(c)
  @Provides @IntoSet fun s23(c: Common): Svc = Svc23(c)
  @Provides @IntoSet fun s24(c: Common): Svc = Svc24(c)
}
