package dev.kima.cogwheel.model;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * A material entry point — manually imported, mob-dropped, or coming from another factory.
 * One output port, no inputs.
 */
public record SourceNode(
        UUID id,
        Vec2 position,
        ItemStack item,
        Kind kind
) implements Node {

    public enum Kind { MANUAL, EXTERNAL_FACTORY, DROP, INFINITE }

    @Override
    public List<Port> inputs() {
        return List.of();
    }

    @Override
    public List<Port> outputs() {
        return List.of(new Port(0, PortType.ITEM, item.getHoverName().getString(), item));
    }

    @Override
    public ItemStack icon() {
        return item;
    }

    @Override
    public Component title() {
        return Component.translatable("node.cogwheel.source." + kind.name().toLowerCase());
    }

    @Override
    public SourceNode withPosition(Vec2 newPosition) {
        return new SourceNode(id, newPosition, item, kind);
    }
}
