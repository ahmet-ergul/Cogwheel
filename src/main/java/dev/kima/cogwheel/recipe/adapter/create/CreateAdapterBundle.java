package dev.kima.cogwheel.recipe.adapter.create;

import dev.kima.cogwheel.recipe.adapter.RecipeAdapterRegistry;

/**
 * Single entry-point that registers all Create-specific adapters. This class — and everything it
 * imports — only loads when {@code ModList.isLoaded("create")} returns true in
 * {@link dev.kima.cogwheel.Cogwheel#registerAdapters()}. Do not reference from elsewhere.
 */
public final class CreateAdapterBundle {
    private CreateAdapterBundle() {}

    public static void register(RecipeAdapterRegistry registry) {
        registry.register(new ProcessingRecipeAdapter());
        // Sequenced Assembly intentionally deferred — see ProcessingRecipeAdapter's class doc.
    }
}
