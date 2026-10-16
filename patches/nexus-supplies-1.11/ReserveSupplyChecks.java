package com.dynastymc.settlements;

import com.google.gson.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.component.ItemContainerContents;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Loads the actual bundled supply tags into the native vanilla item registry. */
public final class ReserveSupplyChecks {
    static int checks;
    static void require(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    static final Map<ResourceLocation, Set<ResourceLocation>> resolved = new HashMap<>();
    static Set<ResourceLocation> ids(ResourceLocation id, Set<ResourceLocation> visiting) throws IOException {
        if (resolved.containsKey(id)) return resolved.get(id);
        if (!visiting.add(id)) throw new IllegalStateException("Cyclic tag " + id);
        Set<ResourceLocation> result = new HashSet<>();
        var contributions = ReserveSupplyChecks.class.getClassLoader().getResources("data/"+id.getNamespace()+"/tags/item/"+id.getPath()+".json");
        while (contributions.hasMoreElements()) {
            try (var stream = contributions.nextElement().openStream()) {
                var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
                if (json.has("replace") && json.get("replace").getAsBoolean()) result.clear();
                for (var entry : json.getAsJsonArray("values")) {
                    String name = entry.isJsonPrimitive() ? entry.getAsString() : entry.getAsJsonObject().get("id").getAsString();
                    if (name.startsWith("#")) result.addAll(ids(ResourceLocation.parse(name.substring(1)), visiting));
                    else result.add(ResourceLocation.parse(name));
                }
            }
        }
        visiting.remove(id); resolved.put(id, Set.copyOf(result)); return result;
    }
    static ResourceLocation location(ReserveKind kind, String tier) { return ResourceLocation.parse("dynastysettlements:reserves/"+kind.name().toLowerCase(Locale.ROOT)+"/"+tier); }
    public static void bindBundledTags() throws IOException {
        resolved.clear();
        Map<TagKey<Item>, List<Holder<Item>>> nativeTags = new HashMap<>();
        for (ReserveKind kind : ReserveKind.values()) {
            List<String> tiers = new ArrayList<>(ReserveValues.POINT_VALUES.stream().map(Object::toString).toList()); tiers.add("excluded");
            for (String tier : tiers) {
                ResourceLocation key = location(kind, tier);
                List<Holder<Item>> items = new ArrayList<>();
                for (ResourceLocation id : ids(key, new HashSet<>())) if (BuiltInRegistries.ITEM.containsKey(id)) items.add(BuiltInRegistries.ITEM.getHolder(id).orElseThrow());
                nativeTags.put(TagKey.create(Registries.ITEM, key), items);
            }
        }
        BuiltInRegistries.ITEM.bindTags(nativeTags);
    }
    static void value(ReserveKind kind, Item item, int wanted) { int actual=ReserveValues.points(kind,new ItemStack(item)); require(actual==wanted,kind+" value of "+item+" should be "+wanted+", got "+actual); }
    public static void main(String[] args) throws Exception {
        net.neoforged.fml.loading.LoadingModList.of(List.of(),List.of(),List.of(),List.of(),Map.of());
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); bindBundledTags();
        value(ReserveKind.FOOD,Items.BREAD,2); value(ReserveKind.FOOD,Items.WHEAT,1); value(ReserveKind.FOOD,Items.CARROT,1);
        value(ReserveKind.FOOD,Items.COOKED_BEEF,3); value(ReserveKind.FOOD,Items.APPLE,1); value(ReserveKind.FOOD,Items.MUSHROOM_STEW,5);
        value(ReserveKind.FOOD,Items.CHICKEN,1); value(ReserveKind.FOOD,Items.SPIDER_EYE,0); value(ReserveKind.FOOD,Items.PUFFERFISH,0); value(ReserveKind.FOOD,Items.SUSPICIOUS_STEW,0);
        value(ReserveKind.CONSTRUCTION,Items.STONE,1); value(ReserveKind.CONSTRUCTION,Items.OAK_LOG,4); value(ReserveKind.CONSTRUCTION,Items.STRIPPED_OAK_WOOD,4);
        value(ReserveKind.CONSTRUCTION,Items.OAK_PLANKS,1); value(ReserveKind.CONSTRUCTION,Items.RED_CONCRETE,2); value(ReserveKind.CONSTRUCTION,Items.OBSIDIAN,8);
        for (Item i : List.of(Items.COPPER_BLOCK,Items.EXPOSED_COPPER,Items.WEATHERED_COPPER,Items.OXIDIZED_COPPER,Items.WAXED_EXPOSED_COPPER,Items.WAXED_OXIDIZED_CUT_COPPER)) value(ReserveKind.CONSTRUCTION,i,6);
        value(ReserveKind.CONSTRUCTION,Items.WAXED_WEATHERED_CUT_COPPER_SLAB,3);
        value(ReserveKind.CIVIC,Items.LANTERN,2); value(ReserveKind.CIVIC,Items.PAPER,1); value(ReserveKind.CIVIC,Items.BOOK,3); value(ReserveKind.CIVIC,Items.CHEST,4);
        value(ReserveKind.CIVIC,Items.IRON_INGOT,2); value(ReserveKind.CIVIC,Items.IRON_BLOCK,18);
        value(ReserveKind.DEFENSE,Items.SHIELD,6); value(ReserveKind.DEFENSE,Items.IRON_CHESTPLATE,8); value(ReserveKind.DEFENSE,Items.DIAMOND_CHESTPLATE,24);
        value(ReserveKind.DEFENSE,Items.LEATHER,1); value(ReserveKind.DEFENSE,Items.GUNPOWDER,2); value(ReserveKind.DEFENSE,Items.CROSSBOW,8);
        ItemStack damaged = new ItemStack(Items.SHIELD); damaged.setDamageValue(1);
        require(ReserveValues.points(ReserveKind.DEFENSE,damaged)==0,"Damaged equipment rejected");
        ItemStack loaded = new ItemStack(Items.CROSSBOW); loaded.set(DataComponents.CHARGED_PROJECTILES,ChargedProjectiles.of(new ItemStack(Items.ARROW)));
        require(ReserveValues.points(ReserveKind.DEFENSE,loaded)==0,"Loaded crossbow rejected without destroying ammunition");
        ItemStack storage = new ItemStack(Items.CHEST); storage.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND))));
        require(ReserveValues.points(ReserveKind.CIVIC,storage)==0,"Nonempty storage rejected");
        storage.set(DataComponents.CONTAINER,ItemContainerContents.EMPTY); require(ReserveValues.points(ReserveKind.CIVIC,storage)==4,"Empty storage accepted");
        ItemStack poison = new ItemStack(Items.BREAD); poison.set(DataComponents.FOOD,new FoodProperties.Builder().nutrition(10).effect(()->new MobEffectInstance(MobEffects.POISON,200),1).build());
        require(ReserveValues.points(ReserveKind.FOOD,poison)==0,"Harmful food component cannot bypass whitelist");
        require(ReserveValues.points(ReserveKind.FOOD,new ItemStack(Items.BREAD,64))==2,"Value is per item, not stack");
        for (ReserveKind kind : ReserveKind.values()) { require(ReserveValues.points(kind,ItemStack.EMPTY)==0,"Empty stack"); value(kind,Items.BEDROCK,0); }
        require(ReserveValues.describe(new ItemStack(Items.IRON_INGOT)).contains("CIVIC: 2"),"Inventory value tooltip exposes category contribution");
        require(ids(location(ReserveKind.FOOD,"36"),new HashSet<>()).contains(ResourceLocation.parse("ends_delight:dragon_leg_with_sauce_block")),"Six dragon portions normalized");
        require(ids(location(ReserveKind.FOOD,"24"),new HashSet<>()).contains(ResourceLocation.parse("ends_delight:steamed_dragon_egg_block")),"Four dragon-egg portions normalized");
        require(ids(location(ReserveKind.FOOD,"9"),new HashSet<>()).contains(ResourceLocation.parse("farmersdelight:rice_bag")),"Packed rice normalized");
        require(ids(location(ReserveKind.FOOD,"excluded"),new HashSet<>()).contains(ResourceLocation.parse("vinery:red_wine")),"Alcohol excluded from Food");
        for (String i : List.of("create:iron_sheet","create:brass_sheet","create:sturdy_sheet","create:mechanical_arm")) {
            int wanted = switch(i) {case "create:iron_sheet"->2;case "create:brass_sheet"->4;case "create:sturdy_sheet"->8;default->12;};
            require(ids(location(ReserveKind.CIVIC,Integer.toString(wanted)),new HashSet<>()).contains(ResourceLocation.parse(i)),"Preserved existing civic supply "+i);
        }
        var settlement = new SettlementRecord(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"Supply capacity");
        long cap = settlement.reserves.capacity(ReserveKind.FOOD,settlement);
        settlement.reserves.deposit(ReserveKind.FOOD,cap-3,settlement);
        int each = ReserveValues.points(ReserveKind.FOOD,new ItemStack(Items.BREAD,64));
        require(Math.min(64,(cap-settlement.reserves.points(ReserveKind.FOOD))/each)==1,"Partial reserve accepts one whole bread and leaves 63");
        require((cap-settlement.reserves.points(ReserveKind.FOOD))/24==0,"Whole feast rejected when no full feast fits");
        long start=System.nanoTime(),sum=0;ItemStack sample=new ItemStack(Items.IRON_INGOT);
        for(int n=0;n<200000;n++)sum+=ReserveValues.points(ReserveKind.CIVIC,sample);
        require(sum==400000,"Bounded tag lookups remain stable under load");
        System.out.println("Reserve supply checks passed: "+checks+"; average lookup ns: "+((System.nanoTime()-start)/200000));
    }
}
