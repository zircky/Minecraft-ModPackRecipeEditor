package dev.recipeeditor.ui;

import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.recipeengine.*;
import dev.recipeeditor.recipeengine.editor.RecipeFormService;
import java.util.*;
import java.util.function.Function;

/** Adapters must explicitly supply a model; namespace alone never implies a crafting layout. */
public final class RecipeLayoutResolver {
    private final dev.recipeeditor.integrations.api.ModdedRecipeAdapterRegistry modded=new dev.recipeeditor.integrations.ProjectRecipeAdapters().registry();
    private final RecipeLayoutProvider vanilla=new RecipeLayoutProvider();
    private final CookingRecipeLayoutProvider cooking=new CookingRecipeLayoutProvider();
    private final RecipeFormService forms=new RecipeFormService();
    private final Map<String,Function<RecipeDefinition,RecipeLayoutModel>> integrations=new HashMap<>();
    public void register(final String type,final Function<RecipeDefinition,RecipeLayoutModel> adapter){ResourceLocation.parse(type);if(modded.supports(ResourceLocation.parse(type))||vanilla.find(type).isPresent()||integrations.putIfAbsent(type,Objects.requireNonNull(adapter))!=null)throw new IllegalArgumentException("Layout already registered: "+type);}
    public Optional<RecipeLayoutModel> resolve(final RecipeDefinition recipe) {
        final String type=recipe.type().displayId();
        if(integrations.containsKey(type)){final var value=integrations.get(type).apply(recipe);if(!value.recipeType().equals(type))throw new IllegalArgumentException("Layout type mismatch");return Optional.of(value);}
        final var adapter=recipe.type().location().flatMap(modded::find);
        if(adapter.isPresent()) {
            if(!adapter.get().capabilities(recipe).supports(dev.recipeeditor.integrations.api.RecipeTypeCapabilities.Feature.CAN_VIEW))return Optional.empty();
            return Optional.of(new RecipeLayoutModel(type,RecipeLayoutProvider.Kind.PROCESSING,List.of(),RecipeSlotContent.parse(""),false,Map.of(),Optional.of(adapter.get().layout(recipe))));
        }
        final var layout=vanilla.find(type);if(layout.isEmpty())return new SemanticRecipeLayout().resolve(recipe);
        if(recipe.source().type()==dev.recipeeditor.registry.SourceType.RUNTIME &&
                !new dev.recipeeditor.recipeengine.editor.RuntimeRecipeBase().matches(recipe.source()))
            return observedVanilla(recipe,layout.get().kind()).or(()->new SemanticRecipeLayout().resolve(recipe));
        final Map<String,String> fields;
        try{fields=forms.fields(recipe.rawJson());}catch(IllegalArgumentException invalid){return new SemanticRecipeLayout().resolve(recipe);}
        final var inputs=new ArrayList<RecipeLayoutModel.Input>();
        final var parameters=new LinkedHashMap<String,String>();
        if(layout.get().kind()==RecipeLayoutProvider.Kind.SHAPED) {final var values=forms.grid(recipe.rawJson());for(int i=0;i<9;i++)inputs.add(new RecipeLayoutModel.Input("grid",i,values.get(i)));}
        else if(layout.get().kind()==RecipeLayoutProvider.Kind.SHAPELESS) {final var values=forms.ingredientList(recipe.rawJson());for(int i=0;i<9;i++)inputs.add(new RecipeLayoutModel.Input("shapeless",i,i<values.size()?values.get(i):""));}
        else for(final var role:layout.get().inputs())inputs.add(new RecipeLayoutModel.Input(role,0,fields.getOrDefault(role,"")));
        cooking.find(type).ifPresent(machine->{
            for(final String key:List.of("cookingtime","experience"))parameters.put(key.equals("cookingtime")?"Cooking time (ticks)":"Experience",fields.getOrDefault(key,recipe.properties().stream().filter(p->p.name().equals(key)).map(RecipeProperty::rawValue).findFirst().orElse("Unknown")));
        });
        final boolean dynamic=layout.get().kind()==RecipeLayoutProvider.Kind.SMITHING_TRIM;
        var output=dynamic?RecipeSlotContent.parse(""):RecipeSlotContent.parse(fields.getOrDefault("result",""));
        if(type.equals("minecraft:stonecutting")&&output.id()!=null) {
            try{final long count=Long.parseLong(fields.getOrDefault("count","1"));output=new RecipeSlotContent(output.kind(),output.id(),count,List.of(),count>0?RecipeSlotState.FILLED:RecipeSlotState.INVALID);}catch(NumberFormatException ignored){output=new RecipeSlotContent(output.kind(),output.id(),1,List.of(),RecipeSlotState.INVALID);}
        }
        return Optional.of(new RecipeLayoutModel(type,layout.get().kind(),inputs,output,dynamic,parameters));
    }

