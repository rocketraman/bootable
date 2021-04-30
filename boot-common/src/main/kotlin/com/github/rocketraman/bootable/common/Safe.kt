package com.github.rocketraman.bootable.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

inline fun safeExec(block: () -> Unit) {
  try {
    block()
  } catch (t: Throwable) {
    Ignore
  }
}

inline fun <R> safeExec(onThrowable: (t: Throwable) -> R, block: () -> R): R {
  return try {
    block()
  } catch (t: Throwable) {
    onThrowable(t)
  }
}

inline fun safeSuppress(e: Exception, block: () -> Unit) {
  try {
    block()
  } catch (t: Throwable) {
    e.addSuppressed(t)
  }
}

inline fun <R> safeSuppress(e: Exception, onThrowable: (t: Throwable) -> R, block: () -> R): R {
  return try {
    block()
  } catch (t: Throwable) {
    e.addSuppressed(t)
    onThrowable(t)
  }
}

inline fun CoroutineScope.safeLaunch(crossinline block: suspend () -> Unit) = launch {
  try {
    block()
  } catch (t: Throwable) {
    Ignore
  }
}


inline fun CoroutineScope.safeLaunch(crossinline onThrowable: (t: Throwable) -> Unit, crossinline block: suspend () -> Unit) = launch {
  try {
    block()
  } catch (t: Throwable) {
    onThrowable(t)
  }
}
