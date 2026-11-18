package dev.recipeeditor.ui;

import dev.recipeeditor.integrations.api.*;

import dev.recipeeditor.integrations.gregtech.*;

import dev.recipeeditor.recipeengine.RecipeDefinition;

import com.fasterxml.jackson.databind.JsonNode;

import javafx.scene.Node;

import javafx.scene.control.*;

import javafx.scene.layout.*;

import java.util.*;

/** Capability-scoped leaf forms patch the original tree; raw/custom payloads remain intact. */

public final class GregTechRecipeEditorProvider implements RecipeEditorProvider {

    @Override public boolean supports(final RecipeDefinition recipe, final IntegrationContext context) {

        return recipe.status()!=dev.recipeeditor.recipeengine.RecipeParseStatus.UNSUPPORTED && recipe.status()!=dev.recipeeditor.recipeengine.RecipeParseStatus.INVALID && recipe.type().location().map(id -> id.namespace().equals("gtceu")).orElse(false)

                && new GregTechIntegration().capabilities(context).enabled("Visual Editing");

    }

    @Override public Node createEditor(final RecipeEditorContext context) {

        final var model = new GregTechRecipeMapper().map(context.recipe());

        final var box = new VBox(12, new Label("GREGTECH RECIPE EDITOR"), new Label("Machine / Recipe Type: " + model.recipeType()));

        model.category().ifPresent(value -> box.getChildren().add(new Label("Category: " + value)));

        final var root = new GregTechRecipeMapper().object(model.rawJson());

        if (root.has("duration")) box.getChildren().add(field("Duration (ticks)", "/duration", root.get("duration"), context));

        for (final String role : List.of("inputs", "outputs", "tickInputs", "tickOutputs")) {

            if (!root.has(role)) continue;

            final var group = new VBox(8, new Label(role));

            root.get(role).fields().forEachRemaining(capability -> {

                for (int index = 0; index < capability.getValue().size(); index++) {

                    final var content = capability.getValue().get(index);

                    final String pointer = "/" + role + "/" + capability.getKey() + "/" + index;

                    final var card = new VBox(5, new Label(capability.getKey() + " #" + (index + 1)));

                    card.setStyle("-fx-padding: 10; -fx-border-color: #45474b; -fx-border-radius: 4;");

                    leaves(content, pointer, context, card);

                    group.getChildren().add(card);

                }

            });

            box.getChildren().add(group);

        }

        if (!model.conditions().isEmpty()) box.getChildren().add(new Label("Conditions preserved: " + model.conditions() + " — codec verification required before export"));

        if (!model.additionalProperties().isEmpty()) {

            final var label = new Label("Additional properties preserved. Inspect Advanced / Raw JSON: " + model.additionalProperties().keySet()); label.setWrapText(true); box.getChildren().add(label);

        }

        box.getChildren().add(new Label("Machine-specific limits require runtime evidence. Unknown properties are retained; unsupported export is blocked."));

        return box;

    }

    private void leaves(final JsonNode value, final String pointer, final RecipeEditorContext context, final VBox box) {

        if (value.isObject()) value.fields().forEachRemaining(entry -> {

            if (Set.of("content", "ingredient", "value").contains(entry.getKey())) leaves(entry.getValue(), pointer + "/" + entry.getKey(), context, box);

            else if (Set.of("item", "tag", "fluid", "count", "amount", "voltage", "amperage", "chance", "maxChance", "tierChanceBoost").contains(entry.getKey()) && entry.getValue().isValueNode()) box.getChildren().add(field(entry.getKey(), pointer + "/" + entry.getKey(), entry.getValue(), context));

            else { final var text = new Label(entry.getKey() + ": " + entry.getValue()); text.setWrapText(true); text.setMaxWidth(850); box.getChildren().add(text); }

        });

        else if (value.isArray()) { for (int i=0; i<value.size(); i++) leaves(value.get(i), pointer + "/" + i, context, box); }

        else if (value.isNumber()) box.getChildren().add(field("EU/t", pointer, value, context));

        else box.getChildren().add(new Label("Custom value preserved: " + value));

    }

    private Node field(final String title, final String pointer, final JsonNode original, final RecipeEditorContext context) {

        final String key = "GT" + pointer;

        final String initial = original.isTextual() ? original.asText() : original.toString();

        final var input = new TextField(context.pending().getOrDefault(key, initial)); input.setPrefWidth(320);

        input.textProperty().addListener((observable, before, after) -> context.changed().accept(key, after));

        final var apply = new Button("Apply");

        apply.setOnAction(event -> {

            try {

                final String json = original.isTextual() ? new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(input.getText()) : input.getText();

                context.apply().accept(pointer, json);

            } catch (Exception invalid) { new Alert(Alert.AlertType.ERROR, invalid.getMessage(), ButtonType.OK).showAndWait(); }

        });

        return new HBox(10, new Label(title), input, apply);

    }

}

