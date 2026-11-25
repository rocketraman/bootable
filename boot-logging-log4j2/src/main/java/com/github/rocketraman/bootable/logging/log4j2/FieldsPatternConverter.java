package com.github.rocketraman.bootable.logging.log4j2;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.pattern.ConverterKeys;
import org.apache.logging.log4j.core.pattern.LogEventPatternConverter;
import org.apache.logging.log4j.core.pattern.PatternConverter;

/**
 * The {@code %fields} pattern converter.
 * Converts a log message of type {@link StructuredMessage} to its fields, as {@code {key=value, ...}}.
 * Ignores other messages.
 *
 * <p>Bootable's text layouts use this converter, not Log4j's {@code %K}, because {@code %K} writes the entries of every
 * map message, not just a {@link StructuredMessage}'s.
 * For a {@code StringMapMessage}, {@code %m} already writes its entries, as {@code key="value"}, so {@code %K} would
 * write them a second time on the same line.
 *
 * <p>Written in Java because Log4j's plugin annotation processor runs in javac, on Java sources.
 * It registers this class as a plugin, and generates its GraalVM reflection metadata.
 * The Kotlin compiler doesn't run annotation processors, so a Kotlin class would need kapt.
 * The formatting itself is in Kotlin, in {@code formatFields}.
 */
@Plugin(name = "FieldsPatternConverter", category = PatternConverter.CATEGORY)
@ConverterKeys({"fields"})
public final class FieldsPatternConverter extends LogEventPatternConverter {

  private static final FieldsPatternConverter INSTANCE = new FieldsPatternConverter();

  private FieldsPatternConverter() {
    super("Fields", "fields");
  }

  /** The converter; it takes no options. */
  public static FieldsPatternConverter newInstance(final String[] options) {
    return INSTANCE;
  }

  @Override
  public void format(final LogEvent event, final StringBuilder toAppendTo) {
    FieldsFormat.formatFields(event, toAppendTo);
  }
}