    private Optional<RecipeLayoutModel> observedVanilla(final RecipeDefinition recipe,final RecipeLayoutProvider.Kind kind) {
        if(recipe.source().type()!=dev.recipeeditor.registry.SourceType.RUNTIME)return Optional.empty();
        try {
            final var state=com.google.gson.JsonParser.parseString(recipe.rawJson()).getAsJsonObject();
            if(!state.has("ingredients"))return Optional.empty();
            final var ingredients=state.getAsJsonArray("ingredients");
            final var inputs=new ArrayList<RecipeLayoutModel.Input>();
            final var parameters=new LinkedHashMap<String,String>();
            if(kind==RecipeLayoutProvider.Kind.SHAPED) {
                final int width=state.get("width").getAsInt(),height=state.get("height").getAsInt();
                if(width<1||width>3||height<1||height>3||ingredients.size()!=width*height)return Optional.empty();
                for(int index=0;index<9;index++) {
                    final int x=index%3,y=index/3;
                    inputs.add(new RecipeLayoutModel.Input("grid",index,x<width&&y<height?ingredient(ingredients.get(y*width+x)):""));
                }
            } else if(kind==RecipeLayoutProvider.Kind.SHAPELESS) {
                for(int index=0;index<9;index++)
                    inputs.add(new RecipeLayoutModel.Input("shapeless",index,index<ingredients.size()?ingredient(ingredients.get(index)):""));
            } else if(kind==RecipeLayoutProvider.Kind.SMITHING_TRANSFORM||kind==RecipeLayoutProvider.Kind.SMITHING_TRIM) {
                for(final String role:List.of("template","base","addition")) {
                    if(!state.has(role))return Optional.empty();
                    inputs.add(new RecipeLayoutModel.Input(role,0,ingredient(state.get(role))));
                }
            } else {
                if(ingredients.size()!=1)return Optional.empty();
                inputs.add(new RecipeLayoutModel.Input("ingredient",0,ingredient(ingredients.get(0))));
                if(state.has("cookingTime"))parameters.put("Cooking time (ticks)",state.get("cookingTime").toString());
                if(state.has("experience"))parameters.put("Experience",state.get("experience").toString());
            }
            final var output=state.has("result")?state.getAsJsonObject("result").deepCopy():new com.google.gson.JsonObject();
            if(output.has("nbtSnbt"))output.add("nbt",output.get("nbtSnbt"));
            final boolean dynamic=state.has("dynamicResult")&&state.get("dynamicResult").getAsBoolean();
            return Optional.of(new RecipeLayoutModel(recipe.type().displayId(),kind,inputs,
                    RecipeSlotContent.parse(output.has("item")&&!output.get("item").getAsString().equals("minecraft:air")?output.toString():""),
                    dynamic,parameters));
        } catch(RuntimeException unavailable) {
            return Optional.empty();
        }
    }

    private String ingredient(final com.google.gson.JsonElement value) {
        return value.isJsonArray()&&value.getAsJsonArray().isEmpty()?"":value.toString();
    }
}
