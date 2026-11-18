package dev.recipeeditor.companionclient;
import dev.recipeeditor.companionprotocol.RuntimeData.*;
import com.google.gson.*;
import java.nio.file.Path;
import java.util.*;
final class RuntimeFixtures {
    Snapshot snapshot(final Path source,final String session,final long generation) {
        final var properties=new JsonObject();final var result=new JsonObject();result.addProperty("item","minecraft:iron_ingot");result.addProperty("count",1);properties.add("result",result);properties.addProperty("cookingTime",200);
        return new Snapshot(1,UUID.randomUUID().toString(),true,new Environment(session,source.toString(),"1.20.1","FORGE","47.4.10","0.1.0",generation,"2026-11-17T00:00:00Z"),List.of(new Registry(Kind.ITEM,"minecraft:iron_ingot","Iron ingot","TestItem",Map.of(),Map.of())),List.of(new Tag(Kind.ITEM,"forge:ingots/iron",List.of("minecraft:iron_ingot")),new Tag(Kind.ITEM,"test:empty",List.of())),List.of(new Recipe("test:smelt","minecraft:smelting","minecraft:smelting","TestRecipe","unit-test",Support.SUPPORTED,properties,"unit fixture, not Minecraft runtime",List.of())),List.of(new Mod("test","1.0","Test")),List.of());
    }
}
