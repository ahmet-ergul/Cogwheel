package dev.kima.cogwheel.recipe;

import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Placeholder for Create-style fluid inputs. Phase 2 ({@code ProcessingRecipeAdapter}) will populate
 * these from Create's {@code FluidIngredient}. Until then, no recipe builds with fluids attached
 * (vanilla recipes don't take fluids on the input side).
 */
public record FluidIngredientStack(FluidStack stack) {
    public static final FluidIngredientStack EMPTY = new FluidIngredientStack(FluidStack.EMPTY);

    public boolean isEmpty() {
        return stack == null || stack.isEmpty();
    }
}
