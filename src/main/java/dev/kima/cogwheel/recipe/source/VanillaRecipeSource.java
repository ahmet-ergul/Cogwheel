package dev.kima.cogwheel.recipe.source;

import dev.kima.cogwheel.CogwheelConstants;
import dev.kima.cogwheel.recipe.RecipeIndex;
import dev.kima.cogwheel.recipe.adapter.RecipeAdapterRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.crafting.RecipeManager;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pulls every recipe out of the client-side {@link RecipeManager} and feeds them through
 * {@link RecipeAdapterRegistry} into a {@link RecipeIndex}.
 *
 * <p>Recipe-access path (per Phase 1a research against 1.21.1 sources): prefer
 * {@code Minecraft.getInstance().level.getRecipeManager()} since it's the same instance referenced
 * by {@code ClientPacketListener}, falling back to the connection-side manager defensively.
 * Both should be the same object, but the level may be null on the title screen.
 */
public final class VanillaRecipeSource {
    private VanillaRecipeSource() {}

    /**
     * Populates {@code target} with every adaptable recipe currently available client-side. Existing
     * contents are cleared first. Returns the number of recipes successfully adapted.
     */
    public static int populate(RecipeIndex target) {
        Minecraft mc = Minecraft.getInstance();
        RecipeManager rm = resolveRecipeManager(mc);
        ClientLevel level = mc.level;

        if (rm == null || level == null) {
            CogwheelConstants.LOG.warn(
                    "RecipeIndex rebuild skipped: rm={} level={}", rm, level);
            return 0;
        }

        HolderLookup.Provider registries = level.registryAccess();
        long started = System.nanoTime();

        target.clear();
        int adapted = 0, skipped = 0;
        Map<String, Integer> byAdapter = new LinkedHashMap<>();
        var registry = RecipeAdapterRegistry.get();
        for (var holder : rm.getRecipes()) {
            RecipeAdapterRegistry.AttemptResult attempt;
            try {
                attempt = registry.adaptWithName(holder, registries);
            } catch (Throwable t) {
                CogwheelConstants.LOG.debug(
                        "Adapter threw for recipe {} ({}): {}",
                        holder.id(), holder.value().getType(), t.toString());
                attempt = new RecipeAdapterRegistry.AttemptResult("threw:" + t.getClass().getSimpleName(), java.util.Optional.empty());
            }
            if (attempt.result().isPresent()) {
                target.add(attempt.result().get());
                adapted++;
                byAdapter.merge(attempt.adapterName(), 1, Integer::sum);
            } else {
                skipped++;
            }
        }

        long elapsedMs = (System.nanoTime() - started) / 1_000_000;
        CogwheelConstants.LOG.info(
                "RecipeIndex built: {} adapted, {} skipped, {} ms",
                adapted, skipped, elapsedMs);
        CogwheelConstants.LOG.info("Cogwheel adapters: {}", byAdapter);
        return adapted;
    }

    private static RecipeManager resolveRecipeManager(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level != null) {
            return level.getRecipeManager();
        }
        ClientPacketListener connection = mc.getConnection();
        return connection != null ? connection.getRecipeManager() : null;
    }
}
