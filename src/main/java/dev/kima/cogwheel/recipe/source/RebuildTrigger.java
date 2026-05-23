package dev.kima.cogwheel.recipe.source;

import dev.kima.cogwheel.CogwheelConstants;
import dev.kima.cogwheel.integration.jei.JeiBridge;
import dev.kima.cogwheel.recipe.RecipeEntry;
import dev.kima.cogwheel.recipe.RecipeIndex;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RecipesUpdatedEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    /** Cogwheel-internal mirror id → original MC recipe id, so picker rendering can resolve the
     *  original RecipeHolder when JEI is asked to draw a mirror entry. Cleared on each rebuild. */
    private static final Map<ResourceLocation, ResourceLocation> MIRROR_TO_ORIGINAL = new HashMap<>();

    private RebuildTrigger() {}

    public static RecipeIndex index() {
        return INDEX;
    }

    /** Returns the original MC recipe id if {@code entryId} is a Cogwheel mirror entry, else null. */
    public static ResourceLocation getMirrorOriginal(ResourceLocation entryId) {
        return MIRROR_TO_ORIGINAL.get(entryId);
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
        MIRROR_TO_ORIGINAL.clear();
        VanillaRecipeSource.populate(INDEX);
        if (ModList.get().isLoaded("create")) {
            mirrorCreateCategoryAliases(INDEX);
        }
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
     * Create's JEI plugin surfaces {@code create:item_application} recipes inside the Deploying
     * category — a Deployer can perform any right-click application automatically. The underlying
     * MC RecipeType is still {@code create:item_application}, so the JEI category enumerator's
     * {@code createRecipeLookup(deploying)} doesn't pick them up. Mirror them here so the picker
     * surfaces "Brass Casing" (and friends) under both categories the user might browse.
     */
    private static void mirrorCreateCategoryAliases(RecipeIndex index) {
        ResourceLocation itemApp = ResourceLocation.fromNamespaceAndPath("create", "item_application");
        ResourceLocation deploying = ResourceLocation.fromNamespaceAndPath("create", "deploying");
        List<RecipeEntry> toMirror = List.copyOf(index.byType(itemApp));
        int added = 0;
        for (RecipeEntry e : toMirror) {
            ResourceLocation mirrorId = ResourceLocation.fromNamespaceAndPath(
                    "cogwheel_mirror",
                    "deploying/" + sanitizePath(e.id().toString()));
            RecipeEntry mirror = new RecipeEntry(
                    mirrorId, deploying,
                    e.inputs(), e.outputs(),
                    e.inputFluid(), e.outputFluid(),
                    e.processingTimeTicks(),
                    e.displayName());
            index.add(mirror);
            MIRROR_TO_ORIGINAL.put(mirrorId, e.id());
            added++;
        }
        if (added > 0) {
            CogwheelConstants.LOG.info("RecipeIndex: +{} create:item_application recipes mirrored as create:deploying", added);
        }
    }

    private static String sanitizePath(String s) {
        return s.toLowerCase().replaceAll("[^a-z0-9/._-]", "_");
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
