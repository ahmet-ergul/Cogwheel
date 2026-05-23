package dev.kima.cogwheel.model;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A material entry point — manually imported, mob-dropped, coming from another factory, etc.
 * One output port, no inputs.
 *
 * <p>{@link Kind#EXTERNAL_FACTORY} sources additionally carry an {@link ExternalRef} pointing at a
 * specific {@link OutputNode} in another {@link Factory}. The referenced factory's output node
 * provides the item identity; if the reference doesn't resolve (factory or output deleted), the
 * source renders as "missing" but still keeps its own {@link #item} as a fallback.
 */
public record SourceNode(
        UUID id,
        Vec2 position,
        ItemStack item,
        Kind kind,
        Optional<ExternalRef> externalRef
) implements Node {

    public enum Kind { MANUAL, EXTERNAL_FACTORY, DROP, INFINITE }

    /** Convenience constructor without external ref (pre-Phase-9f sources). */
    public SourceNode(UUID id, Vec2 position, ItemStack item, Kind kind) {
        this(id, position, item, kind, Optional.empty());
    }

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
        return new SourceNode(id, newPosition, item, kind, externalRef);
    }

    public SourceNode withKind(Kind newKind) {
        return new SourceNode(id, position, item, newKind, externalRef);
    }

    public SourceNode withItem(ItemStack newItem) {
        return new SourceNode(id, position, newItem, kind, externalRef);
    }

    public SourceNode withExternalRef(ExternalRef ref) {
        return new SourceNode(id, position, item, kind, Optional.of(ref));
    }

    public SourceNode clearExternalRef() {
        return new SourceNode(id, position, item, kind, Optional.empty());
    }
}
