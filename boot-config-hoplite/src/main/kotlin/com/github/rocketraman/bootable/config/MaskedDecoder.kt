package com.github.rocketraman.bootable.config

import com.sksamuel.hoplite.ConfigFailure
import com.sksamuel.hoplite.ConfigResult
import com.sksamuel.hoplite.DecoderContext
import com.sksamuel.hoplite.DoubleNode
import com.sksamuel.hoplite.LongNode
import com.sksamuel.hoplite.Node
import com.sksamuel.hoplite.StringNode
import com.sksamuel.hoplite.decoder.NullHandlingDecoder
import com.sksamuel.hoplite.fp.invalid
import com.sksamuel.hoplite.fp.valid
import com.sksamuel.hoplite.secrets.SecretsPolicy
import kotlin.reflect.KType

/**
 * Decodes a [Masked] from a string, or a number, as Hoplite's own `Secret` is.
 *
 * It records each value it decodes, with its type, for the config report, so [MaskedSecretsPolicy] masks it wherever
 * it is: Hoplite's own decoders record a data class's properties, but not a map's values, which the report would
 * otherwise list as unused, in plain text.
 */
class MaskedDecoder : NullHandlingDecoder<Masked> {
  override fun supports(type: KType): Boolean = type.classifier == Masked::class

  override fun safeDecode(node: Node, type: KType, context: DecoderContext): ConfigResult<Masked> {
    val masked = when (node) {
      is StringNode -> Masked(node.value)
      is LongNode -> Masked(node.value.toString())
      is DoubleNode -> Masked(node.value.toString())
      else -> return ConfigFailure.DecodeError(node, type).invalid()
    }
    context.used(node, type, masked)
    return masked.valid()
  }
}

/** A [SecretsPolicy] that treats every value decoded as a [Masked] as a secret, so the config report masks it. */
object MaskedSecretsPolicy : SecretsPolicy {
  override fun isSecret(node: Node, type: KType?): Boolean = type?.classifier == Masked::class
}
