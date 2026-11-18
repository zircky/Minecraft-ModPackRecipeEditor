package dev.recipeeditor.recipeanalyzer.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import dev.recipeeditor.registry.ResourceJsonReader;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/**
 * Object order is irrelevant; array order, numeric representation and every unknown field are
 * preserved.
 */
public final class CanonicalJson {
  private final ResourceJsonReader reader = new ResourceJsonReader();

  public JsonNode read(final String raw) {
    try {
      if (raw.length() > 2 * 1024 * 1024
          || raw.getBytes(StandardCharsets.UTF_8).length > 2 * 1024 * 1024)
        throw new IllegalArgumentException("Analysis JSON exceeds 2 MiB");
      return reader.read(raw);
    } catch (java.io.IOException error) {
      throw new IllegalArgumentException("Invalid/bounded JSON", error);
    }
  }

  public String canonical(final String raw) {
    return sorted(read(raw)).toString();
  }

  public String hash(final String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private JsonNode sorted(final JsonNode value) {
    if (value.isObject()) {
      final var result = JsonNodeFactory.instance.objectNode();
      final var names = new ArrayList<String>();
      value.fieldNames().forEachRemaining(names::add);
      Collections.sort(names);
      for (final String name : names) result.set(name, sorted(value.get(name)));
      return result;
    }
    if (value.isArray()) {
      final var result = JsonNodeFactory.instance.arrayNode();
      value.forEach(item -> result.add(sorted(item)));
      return result;
    }
    return value;
  }
}
