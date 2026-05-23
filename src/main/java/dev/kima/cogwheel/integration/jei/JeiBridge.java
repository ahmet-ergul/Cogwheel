package dev.kima.cogwheel.integration.jei;

import dev.kima.cogwheel.recipe.RecipeEntry;
import dev.kima.cogwheel.recipe.RecipeIndex;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Thin static API between Cogwheel's main code and the JEI plugin classes. This class deliberately
 * does NOT import any JEI types so that {@code RebuildTrigger} and the recipe picker can reference
 * it without forcing JVM class-loading of JEI-touching classes when JEI is absent.
 *
 * <p>The plugin ({@link CogwheelJeiPlugin}), extractor ({@link JeiRecipeExtractor}), and renderer
 * ({@link JeiPickerRenderer}) all import JEI — they're only loaded when JEI itself loads them
 * (via @JeiPlugin annotation scan), or lazily when {@link #tryRenderPickerRow} runs against a
 * non-null runtime.
 */
public final class JeiBridge {
    private static volatile List<RecipeEntry> entries = List.of();

    /** Stored as Object so this file doesn't pull in JEI types. Cast back inside JeiPickerRenderer. */
    private static volatile Object jeiRuntime;

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

    public static int cachedEntryCount() {
        return entries.size();
    }
}
