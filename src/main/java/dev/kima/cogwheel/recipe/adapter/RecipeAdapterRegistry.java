package dev.kima.cogwheel.recipe.adapter;

import dev.kima.cogwheel.recipe.RecipeEntry;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * First-match-wins dispatcher over {@link RecipeAdapter}s. {@link GenericAdapter} is always
 * registered last and always matches.
 */
public final class RecipeAdapterRegistry {
    private static final RecipeAdapterRegistry INSTANCE = new RecipeAdapterRegistry();

    private final List<RecipeAdapter> adapters = new ArrayList<>();
    private final GenericAdapter fallback = new GenericAdapter();

    private RecipeAdapterRegistry() {}

    public static RecipeAdapterRegistry get() {
        return INSTANCE;
    }

    public void register(RecipeAdapter adapter) {
        adapters.add(adapter);
    }

    public Optional<RecipeEntry> adapt(RecipeHolder<?> holder, HolderLookup.Provider registries) {
        Recipe<?> recipe = holder.value();
        RecipeType<?> type = recipe.getType();
        for (RecipeAdapter adapter : adapters) {
            if (adapter.matches(type)) {
                Optional<RecipeEntry> result = adapter.adapt(holder, registries);
                if (result.isPresent()) {
                    return result;
                }
            }
        }
        return fallback.adapt(holder, registries);
    }
}
