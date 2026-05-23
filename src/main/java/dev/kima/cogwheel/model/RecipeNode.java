package dev.kima.cogwheel.model;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * A recipe applied at the node — produces its outputs by consuming its inputs at the chosen
 * parallelism factor.
 *
 * <p>For Phase 3 we hold pre-baked Port lists and don't try to derive them from the live
 * RecipeIndex. Phase 4 will introduce a builder that takes a {@code RecipeEntry} and computes the
 * port list from the recipe's inputs/outputs.
 */
public record RecipeNode(
        UUID id,
        Vec2 position,
        ResourceLocation recipeId,
        Component title,
        ItemStack icon,
        List<Port> inputs,
        List<Port> outputs,
        int parallelism
) implements Node {
    @Override
    public RecipeNode withPosition(Vec2 newPosition) {
        return new RecipeNode(id, newPosition, recipeId, title, icon, inputs, outputs, parallelism);
    }

    public RecipeNode withParallelism(int newParallelism) {
        int clamped = Math.max(1, Math.min(256, newParallelism));
        return new RecipeNode(id, position, recipeId, title, icon, inputs, outputs, clamped);
    }
}
