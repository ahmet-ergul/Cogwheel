package dev.kima.cogwheel.integration.jei;

import dev.kima.cogwheel.recipe.RecipeEntry;
import dev.kima.cogwheel.recipe.RecipeIndex;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

/**
 * Thin static API between Cogwheel's main code and the JEI plugin classes. This class deliberately
 * does NOT import any JEI types so that {@code RebuildTrigger} and the recipe picker can reference
 * it without forcing JVM class-loading of JEI-touching classes when JEI is absent.
 *
 * <p>The plugin ({@link CogwheelJeiPlugin}), enumerator ({@link JeiRecipeEnumerator}), and
 * renderer ({@link JeiPickerRenderer}) all import JEI — they're only loaded when JEI itself loads
 * them (via @JeiPlugin annotation scan), or lazily when {@link #tryRenderPickerRow} runs against
 * a non-null runtime.
 */
public final class JeiBridge {
    private static volatile List<RecipeEntry> entries = List.of();

    /** Stored as Object so this file doesn't pull in JEI types. Cast back inside JeiPickerRenderer. */
    private static volatile Object jeiRuntime;

    /** Cogwheel-entry-id → JEI recipe instance, populated by {@link JeiRecipeEnumerator}. Lets
     *  {@link JeiPickerRenderer} resolve "cogwheel_jei:" entries straight to the JEI recipe object
     *  instead of having to scan the category by UID. Values stored as {@code Object} so the bridge
     *  stays JEI-import-free. */
    private static volatile Map<ResourceLocation, Object> jeiSourcedRecipes = Map.of();

    private JeiBridge() {}

    /**
     * Called from {@link CogwheelJeiPlugin} when JEI's runtime becomes available. We cache the
     * extracted entries AND merge them into the current {@link RecipeIndex} so the editor sees
     * them immediately if it's already populated.
     */
    public static void setEntries(List<RecipeEntry> newEntries) {
        entries = List.copyOf(newEntries);
        // Defer the import-RebuildTrigger call to avoid a static-init cycle:
        // RebuildTrigger → JeiBridge → RebuildTrigger.
        dev.kima.cogwheel.recipe.source.RebuildTrigger.onJeiContribution();
    }

    /** Replace the JEI-sourced recipe instance map (entries built via
     *  {@link JeiRecipeEnumerator}). */
    public static void setJeiSourcedRecipes(Map<ResourceLocation, Object> map) {
        jeiSourcedRecipes = Map.copyOf(map);
    }

    /** Looks up a JEI recipe instance for a Cogwheel entry id; returns null if not JEI-sourced. */
    public static Object lookupJeiSourcedRecipe(ResourceLocation entryId) {
        return jeiSourcedRecipes.get(entryId);
    }

    /** Stash the live IJeiRuntime so the picker can ask it to draw recipe rows. */
    public static void setRuntime(Object runtime) {
        jeiRuntime = runtime;
    }

    public static Object getRuntime() {
        return jeiRuntime;
    }

    /**
     * Called from {@code RebuildTrigger.rebuild()} after the vanilla-source pass. Merges any
     * cached JEI entries into {@code index}. No-op if JEI hasn't fired yet.
     */
    public static void addToIndex(RecipeIndex index) {
        for (RecipeEntry entry : entries) {
            index.add(entry);
        }
    }

    /**
     * Picker-side hook: ask JEI to draw a recipe row inside the given bounds. Returns true if the
     * row was rendered (caller skips fallback layout), false otherwise.
     *
     * <p>If JEI isn't loaded, returns false immediately — the JeiPickerRenderer class itself is
     * never loaded (lazy class-loading inside the if-branch).
     */
    public static boolean tryRenderPickerRow(GuiGraphics graphics,
                                              ResourceLocation entryId,
                                              ResourceLocation typeId,
                                              int x, int y, int w, int h,
                                              double mouseX, double mouseY) {
        if (jeiRuntime == null) return false;
        return JeiPickerRenderer.tryRender(graphics, jeiRuntime, entryId, typeId, x, y, w, h, mouseX, mouseY);
    }

    /**
     * Opens JEI's full recipe screen focused on the given recipe's primary output. Used by the
     * recipe picker's right-click gesture so the user can browse the recipe at full detail in JEI.
     * Returns false (silent) if JEI isn't loaded or the lookup fails.
     */
    public static boolean showRecipeInJei(ResourceLocation typeId, ResourceLocation entryId) {
        if (jeiRuntime == null) return false;
        return JeiRecipeOpener.tryShow(jeiRuntime, typeId, entryId);
    }

    /** Opens JEI showing all recipes that PRODUCE the given item (JEI's "R" gesture). */
    public static boolean showRecipesOfItem(net.minecraft.world.item.ItemStack stack) {
        if (jeiRuntime == null || stack == null || stack.isEmpty()) return false;
        return JeiRecipeOpener.tryShowFocus(jeiRuntime, stack, true);
    }

    /** Opens JEI showing all recipes that CONSUME the given item (JEI's "U" gesture). */
    public static boolean showUsesOfItem(net.minecraft.world.item.ItemStack stack) {
        if (jeiRuntime == null || stack == null || stack.isEmpty()) return false;
        return JeiRecipeOpener.tryShowFocus(jeiRuntime, stack, false);
    }

    public static int cachedEntryCount() {
        return entries.size();
    }
}
