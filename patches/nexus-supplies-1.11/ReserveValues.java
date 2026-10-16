package com.dynastymc.settlements;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Supply-only values. Native item tags reload and synchronize to clients with the datapack. */
public final class ReserveValues {
    public static final List<Integer> POINT_VALUES = List.of(144,108,72,64,54,48,40,36,32,27,24,20,18,16,12,9,8,6,5,4,3,2,1);
    private record Rule(TagKey<Item> tag, int points) {}
    private static final Map<ReserveKind, List<Rule>> RULES = new EnumMap<>(ReserveKind.class);
    private static final Map<ReserveKind, TagKey<Item>> EXCLUDED = new EnumMap<>(ReserveKind.class);
    static {
        for (ReserveKind kind : ReserveKind.values()) {
            String path = "reserves/" + kind.name().toLowerCase(Locale.ROOT) + "/";
            RULES.put(kind, POINT_VALUES.stream().map(n -> new Rule(tag(path + n), n)).toList());
            EXCLUDED.put(kind, tag(path + "excluded"));
        }
    }
    private static TagKey<Item> tag(String path) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("dynastysettlements", path));
    }

    public static int points(ReserveKind kind, ItemStack stack) {
        if (stack.isEmpty() || stack.isDamaged() || stack.is(EXCLUDED.get(kind)) || carriesContents(stack)) return 0;
        if (kind == ReserveKind.FOOD && unsafeFood(stack)) return 0;
        // Highest matching tier wins; tags never add together. Work is bounded, with no registry scans.
        for (Rule rule : RULES.get(kind)) if (stack.is(rule.tag)) return rule.points;
        return 0;
    }

    private static boolean carriesContents(ItemStack stack) {
        var container = stack.get(DataComponents.CONTAINER);
        if (container != null && container.nonEmptyItems().iterator().hasNext()) return true;
        var bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (bundle != null && !bundle.isEmpty()) return true;
        var projectiles = stack.get(DataComponents.CHARGED_PROJECTILES);
        if (projectiles != null && !projectiles.isEmpty()) return true;
        var data = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        return data != null && !data.isEmpty() || stack.has(DataComponents.CONTAINER_LOOT);
    }

    private static boolean unsafeFood(ItemStack stack) {
        var food = stack.get(DataComponents.FOOD);
        if (food == null) return false; // Tagged ingredients and whole meals need not be directly edible.
        for (var effect : food.effects()) {
            var applied = effect.effect();
            if (effect.probability() > 0 && applied.getEffect().value().getCategory() == MobEffectCategory.HARMFUL
                    && !applied.getEffect().equals(MobEffects.HUNGER)) return true;
        }
        return false;
    }

    public static String describe(ItemStack stack) {
        if (stack.isEmpty()) return "Select a supply stack to review its value.";
        StringBuilder result = new StringBuilder();
        for (ReserveKind kind : ReserveKind.values()) {
            int value = points(kind, stack);
            if (value > 0) {
                if (!result.isEmpty()) result.append(" | ");
                result.append(kind.name()).append(": ").append(value).append(" points each");
            }
        }
        return result.isEmpty() ? "Not accepted as Nexus supplies; equipment must be undamaged and storage empty." : result.toString();
    }
    private ReserveValues() {}
}
