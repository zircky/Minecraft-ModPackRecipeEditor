package dev.recipeeditor.recipeanalyzer.model;

import dev.recipeeditor.companionprotocol.*;
import dev.recipeeditor.integrations.ProjectRecipeParsers;
import dev.recipeeditor.integrations.gregtech.GregTechRuntimeDraft;
import dev.recipeeditor.recipeengine.*;
import dev.recipeeditor.recipeengine.editor.*;
import dev.recipeeditor.registry.*;
import java.nio.file.Path;
import java.util.*;

/**
 * Converts only explicitly collected fields. Synthetic domain JSON is never an original
 * source/export contract.
 */
public final class RuntimeDependencyBridge {
  private final RecipeValidator validator;
  private final boolean vanillaSupported, gtSupported;

  public RuntimeDependencyBridge(
      final RegistryIndex registry, final boolean vanillaSupported, final boolean gtSupported) {
    this.vanillaSupported = vanillaSupported;
    this.gtSupported = gtSupported;
    validator = new RecipeValidator(new ProjectRecipeParsers().parsers(), registry);
  }

  public RecipeDefinition map(final RuntimeData.Recipe dto) {
    try {
      return convert(dto);
    } catch (java.util.concurrent.CancellationException cancelled) {
      throw cancelled;
    } catch (RuntimeException invalid) {
      return opaqueRuntime(dto);
    }
  }

  private RecipeDefinition convert(final RuntimeData.Recipe dto) {
    if (!vanillaSupported || (dto.adapter().startsWith("gtceu") && !gtSupported))
      return opaqueRuntime(dto);
    final String raw;
    try {
      raw =
          dto.adapter().equals("gtceu-7.1.4")
              ? new GregTechRuntimeDraft().copy(withoutVariants(dto))
              : "";
    } catch (RuntimeException invalid) {
      if (invalid instanceof java.util.concurrent.CancellationException cancelled) throw cancelled;
      return opaqueRuntime(dto);
    }
    final RecipeDefinition definition;
    if (raw.isEmpty()) definition = runtimeVanilla(dto);
    else definition = validator.validate(dto.id(), raw, source(dto)).recipe();
    final var properties = new ArrayList<>(definition.properties());
    final var collected = dto.properties();
    collected.remove("runtimeVariants");
    properties.add(new RecipeProperty("analysis.runtimeCanonical", collected.toString()));
    properties.add(
        new RecipeProperty(
            "analysis.runtimeClass",
            dto.adapter() + "|" + dto.serializer() + "|" + dto.runtimeClass()));
    final var retained =
        new RecipeDefinition(
            definition.id(),
            definition.type(),
            definition.inputs(),
            definition.outputs(),
            definition.layout(),
            properties,
            definition.source(),
            definition.rawJson(),
            definition.status(),
            definition.diagnostics());
    return retained;
  }

  private RuntimeData.Recipe withoutVariants(final RuntimeData.Recipe dto) {
    final var properties = dto.properties();
    properties.remove("runtimeVariants");
    return new RuntimeData.Recipe(
        dto.id(),
        dto.type(),
        dto.serializer(),
        dto.runtimeClass(),
        dto.adapter(),
        dto.status(),
        properties,
        dto.sourceInformation(),
        dto.diagnostics());
  }

  private RecipeSource source(final RuntimeData.Recipe dto) {
    return new RecipeSource(
        SourceType.RUNTIME,
        List.of(),
        Path.of("runtime-snapshot"),
        "recipes/" + dto.id(),
        "Runtime normalized state, original source UNKNOWN");
  }

  private RecipeDefinition opaqueRuntime(final RuntimeData.Recipe dto) {
    return new RecipeDefinition(
        dev.recipeeditor.core.ResourceLocation.parse(dto.id()),
        new RecipeTypeDefinition(dto.type()),
        List.of(),
        List.of(new RecipeOutput.Dynamic("Opaque runtime recipe")),
        new RecipeLayout(),
        List.of(
            new RecipeProperty(
                "analysis.runtimeClass",
                dto.adapter() + "|" + dto.serializer() + "|" + dto.runtimeClass())),
        source(dto),
        dto.properties().toString(),
        RecipeParseStatus.UNSUPPORTED,
        List.of());
  }

