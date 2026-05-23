package dev.kima.cogwheel.recipe.adapter;

import dev.kima.cogwheel.recipe.RecipeEntry;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.Optional;

/**
 * Translates a raw {@link Recipe} into Cogwheel's normalized {@link RecipeEntry}.
 *
 * <p>The {@link RecipeAdapterRegistry} walks adapters in registration order; the first one that
 * matches the recipe's type wins. {@link GenericAdapter} is always last and always matches — it
 * gets the 90% case for free using {@code Recipe.getIngredients()} + {@code Recipe.getResultItem()}.
 */
public interface RecipeAdapter {
    boolean matches(RecipeType<?> type);

    Optional<RecipeEntry> adapt(RecipeHolder<?> holder, HolderLookup.Provider registries);
}
