package dev.kima.cogwheel.recipe.source;

import dev.kima.cogwheel.CogwheelConstants;
import dev.kima.cogwheel.integration.jei.JeiBridge;
import dev.kima.cogwheel.recipe.RecipeIndex;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RecipesUpdatedEvent;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Owns the single client-side {@link RecipeIndex} instance and rebuilds it whenever the underlying
 * recipe set could have changed:
 *
 * <ul>
 *   <li><b>{@link ClientPlayerNetworkEvent.LoggingIn}</b> — clear the index so a stale one from a
 *       previous world isn't presented. The recipes haven't synced yet here; counts will be 0.</li>
 *   <li><b>{@link RecipesUpdatedEvent}</b> — recipes just arrived from the server; rebuild now.
 *       This is the primary trigger on multiplayer; on singleplayer it fires once during world load.</li>
 *   <li><b>Lazy first-access</b> via {@link #ensureBuilt()} — belt-and-suspenders if neither event
 *       fired (rare, but the cost of an empty index is bad UX).</li>
 *   <li>{@link ClientPlayerNetworkEvent.LoggingOut} — clear, so the index doesn't outlive the
 *       world.</li>
 * </ul>
 */
@EventBusSubscriber(modid = CogwheelConstants.MODID, value = Dist.CLIENT)
public final class RebuildTrigger {
    private static final RecipeIndex INDEX = new RecipeIndex();
    private static final AtomicBoolean BUILT = new AtomicBoolean(false);

    private RebuildTrigger() {}

    public static RecipeIndex index() {
        return INDEX;
    }

    /**
     * If the index hasn't been built yet (e.g. neither event fired), build it now. Safe to call
     * from any client-side context where {@code Minecraft.getInstance().level} is non-null.
     */
    public static void ensureBuilt() {
        if (!BUILT.get()) {
            rebuild();
        }
    }

    private static void rebuild() {
        VanillaRecipeSource.populate(INDEX);
        if (ModList.get().isLoaded("jei")) {
            int before = INDEX.size();
            JeiBridge.addToIndex(INDEX);
            int added = INDEX.size() - before;
            if (added > 0) {
                CogwheelConstants.LOG.info("RecipeIndex: +{} virtual entries from JEI bridge", added);
            }
        }
        BUILT.set(true);
    }

    /**
     * Called from {@link JeiBridge#setEntries} when JEI's runtime finishes loading. If our index is
     * already built (typical case — vanilla recipes synced before JEI's plugin lifecycle
     * completes), splice the cached JEI entries in directly without a full rebuild.
     */
    public static void onJeiContribution() {
        if (BUILT.get()) {
            int before = INDEX.size();
            JeiBridge.addToIndex(INDEX);
            int added = INDEX.size() - before;
            CogwheelConstants.LOG.info("RecipeIndex: +{} virtual entries spliced in by JEI bridge", added);
        }
    }

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        // Recipes haven't synced yet on multiplayer; on integrated/singleplayer they might be ready.
        // Clear first so a stale index from a prior world doesn't leak across.
        INDEX.clear();
        BUILT.set(false);
    }

    @SubscribeEvent
    public static void onRecipesUpdated(RecipesUpdatedEvent event) {
        rebuild();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        INDEX.clear();
        BUILT.set(false);
    }
}
