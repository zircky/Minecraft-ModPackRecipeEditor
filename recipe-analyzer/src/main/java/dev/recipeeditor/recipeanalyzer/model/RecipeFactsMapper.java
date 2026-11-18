package dev.recipeeditor.recipeanalyzer.model;

import dev.recipeeditor.recipeanalyzer.graph.DependencyEdge.*;
import dev.recipeeditor.recipeengine.*;
import java.math.BigInteger;
import java.util.*;

public final class RecipeFactsMapper {
  private final CanonicalJson json = new CanonicalJson();
  private final dev.recipeeditor.recipeanalyzer.graph.AnalysisTagResolver tags;

  public RecipeFactsMapper() {
    this(null);
  }

  public RecipeFactsMapper(final dev.recipeeditor.recipeanalyzer.graph.AnalysisTagResolver tags) {
    this.tags = tags;
  }

  private final StandardDependencyAdapter standard = new StandardDependencyAdapter();

  public RecipeFacts map(
      final RecipeDefinition recipe,
      final String origin,
      final int ordinal,
      final List<RecipeDependencyAdapter> adapters,
      final boolean vanillaRulesKnown) {
    final String collected =
        recipe.properties().stream()
            .filter(p -> p.name().equals("analysis.runtimeCanonical"))
            .map(RecipeProperty::rawValue)
            .findFirst()
            .orElse(recipe.rawJson());
    final String runtimeClass =
        recipe.properties().stream()
            .filter(p -> p.name().equals("analysis.runtimeClass"))
            .map(RecipeProperty::rawValue)
            .findFirst()
            .orElse("");
    final String canonical =
        json.hash(recipe.type().displayId() + "|" + runtimeClass + "|" + json.canonical(collected));
    final var matches = adapters.stream().filter(a -> a.supports(recipe)).toList();
    if (matches.size() > 1)
      throw new DependencyAdapterConflictException(
          "Conflicting dependency adapters for " + recipe.id());
    final var ports = (matches.isEmpty() ? standard : matches.getFirst()).describe(recipe);
    final String source =
        recipe.source().archivePath().getFileName() + "!" + recipe.source().internalPath();
    final String key =
        json.hash(origin + "|" + recipe.id() + "|" + source + "|" + canonical + "|" + ordinal);
    final boolean matcher =
        vanillaRulesKnown
            && ports.complete()
            && ports.inputs().stream().allMatch(p -> confirmedOperand(p.ingredient()))
            && Set.of(
                    "minecraft:crafting_shaped",
                    "minecraft:crafting_shapeless",
                    "minecraft:smelting",
                    "minecraft:blasting",
                    "minecraft:smoking",
                    "minecraft:campfire_cooking",
                    "minecraft:stonecutting")
                .contains(recipe.type().displayId());
    final String inputs =
        recipe.type().displayId().equals("minecraft:crafting_shaped")
            ? shaped(recipe)
            : signature(ports.inputs());
    return new RecipeFacts(
        key,
        recipe,
        origin,
        source,
        canonical,
        inputs,
        signature(ports.outputs()),
        ratio(recipe, ports),
        matcher,
        ports.complete(),
        ports.inputs(),
        ports.outputs(),
        ports.uncertainties());
  }

  private boolean confirmedOperand(final DependencyIngredient value) {
    if (value.resource() == null)
      return value.alternatives().stream().allMatch(this::confirmedOperand);
    if (value.resource().type() == dev.recipeeditor.recipeanalyzer.graph.DependencyNodeType.UNKNOWN)
      return false;
    if (value.resource().type() == dev.recipeeditor.recipeanalyzer.graph.DependencyNodeType.TAG) {
      if (tags == null) return false;
      final var resolved = tags.resolve(value.resource());
      return resolved.status()
              == dev.recipeeditor.recipeanalyzer.graph.TagResolution.Status.RESOLVED_RUNTIME
          && !resolved.truncated()
          && !resolved.members().isEmpty();
    }
    return true;
  }

