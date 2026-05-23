package dev.kima.cogwheel.recipe;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cogwheel's in-memory, queryable view of every recipe in the current world's recipe manager.
 *
 * <p>Built by {@link dev.kima.cogwheel.recipe.source.VanillaRecipeSource} on world login /
 * recipe-sync events (see {@link dev.kima.cogwheel.recipe.source.RebuildTrigger}). Indices are
 * rebuilt wholesale on each trigger — recipe sets in modded packs are large but bounded
 * (~10k entries) and rebuild is cheap compared to maintenance.
 */
public final class RecipeIndex {
    private final Map<ResourceLocation, RecipeEntry> byId = new Object2ObjectOpenHashMap<>();
    private final Map<ResourceLocation, List<RecipeEntry>> byType = new Object2ObjectOpenHashMap<>();
    private final Map<Item, List<RecipeEntry>> producing = new HashMap<>();
    private final Map<Item, List<RecipeEntry>> consuming = new HashMap<>();

    public void clear() {
        byId.clear();
        byType.clear();
        producing.clear();
        consuming.clear();
    }

    public void add(RecipeEntry entry) {
        byId.put(entry.id(), entry);
        byType.computeIfAbsent(entry.typeId(), k -> new ArrayList<>()).add(entry);

        for (ItemStack output : entry.outputs()) {
            if (output.isEmpty()) continue;
            producing.computeIfAbsent(output.getItem(), k -> new ArrayList<>()).add(entry);
        }
        for (IngredientStack input : entry.inputs()) {
            if (input.isEmpty()) continue;
            for (Item item : input.matchingItems()) {
                consuming.computeIfAbsent(item, k -> new ArrayList<>()).add(entry);
            }
        }
    }

    public int size() {
        return byId.size();
    }

    public Collection<RecipeEntry> all() {
        return Collections.unmodifiableCollection(byId.values());
    }

    public RecipeEntry byId(ResourceLocation id) {
        return byId.get(id);
    }

    public List<RecipeEntry> byType(ResourceLocation typeId) {
        return byType.getOrDefault(typeId, List.of());
    }

    public List<RecipeEntry> waysToProduce(Item item) {
        return producing.getOrDefault(item, List.of());
    }

    public List<RecipeEntry> usesOf(Item item) {
        return consuming.getOrDefault(item, List.of());
    }

    /**
     * Returns the union of items that appear as either an input or an output of any indexed recipe.
     * Used by the toolbox to populate its item list — items with no recipes wouldn't be useful nodes.
     */
    public Set<Item> indexedItems() {
        Set<Item> result = new HashSet<>(producing.size() + consuming.size());
        result.addAll(producing.keySet());
        result.addAll(consuming.keySet());
        return result;
    }
}
