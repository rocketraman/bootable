package com.github.rocketraman.bootable.common

// In Kotlin, we can return Unit when we want to ignore a block - allow using Ignore instead of Unit for more readability
// For example, instead of:
//  try { foo() } catch (e: Exception) { /* ignore */ }
// we can do:
//  try { foo() } catch (e: Exception) { Ignore }
@Suppress("PropertyName")
val Ignore = Unit
