package dev.recipeeditor.integrations.botania;

import com.fasterxml.jackson.databind.JsonNode;
import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.core.importing.*;
import dev.recipeeditor.integrations.api.*;
import dev.recipeeditor.recipeengine.*;
import dev.recipeeditor.registry.ResourceJsonReader;
import java.util.*;

/** Read-only structural adapter verified against Botania 1.20.1-456-FORGE. */
public final class BotaniaRecipeAdapter implements ModdedRecipeAdapter {
    private final Map<ResourceLocation,ModdedRecipeType> types;
    private final BotaniaJsonValues values=new BotaniaJsonValues();
    public BotaniaRecipeAdapter() {
        final var index=new LinkedHashMap<ResourceLocation,ModdedRecipeType>();
        register(index,"brew","Botania Brewing","Варка Botania","Botanical Brewery","Ботаническая пивоварня");
        register(index,"petal_apothecary","Petal Apothecary","Лепестковый аптекарь","Petal Apothecary","Лепестковый аптекарь");
        register(index,"mana_infusion","Mana Infusion","Наполнение маной","Mana Pool","Бассейн маны");
        types=Map.copyOf(index);
    }
    @Override public Map<ResourceLocation,ModdedRecipeType> types(){return types;}
    @Override public RecipeParseResult parse(final RecipeParseContext context) {
        if(!context.type().location().map(this::supports).orElse(false))throw new IllegalArgumentException("Not a registered Botania serializer");
        final var inputs=new ArrayList<IngredientDefinition>();final var outputs=new ArrayList<RecipeOutput>();
        final var slots=new LinkedHashMap<String,IngredientDefinition>();final var properties=new ArrayList<RecipeProperty>();
        final var diagnostics=new ArrayList<ImportDiagnostic>();RecipeParseStatus status=RecipeParseStatus.PARSED;
        try {
            final var root=object(context.rawJson());
            if(!root.path("type").asText().equals(context.type().displayId()))throw new IllegalArgumentException("Serializer type mismatch");
            final String type=context.type().location().orElseThrow().path();
            final boolean infusion=type.equals("mana_infusion");boolean partial=false;
            if(infusion) {final var input=values.ingredient(root.path("input"));inputs.add(input.value());partial=input.partial();values.integer(root.path("mana"),0,"mana");}
            else {
                final var ingredients=root.path("ingredients");
                if(!ingredients.isArray()||ingredients.isEmpty()||ingredients.size()>256)throw new IllegalArgumentException("Ingredients must contain 1..256 entries (editor safety bound)");
                for(final var node:ingredients){final var input=values.ingredient(node);inputs.add(input.value());partial|=input.partial();}
            }
            if(type.equals("brew")) {
                final var id=values.id(root.path("brew"));outputs.add(new RecipeOutput.Brew(new BrewDefinition(id,id.namespace()+".brew."+id.path(),Map.of())));
                diagnostics.add(new ImportDiagnostic(ScanStatus.WARNING,context.source().sourceFile(),"Declared brew reference only: runtime registration, effects, container and mana cost are not resolved statically"));
            } else {
                final var output=values.output(root.path("output"));outputs.add(output);
                final var names=root.path("output").fieldNames();while(names.hasNext())if(!Set.of("item","count").contains(names.next()))partial=true;
            }
            if(type.equals("petal_apothecary")){final var reagent=values.ingredient(root.path("reagent"));slots.put("reagent",reagent.value());inputs.add(reagent.value());partial|=reagent.partial();}
            if(infusion&&root.has("catalyst")) {
                final var catalyst=root.path("catalyst");
                if(!catalyst.isObject()||!catalyst.path("type").isTextual())throw new IllegalArgumentException("Legacy or invalid catalyst syntax");
                final String kind=catalyst.path("type").asText();
                if(kind.equals("block")||kind.equals("tag")){values.id(catalyst.path(kind));if(catalyst.size()!=2)partial=true;}
                else partial=true;
                slots.put("catalyst",new IngredientDefinition.Custom("botania:state_ingredient",catalyst.toString()));
            }
            if(root.has("group")&&!root.path("group").isTextual())throw new IllegalArgumentException("group must be text");
            final var allowed=type.equals("brew")?Set.of("type","brew","ingredients"):infusion?Set.of("type","input","output","mana","catalyst","group"):Set.of("type","ingredients","output","reagent");
            final var fields=root.fields();while(fields.hasNext()){final var entry=fields.next();if(!allowed.contains(entry.getKey()))partial=true;if(!Set.of("type","input","ingredients","output","brew").contains(entry.getKey()))properties.add(new RecipeProperty(entry.getKey(),entry.getValue().toString()));}
            if(partial){status=RecipeParseStatus.PARTIALLY_PARSED;diagnostics.add(new ImportDiagnostic(ScanStatus.WARNING,context.source().sourceFile(),"Unknown fields, conditions or custom matching are preserved and not evaluated"));}
        } catch(Exception invalid) {
            status=RecipeParseStatus.INVALID;inputs.clear();outputs.clear();slots.clear();properties.clear();
            diagnostics.add(new ImportDiagnostic(ScanStatus.ERROR,context.source().sourceFile(),"Invalid Botania recipe: "+invalid.getMessage()));
        }
        var recipe=new RecipeDefinition(context.id(),context.type(),inputs,outputs,new RecipeLayout(List.of(),slots),properties,context.source(),context.rawJson(),status,diagnostics);
        if(status!=RecipeParseStatus.INVALID) {
            final var layout=new RecipeLayout(List.of(),slots,Optional.of(new BotaniaRecipeLayoutProvider().create(recipe)));
            recipe=new RecipeDefinition(recipe.id(),recipe.type(),recipe.inputs(),recipe.outputs(),layout,recipe.properties(),recipe.source(),recipe.rawJson(),recipe.status(),recipe.diagnostics());
        }
        return new RecipeParseResult(recipe,diagnostics);
    }
    @Override public RecipeMachineLayout layout(final RecipeDefinition recipe){return recipe.layout().machine().orElseThrow(()->new IllegalArgumentException("Machine metadata is unavailable; parse the source using its adapter"));}
    @Override public RecipeTypeCapabilities capabilities(final RecipeDefinition recipe) {
        final var features=EnumSet.of(RecipeTypeCapabilities.Feature.REQUIRES_INTEGRATION);
        if(recipe.type().location().map(this::supports).orElse(false)&&recipe.status()!=RecipeParseStatus.INVALID&&recipe.status()!=RecipeParseStatus.UNSUPPORTED)features.add(RecipeTypeCapabilities.Feature.CAN_VIEW);
        if(recipe.type().displayId().equals("botania:brew"))features.add(RecipeTypeCapabilities.Feature.REQUIRES_RUNTIME);
        return new RecipeTypeCapabilities(features,"Botania 1.20.1-456 structural parser and read-only layout; authoring/export are not verified in Steps 1–4. Brew registry binding requires runtime evidence.");
    }
    private JsonNode object(final String raw)throws java.io.IOException {final var root=new ResourceJsonReader().read(raw);if(!root.isObject())throw new IllegalArgumentException("Recipe must be an object");return root;}
    private void register(final Map<ResourceLocation,ModdedRecipeType> index,final String path,final String en,final String ru,final String machineEn,final String machineRu){final var id=new ResourceLocation("botania",path);index.put(id,new ModdedRecipeType(id,Map.of("en_us",en,"ru_ru",ru),Map.of("en_us",machineEn,"ru_ru",machineRu)));}
}
