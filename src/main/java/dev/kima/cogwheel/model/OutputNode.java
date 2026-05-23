package dev.kima.cogwheel.model;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.UUID;

/**
 * Marks a flow as an EXPORT from this factory, available for {@link SourceNode}s in OTHER factories
 * to reference via {@link ExternalRef}.
 *
 * <p>1 input port, no outputs — the recipe / source feeds INTO this node and it's effectively
 * the factory's "produces X" terminal. Distinct from {@link SinkNode} (a sink is a local final
 * destination; an output is exposed to the broader factory ecosystem).
 */
public record OutputNode(
        UUID id,
        Vec2 position,
        ItemStack item,
        String exportName
) implements Node {

    @Override
    public List<Port> inputs() {
        String label = exportName.isEmpty() ? item.getHoverName().getString() : exportName;
        return List.of(new Port(0, PortType.ITEM, label, item));
    }

    @Override
    public List<Port> outputs() {
        return List.of();
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.ENDER_CHEST);
    }

    @Override
    public Component title() {
        return Component.literal("→ " + (exportName.isEmpty() ? "Output" : exportName));
    }

    @Override
    public OutputNode withPosition(Vec2 newPosition) {
        return new OutputNode(id, newPosition, item, exportName);
    }

    public OutputNode withExportName(String newName) {
        return new OutputNode(id, position, item, newName == null ? "" : newName);
    }

    public OutputNode withItem(ItemStack newItem) {
        return new OutputNode(id, position, newItem, exportName);
    }
}
