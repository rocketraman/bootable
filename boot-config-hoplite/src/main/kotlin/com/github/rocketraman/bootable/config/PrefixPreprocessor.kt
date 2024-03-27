package com.github.rocketraman.bootable.config

import com.sksamuel.hoplite.ConfigResult
import com.sksamuel.hoplite.DecoderContext
import com.sksamuel.hoplite.Node
import com.sksamuel.hoplite.fp.valid
import com.sksamuel.hoplite.preprocessor.Preprocessor

/**
 * Workaround lack of built-in prefix support in Hoplite. See
 * https://github.com/sksamuel/hoplite/issues/386#issuecomment-2019937200.
 */
class PrefixPreprocessor(private val prefix: String) : Preprocessor {
  override fun process(node: Node, context: DecoderContext): ConfigResult<Node> = node.atPath(prefix).valid()
}