  public RecipeFacts opaque(final RecipeDefinition recipe, final String origin, final int ordinal) {
    final String hash = json.hash("opaque:" + recipe.type().displayId() + ":" + recipe.rawJson());
    final var port =
        new RecipePort(
            "unknown",
            new IngredientDependencies().opaque(recipe.rawJson()),
            Consumption.UNKNOWN,
            Cadence.UNKNOWN,
            null,
            null,
            null);
    return new RecipeFacts(
        json.hash(origin + recipe.id() + hash + ordinal),
        recipe,
        origin,
        "Opaque source",
        hash,
        "",
        "",
        "",
        false,
        false,
        List.of(port),
        List.of(),
        List.of("Raw/adapter semantics unavailable"));
  }

  private String signature(final List<RecipePort> ports) {
    if (ports.isEmpty()) return "";
    final var values = new ArrayList<String>();
    for (final var port : ports)
      values.add(operand(port.ingredient()) + ":" + port.consumption() + ":" + port.cadence());
    Collections.sort(values);
    return json.hash(values.toString());
  }

  private String operand(final DependencyIngredient ingredient) {
    if (ingredient.resource() != null)
      return ingredient.resource()
          + "*"
          + ingredient.quantity()
          + "~"
          + ingredient.representationHash();
    final var choices = ingredient.alternatives().stream().map(this::operand).sorted().toList();
    return "OR" + choices;
  }

  private String shaped(final RecipeDefinition recipe) {
    final var pattern = recipe.layout().pattern();
    if (pattern.isEmpty()) return "";
    int top = 0, bottom = pattern.size(), left = pattern.getFirst().length(), right = 0;
    for (int y = 0; y < pattern.size(); y++)
      for (int x = 0; x < pattern.get(y).length(); x++)
        if (pattern.get(y).charAt(x) != ' ') {
          left = Math.min(left, x);
          right = Math.max(right, x + 1);
        }
    while (top < bottom && pattern.get(top).isBlank()) top++;
    while (bottom > top && pattern.get(bottom - 1).isBlank()) bottom--;
    final var forward = new ArrayList<String>();
    final var mirrored = new ArrayList<String>();
    final var ingredients = new IngredientDependencies();
    for (int y = top; y < bottom; y++)
      for (int x = left; x < right; x++) {
        forward.add(cell(recipe, pattern.get(y).charAt(x), ingredients));
        mirrored.add(cell(recipe, pattern.get(y).charAt(right - 1 - (x - left)), ingredients));
      }
    final String normal = forward.toString(), mirror = mirrored.toString();
    return json.hash(
        (right - left)
            + "x"
            + (bottom - top)
            + ":"
            + (normal.compareTo(mirror) < 0 ? normal : mirror));
  }

  private String cell(
      final RecipeDefinition recipe, final char key, final IngredientDependencies ingredients) {
    return key == ' '
        ? "EMPTY"
        : operand(ingredients.describe(recipe.layout().slots().get(String.valueOf(key)), true));
  }

  private String ratio(
      final RecipeDefinition recipe, final RecipeDependencyAdapter.ListPorts ports) {
    if (!ports.complete() || !recipe.type().displayId().equals("minecraft:crafting_shapeless"))
      return "";
    final var values = new TreeMap<String, BigInteger>();
    for (final var side : List.of(ports.inputs(), ports.outputs()))
      for (final var port : side) {
        final var value = port.ingredient();
        if (value.resource() == null
            || value.quantity() == null
            || !value.representationHash().isEmpty()) return "";
        if (value.resource().type()
            != dev.recipeeditor.recipeanalyzer.graph.DependencyNodeType.ITEM) return "";
        try {
          values.merge(
              (side == ports.inputs() ? "I:" : "O:") + value.resource(),
              value.quantity().toBigIntegerExact(),
              BigInteger::add);
        } catch (ArithmeticException nonInteger) {
          return "";
        }
      }
    if (values.isEmpty()) return "";
    BigInteger divisor = BigInteger.ZERO;
    for (final var value : values.values()) divisor = divisor.gcd(value);
    if (divisor.signum() == 0) return "";
    final var normalized = new ArrayList<String>();
    for (final var entry : values.entrySet())
      normalized.add(entry.getKey() + "=" + entry.getValue().divide(divisor));
    final var extras = json.read(recipe.rawJson()).deepCopy();
    if (extras instanceof com.fasterxml.jackson.databind.node.ObjectNode object)
      object.remove(List.of("ingredients", "result"));
    return json.hash(normalized + "|" + json.canonical(extras.toString()));
  }
}
