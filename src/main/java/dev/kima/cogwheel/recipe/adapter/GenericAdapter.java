package dev.kima.cogwheel.recipe.adapter;

import dev.kima.cogwheel.recipe.IngredientStack;
import dev.kima.cogwheel.recipe.IngredientStacks;
import dev.kima.cogwheel.recipe.RecipeEntry;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.List;
import java.util.Optional;

/**
 * Universal fallback: never declines a recipe. Uses the {@link Recipe#getIngredients()} +
 * {@link Recipe#getResultItem(HolderLookup.Provider)} pair, which together describe the
 * vast majority of vanilla and mod recipes accurately enough for the planning canvas.
 *
 * <p>Loses information for recipes with:
 * <ul>
 *   <li>processing duration (smelting, Create processing) — Phase 1b's {@code CookingAdapter}
 *       and Phase 2's {@code ProcessingRecipeAdapter} recover this.</li>
 *   <li>fluid inputs/outputs — Phase 2 ({@code ProcessingRecipeAdapter}) recovers these.</li>
 *   <li>byproducts with chance (Create rollable results) — Phase 2 recovers these.</li>
 *   <li>per-slot input counts greater than 1 — currently no recipe surfaces these via
 *       {@code getIngredients()}; mod adapters with their own count semantics override.</li>
 * </ul>
 */
public final class GenericAdapter implements RecipeAdapter {
    @Override
    public boolean matches(RecipeType<?> type) {
        return true;
    }

    @Override
    public Optional<RecipeEntry> adapt(RecipeHolder<?> holder, HolderLookup.Provider registries) {
        Recipe<?> recipe = holder.value();
        ResourceLocation id = holder.id();
        ResourceLocation typeId = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
        if (typeId == null) {
            return Optional.empty();
        }

        List<IngredientStack> rawInputs = recipe.getIngredients().stream()
                .map(IngredientStack::fromIngredient)
                .filter(stack -> !stack.isEmpty())
                .toList();
        List<IngredientStack> inputs = IngredientStacks.groupEquivalents(rawInputs);

        ItemStack result = safeResult(recipe, registries);
        List<ItemStack> outputs = result.isEmpty() ? List.of() : List.of(result);

        return Optional.of(new RecipeEntry(
                id, typeId,
                inputs, outputs,
                Optional.empty(), Optional.empty(),
                0,
                Optional.empty()));
    }

    /**
     * Some "special" recipes (e.g. {@code MapExtendingRecipe}) throw when asked for a result with no
     * input. We catch defensively to keep the index build robust against the long tail.
     */
    private static ItemStack safeResult(Recipe<?> recipe, HolderLookup.Provider registries) {
        try {
            ItemStack result = recipe.getResultItem(registries);
            return result == null ? ItemStack.EMPTY : result;
        } catch (Throwable ignored) {
            return ItemStack.EMPTY;
        }
    }
}
