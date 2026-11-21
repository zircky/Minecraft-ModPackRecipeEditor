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
        final var layout=vanilla.find(type);if(layout.isEmpty())return Optional.empty();
        final Map<String,String> fields;
        try{fields=forms.fields(recipe.rawJson());}catch(IllegalArgumentException invalid){return Optional.empty();}
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
}
