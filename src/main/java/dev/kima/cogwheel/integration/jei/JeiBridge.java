package dev.kima.cogwheel.integration.jei;

import dev.kima.cogwheel.recipe.RecipeEntry;
import dev.kima.cogwheel.recipe.RecipeIndex;

import java.util.List;

/**
 * Thin static API between Cogwheel's main code and the JEI plugin classes. This class deliberately
 * does NOT import any JEI types so that {@code RebuildTrigger} can reference it without forcing
 * JVM class-loading of JEI-touching classes when JEI is absent.
 *
 * <p>The plugin ({@link CogwheelJeiPlugin}) and extractor ({@link JeiRecipeExtractor}) do import
 * JEI — they're only loaded when JEI itself loads them (via @JeiPlugin annotation scan).
 */
public final class JeiBridge {
    private static volatile List<RecipeEntry> entries = List.of();

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

    /**
     * Called from {@code RebuildTrigger.rebuild()} after the vanilla-source pass. Merges any
     * cached JEI entries into {@code index}. No-op if JEI hasn't fired yet.
     */
    public static void addToIndex(RecipeIndex index) {
        for (RecipeEntry entry : entries) {
            index.add(entry);
        }
    }

    public static int cachedEntryCount() {
        return entries.size();
    }
}
