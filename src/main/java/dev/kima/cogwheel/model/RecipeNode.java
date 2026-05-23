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

    /** Replace the display ItemStack of a specific input port — used when the user picks a
     *  specific variant for a multi-alternative tag-backed ingredient. The label is also updated
     *  to the new item's display name so the props panel + canvas text track the chosen variant. */
    public RecipeNode withInputDisplay(int portIndex, ItemStack newDisplay) {
        if (portIndex < 0 || portIndex >= inputs.size()) return this;
        java.util.List<Port> newInputs = new java.util.ArrayList<>(inputs);
        Port p = newInputs.get(portIndex);
        String newLabel = newDisplay.isEmpty() ? p.label() : newDisplay.getHoverName().getString();
        newInputs.set(portIndex, new Port(p.index(), p.type(), newLabel, newDisplay));
        return new RecipeNode(id, position, recipeId, title, icon, List.copyOf(newInputs), outputs, parallelism);
    }
}
