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

    /** Name used in the histogram + diagnose output for the always-last fallback. */
    public static final String FALLBACK_NAME = "GenericAdapter";

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
        return adaptWithName(holder, registries).result();
    }

    /**
     * Variant of {@link #adapt} that also returns the simple class name of the adapter that
     * produced the entry. Used by {@link dev.kima.cogwheel.recipe.source.VanillaRecipeSource}
     * to build a per-adapter histogram for the rebuild log line.
     */
    public AttemptResult adaptWithName(RecipeHolder<?> holder, HolderLookup.Provider registries) {
        Recipe<?> recipe = holder.value();
        RecipeType<?> type = recipe.getType();
        for (RecipeAdapter adapter : adapters) {
            if (adapter.matches(type)) {
                Optional<RecipeEntry> result = adapter.adapt(holder, registries);
                if (result.isPresent()) {
                    return new AttemptResult(adapter.getClass().getSimpleName(), result);
                }
            }
        }
        return new AttemptResult(FALLBACK_NAME, fallback.adapt(holder, registries));
    }

    /**
     * Runs every registered adapter (plus the fallback) against the recipe regardless of whether
     * earlier ones matched. Used by the {@code /cogwheel debug recipe <id>} command so the user
     * can see exactly why a recipe ends up in the index the way it does — or doesn't.
     */
    public List<DiagnoseAttempt> diagnose(RecipeHolder<?> holder, HolderLookup.Provider registries) {
        List<DiagnoseAttempt> attempts = new ArrayList<>();
        Recipe<?> recipe = holder.value();
        RecipeType<?> type = recipe.getType();
        for (RecipeAdapter adapter : adapters) {
            String name = adapter.getClass().getSimpleName();
            boolean matched = adapter.matches(type);
            if (!matched) {
                attempts.add(new DiagnoseAttempt(name, false, Optional.empty(), null));
                continue;
            }
            try {
                Optional<RecipeEntry> result = adapter.adapt(holder, registries);
                attempts.add(new DiagnoseAttempt(name, true, result, null));
            } catch (Throwable t) {
                attempts.add(new DiagnoseAttempt(name, true, Optional.empty(), t));
            }
        }
        // Fallback always runs.
        try {
            Optional<RecipeEntry> result = fallback.adapt(holder, registries);
            attempts.add(new DiagnoseAttempt(FALLBACK_NAME + " (fallback)", true, result, null));
        } catch (Throwable t) {
            attempts.add(new DiagnoseAttempt(FALLBACK_NAME + " (fallback)", true, Optional.empty(), t));
        }
        return attempts;
    }

    public record AttemptResult(String adapterName, Optional<RecipeEntry> result) {}

    public record DiagnoseAttempt(String adapterName, boolean matchedType, Optional<RecipeEntry> result, Throwable error) {}
}
