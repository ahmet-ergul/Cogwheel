package dev.kima.cogwheel.recipe.adapter;

import dev.kima.cogwheel.recipe.IngredientStack;
import dev.kima.cogwheel.recipe.IngredientStacks;
import dev.kima.cogwheel.recipe.RecipeEntry;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.List;
import java.util.Optional;

/**
 * Recovers {@code cookingTime} (ticks) for smelting / blasting / smoking / campfire recipes —
 * information the {@link GenericAdapter} loses.
 */
public final class CookingAdapter implements RecipeAdapter {
    @Override
    public boolean matches(RecipeType<?> type) {
        return type == RecipeType.SMELTING
                || type == RecipeType.BLASTING
                || type == RecipeType.SMOKING
                || type == RecipeType.CAMPFIRE_COOKING;
    }

    @Override
    public Optional<RecipeEntry> adapt(RecipeHolder<?> holder, HolderLookup.Provider registries) {
        if (!(holder.value() instanceof AbstractCookingRecipe cooking)) {
            return Optional.empty();
        }
        ResourceLocation id = holder.id();
        ResourceLocation typeId = BuiltInRegistries.RECIPE_TYPE.getKey(cooking.getType());
        if (typeId == null) {
            return Optional.empty();
        }

        List<IngredientStack> rawInputs = cooking.getIngredients().stream()
                .map(IngredientStack::fromIngredient)
                .filter(stack -> !stack.isEmpty())
                .toList();
        List<IngredientStack> inputs = IngredientStacks.groupEquivalents(rawInputs);

        ItemStack result = cooking.getResultItem(registries);
        List<ItemStack> outputs = result == null || result.isEmpty() ? List.of() : List.of(result);

        return Optional.of(new RecipeEntry(
                id, typeId,
                inputs, outputs,
                Optional.empty(), Optional.empty(),
                cooking.getCookingTime(),
                Optional.empty()));
    }
}
