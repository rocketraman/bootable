package bench

fun build(fw: String, n: Int): Set<Svc> = when (fw to n) {
  "metro" to 25 -> dev.zacsweers.metro.createGraph<MetroG25>().all
  "kodein" to 25 -> kodein25()
  "metro" to 100 -> dev.zacsweers.metro.createGraph<MetroG100>().all
  "kodein" to 100 -> kodein100()
  "metro" to 400 -> dev.zacsweers.metro.createGraph<MetroG400>().all
  "kodein" to 400 -> kodein400()
  else -> error("unknown $fw $n")
}

fun main(args: Array<String>) {
  val fw = args[0]; val n = args[1].toInt()
  val t0 = System.nanoTime(); val s1 = build(fw, n); val cold = (System.nanoTime() - t0) / 1_000_000.0
  val t1 = System.nanoTime(); val s2 = build(fw, n); val warm = (System.nanoTime() - t1) / 1_000_000.0
  println("RESULT fw=$fw n=$n size=${s1.size} cold_ms=$cold warm_ms=$warm")
}