  private RecipeDefinition runtimeVanilla(final RuntimeData.Recipe dto) {
    // This does not recreate original JSON. It uses only explicitly collected normalized fields.
    if (!dto.adapter().equals("vanilla-1.20.1") || !dto.type().startsWith("minecraft:"))
      return opaqueRuntime(dto);
    final var p = dto.properties();
    final var root = new com.google.gson.JsonObject();
    root.addProperty("type", dto.serializer());
    if (p.has("result")) {
      final var result = p.getAsJsonObject("result");
      if (!result.has("item") || result.has("nbtSnbt")) return opaqueRuntime(dto);
      final var value = new com.google.gson.JsonObject();
      value.add("item", result.get("item"));
      value.add("count", result.get("count"));
      root.add("result", value);
    }
    if (dto.type().equals("minecraft:crafting")) {
      if (dto.serializer().equals("minecraft:crafting_shapeless"))
        root.add("ingredients", p.get("ingredients"));
      else if (dto.serializer().equals("minecraft:crafting_shaped")
          && p.has("width")
          && p.has("height")) {
        final int width = p.get("width").getAsInt(), height = p.get("height").getAsInt();
        final var values = p.getAsJsonArray("ingredients");
        if (width < 1 || height < 1 || width > 3 || height > 3 || values.size() != width * height)
          return opaqueRuntime(dto);
        final var pattern = new com.google.gson.JsonArray();
        final var keys = new com.google.gson.JsonObject();
        int symbol = 0;
        for (int y = 0; y < height; y++) {
          final var row = new StringBuilder();
          for (int x = 0; x < width; x++) {
            final var ingredient = values.get(y * width + x);
            if (ingredient.isJsonArray() && ingredient.getAsJsonArray().size() == 0)
              row.append(' ');
            else {
              final String key = String.valueOf((char) ('A' + symbol++));
              row.append(key);
              keys.add(key, ingredient);
            }
          }
          pattern.add(row.toString());
        }
        root.add("pattern", pattern);
        root.add("key", keys);
      } else return opaqueRuntime(dto);
    } else if (Set.of(
            "minecraft:smelting",
            "minecraft:blasting",
            "minecraft:smoking",
            "minecraft:campfire_cooking",
            "minecraft:stonecutting")
        .contains(dto.type())) {
      if (!p.has("ingredients") || p.getAsJsonArray("ingredients").size() != 1)
        return opaqueRuntime(dto);
      root.add("ingredient", p.getAsJsonArray("ingredients").get(0));
      if (dto.type().equals("minecraft:stonecutting")) {
        if (!p.has("result")) return opaqueRuntime(dto);
        root.add("result", p.getAsJsonObject("result").get("item"));
        root.add("count", p.getAsJsonObject("result").get("count"));
      }
      if (p.has("cookingTime")) root.add("cookingtime", p.get("cookingTime"));
      if (p.has("experience")) root.add("experience", p.get("experience"));
    } else return opaqueRuntime(dto);
    final var parsed = validator.validate(dto.id(), root.toString(), source(dto)).recipe();
    // Partial runtime adapters may hide custom ingredient/dynamic semantics: never confirm their
    // matcher.
    return dto.status() == RuntimeData.Support.SUPPORTED
            && p.keySet().stream()
                .allMatch(
                    Set.of(
                            "group",
                            "ingredients",
                            "result",
                            "width",
                            "height",
                            "category",
                            "showNotification",
                            "cookingTime",
                            "experience",
                            "sourceJsonAvailable")
                        ::contains)
        ? parsed
        : new RecipeDefinition(
            parsed.id(),
            parsed.type(),
            parsed.inputs(),
            parsed.outputs(),
            parsed.layout(),
            parsed.properties(),
            parsed.source(),
            parsed.rawJson(),
            RecipeParseStatus.PARTIALLY_PARSED,
            parsed.diagnostics());
  }
}
