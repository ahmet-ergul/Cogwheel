package dev.kima.cogwheel.recipe;

import dev.kima.cogwheel.model.Port;
import dev.kima.cogwheel.model.PortType;
import dev.kima.cogwheel.model.RecipeNode;
import dev.kima.cogwheel.model.SourceNode;
import dev.kima.cogwheel.model.Vec2;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Builds {@link RecipeNode}s from {@link RecipeEntry}s. Picks representative items for each port:
 * for tag-based inputs we currently use the first matching item; Phase 9 polish will let the user
 * pin a concrete choice or display the tag itself.
 */
public final class RecipeNodeFactory {
    private RecipeNodeFactory() {}

    public static RecipeNode fromRecipeEntry(RecipeEntry entry, Vec2 position) {
        List<Port> inputs = new ArrayList<>(entry.inputs().size());
        int idx = 0;
        for (IngredientStack input : entry.inputs()) {
            ItemStack display = input.matchingItems().isEmpty()
                    ? ItemStack.EMPTY
                    : new ItemStack(input.matchingItems().get(0));
            String label = input.tag().isPresent()
                    ? "#" + input.tag().get().location()
                    : (display.isEmpty() ? "?" : display.getHoverName().getString());
            inputs.add(new Port(idx++, PortType.ITEM, label, display));
        }

        List<Port> outputs = new ArrayList<>(entry.outputs().size());
        idx = 0;
        for (ItemStack output : entry.outputs()) {
            if (output.isEmpty()) continue;
            outputs.add(new Port(idx++, PortType.ITEM, output.getHoverName().getString(), output));
        }

        ItemStack icon = entry.outputs().isEmpty() ? ItemStack.EMPTY : entry.outputs().get(0);
        Component title = entry.displayName().orElseGet(() -> Component.literal(entry.id().getPath()));

        return new RecipeNode(
                UUID.randomUUID(),
                position,
                entry.id(),
                title,
                icon,
                List.copyOf(inputs),
                List.copyOf(outputs),
                1);
    }

    public static SourceNode source(Item item, Vec2 position) {
        return new SourceNode(UUID.randomUUID(), position, new ItemStack(item), SourceNode.Kind.MANUAL);
    }
}
