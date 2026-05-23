package dev.kima.cogwheel.model;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * The final destination of a flow — what the factory ultimately produces. One input, no outputs.
 */
public record SinkNode(
        UUID id,
        Vec2 position,
        ItemStack item
) implements Node {

    @Override
    public List<Port> inputs() {
        return List.of(new Port(0, PortType.ITEM, item.getHoverName().getString(), item));
    }

    @Override
    public List<Port> outputs() {
        return List.of();
    }

    @Override
    public ItemStack icon() {
        return item;
    }

    @Override
    public Component title() {
        return Component.translatable("node.cogwheel.sink");
    }

    @Override
    public SinkNode withPosition(Vec2 newPosition) {
        return new SinkNode(id, newPosition, item);
    }
}
