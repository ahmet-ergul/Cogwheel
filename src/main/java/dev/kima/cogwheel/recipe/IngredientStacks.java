package dev.kima.cogwheel.recipe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility for normalizing a raw list of {@link IngredientStack}s — typically the per-slot output of
 * {@code Recipe.getIngredients()} — into a deduplicated list where ingredient slots that describe
 * the same thing are merged with summed counts.
 *
 * <p>Used by every adapter that derives inputs from {@code Recipe.getIngredients()}: vanilla
 * {@link net.minecraft.world.item.crafting.ShapedRecipe} returns one {@code Ingredient} per filled
 * pattern cell, so a 3×3 of iron nuggets becomes nine separate slots. Without grouping the canvas
 * node renders nine input ports; with grouping it renders one input port with a "×9" badge.
 */
public final class IngredientStacks {
    private IngredientStacks() {}

    /**
     * Returns a new list where stacks sharing the same {@link IngredientStack#groupingKey()} are
     * merged with summed counts. First-occurrence order is preserved. Empty input stacks (and
     * stacks that become empty under grouping — shouldn't happen, defensive) are dropped.
     */
    public static List<IngredientStack> groupEquivalents(List<IngredientStack> raw) {
        Map<Object, IngredientStack> byKey = new LinkedHashMap<>();
        for (IngredientStack stack : raw) {
            if (stack.isEmpty()) continue;
            Object key = stack.groupingKey();
            IngredientStack existing = byKey.get(key);
            if (existing == null) {
                byKey.put(key, stack);
            } else {
                byKey.put(key, existing.withCount(existing.count() + stack.count()));
            }
        }
        return new ArrayList<>(byKey.values());
    }
}
