package com.github.rocketraman.bootable.scheduler.db

import com.github.kagkarlsson.scheduler.serializer.Serializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializerOrNull
import java.util.concurrent.ConcurrentHashMap

/**
 * A db-scheduler [Serializer] that stores task data as JSON, with kotlinx-serialization, rather than with Java
 * serialization, db-scheduler's default: readable in the table, and safe to change as classes evolve, with
 * [Json.ignoreUnknownKeys] on by default, so an older instance reads data a newer one wrote.
 *
 * The data's class must be `@Serializable`. db-scheduler gives a task's data class, from its `TaskDescriptor`, but not
 * its type arguments, so the data can't be generic, such as a `List<String>`: wrap it in a class of its own.
 *
 * A subclass of a `@Serializable` sealed class or interface is stored with the sealed type's serializer, so its JSON
 * has the subclass's discriminator, and a task whose data class is the sealed type reads it back as the right subclass.
 * Such a subclass must have a `@SerialName`, so that renaming or moving it doesn't strand the data already stored.
 */
class KotlinxSerializationTaskDataSerializer(private val json: Json = DefaultJson) : Serializer {
  private val serializers = ConcurrentHashMap<Class<*>, KSerializer<Any?>>()

  override fun serialize(data: Any?): ByteArray? =
    data?.let { json.encodeToString(serializerFor(it.javaClass), it).encodeToByteArray() }

  override fun <T> deserialize(clazz: Class<T>, serializedData: ByteArray?): T? {
    if (serializedData == null) return null
    val value = json.decodeFromString(serializerFor(clazz), serializedData.decodeToString())
    return clazz.cast(value)
  }

  private fun serializerFor(type: Class<*>): KSerializer<Any?> = serializers.getOrPut(type) {
    val sealed = sealedAncestor(type)
    if (sealed != null && sealed.first != type && sealedSubclassName(sealed.second, type) == null) {
      throw SerializationException(
        "${type.name}, a subclass of the sealed ${sealed.first.name}, must have a @SerialName, so that its stored " +
          "task data still reads once it is renamed or moved"
      )
    }
    @Suppress("UNCHECKED_CAST")
    (sealed?.second ?: json.serializersModule.serializerOrNull(type)
      ?: throw SerializationException("${type.name} is not @Serializable")) as KSerializer<Any?>
  }

  /** The nearest sealed `@Serializable` class or interface of [type], itself included, with its serializer. */
  private fun sealedAncestor(type: Class<*>): Pair<Class<*>, KSerializer<*>>? {
    val queue = ArrayDeque(listOf(type))
    val seen = mutableSetOf<Class<*>>()
    while (queue.isNotEmpty()) {
      val candidate = queue.removeFirst()
      if (!seen.add(candidate) || candidate == Any::class.java) continue
      val serializer = json.serializersModule.serializerOrNull(candidate)
      if (serializer?.descriptor?.kind == PolymorphicKind.SEALED) return candidate to serializer
      candidate.superclass?.let(queue::add)
      queue.addAll(candidate.interfaces)
    }
    return null
  }

  /** The serial name [sealed] gives [subclass], or null if it is the default, the class's own name. */
  private fun sealedSubclassName(sealed: KSerializer<*>, subclass: Class<*>): String? {
    val default = subclass.name.replace('$', '.')
    // the sealed serializer's descriptor lists its subclasses' serial names as the elements of its value element
    val subclasses = sealed.descriptor.getElementDescriptor(1)
    val names = (0 until subclasses.elementsCount).map { subclasses.getElementName(it) }
    val own = json.serializersModule.serializerOrNull(subclass)?.descriptor?.serialName
    return own?.takeIf { it in names && it != default }
  }

  companion object {
    /** The default [Json]: unknown keys are ignored, so data written by a newer version still reads. */
    val DefaultJson: Json = Json { ignoreUnknownKeys = true }
  }
}
