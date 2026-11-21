package dev.recipeeditor.ui;

import dev.recipeeditor.core.ResourceLocation;
import java.util.*;

/** Display labels are registered by serializer ID; unknown integrations retain their technical ID. */
public final class RecipeTypeDisplayNameResolver {
    private record Names(Map<String,String> type,Map<String,String> machine) { }
    private final Map<String,Names> registered=new LinkedHashMap<>();
    public RecipeTypeDisplayNameResolver() {
        vanilla("crafting_shaped","Shaped Crafting","Форменный крафт","Crafting Table","Верстак");
        vanilla("crafting_shapeless","Shapeless Crafting","Бесформенный крафт","Crafting Table","Верстак");
        vanilla("smelting","Smelting","Плавка","Furnace","Печь");
        vanilla("blasting","Blasting","Плавка в доменной печи","Blast Furnace","Доменная печь");
        vanilla("smoking","Smoking","Копчение","Smoker","Коптильня");
        vanilla("campfire_cooking","Campfire Cooking","Готовка на костре","Campfire","Костёр");
        vanilla("stonecutting","Stonecutting","Обработка камня","Stonecutter","Камнерез");
        vanilla("smithing_transform","Smithing","Кузнечное дело","Smithing Table","Кузнечный стол");
        vanilla("smithing_trim","Smithing Trim","Отделка брони","Smithing Table","Кузнечный стол");
    }
    public void register(final String type,final Map<String,String> names,final Map<String,String> machines) {
        ResourceLocation.parse(type);final var value=new Names(Map.copyOf(names),Map.copyOf(machines));
        if(registered.putIfAbsent(type,value)!=null)throw new IllegalArgumentException("Type labels already registered: "+type);
    }
    public String name(final String type,final String language){final var value=registered.get(type);return value==null?type:localized(value.type(),language,type);}
    public String machine(final String type,final String language){final var value=registered.get(type);return value==null?type:localized(value.machine(),language,type);}
    private String localized(final Map<String,String> names,final String language,final String fallback){return names.getOrDefault(language,names.getOrDefault("en_us",fallback));}
    private void vanilla(final String path,final String en,final String ru,final String enMachine,final String ruMachine){register("minecraft:"+path,Map.of("en_us",en,"ru_ru",ru),Map.of("en_us",enMachine,"ru_ru",ruMachine));}
}
