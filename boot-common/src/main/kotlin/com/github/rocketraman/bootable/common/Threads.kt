package com.github.rocketraman.bootable.common

fun threadStacks(): String = buildString {
  Thread.getAllStackTraces().forEach { (t, st) ->
    // can't update to non-deprecated method until minimum support is Java 19
    appendLine("""Thread "${t.name}" ${if(t.isDaemon) "daemon " else " " }prio=${t.priority} tid=${t.id} (${t.state})""")
    st.forEach {
      appendLine("\tat $it")
    }
  }
}
