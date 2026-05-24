package dev.kima.cogwheel.model;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A recipe applied at the node — produces its outputs by consuming its inputs at the chosen
 * parallelism factor.
 *
 * <p>{@link #rpmOverride} is the per-node Create RPM. {@link Optional#empty()} means "use the
 * factory's default RPM"; set to use a custom speed for this node specifically. Has no effect on
 * non-Create recipes (the solver only consults it when looking up Create stress impact).
 */
public record RecipeNode(
        UUID id,
        Vec2 position,
        ResourceLocation recipeId,
        Component title,
        ItemStack icon,
        List<Port> inputs,
        List<Port> outputs,
        int parallelism,
        Optional<Integer> rpmOverride
) implements Node {

    public RecipeNode {
        if (rpmOverride == null) rpmOverride = Optional.empty();
    }

    /** Back-compat constructor for pre-rpm-override callers / saves. */
    public RecipeNode(UUID id, Vec2 position, ResourceLocation recipeId, Component title,
                       ItemStack icon, List<Port> inputs, List<Port> outputs, int parallelism) {
        this(id, position, recipeId, title, icon, inputs, outputs, parallelism, Optional.empty());
    }

    @Override
    public List<Port> bottomPorts() {
        return List.of(new Port(0, PortType.LOOP, "loop", ItemStack.EMPTY));
    }

    @Override
    public RecipeNode withPosition(Vec2 newPosition) {
        return new RecipeNode(id, newPosition, recipeId, title, icon, inputs, outputs, parallelism, rpmOverride);
    }

    public RecipeNode withParallelism(int newParallelism) {
        int clamped = Math.max(1, Math.min(256, newParallelism));
        return new RecipeNode(id, position, recipeId, title, icon, inputs, outputs, clamped, rpmOverride);
    }

    public RecipeNode withRpmOverride(Optional<Integer> newRpm) {
        return new RecipeNode(id, position, recipeId, title, icon, inputs, outputs, parallelism,
                newRpm == null ? Optional.empty() : newRpm);
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
        return new RecipeNode(id, position, recipeId, title, icon, List.copyOf(newInputs), outputs, parallelism, rpmOverride);
    }
}
