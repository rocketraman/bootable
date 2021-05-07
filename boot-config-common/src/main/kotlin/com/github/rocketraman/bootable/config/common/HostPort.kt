package com.github.rocketraman.bootable.config.common

import java.net.InetAddress

fun HostPort.host() = host("0.0.0.0")

fun HostPort.host(default: String) = hostPort.split(":")[0].ifBlank { default }

fun HostPort.port(default: Int) = hostPort.split(":").getOrNull(1)?.toInt() ?: default

fun HostPort.portOrNull() = hostPort.split(":").getOrNull(1)?.toInt()

fun HostPort.inetAddress() = InetAddress.getByName(host())

interface HostPort {
  companion object {
    operator fun invoke(hostPort: String) = object: HostPort { override val hostPort = hostPort }
    operator fun invoke(host: String, port: Int) = object: HostPort { override val hostPort = "$host:$port" }
    operator fun invoke(port: Int) = object: HostPort { override val hostPort = ":$port" }
  }

  val hostPort: String
}
