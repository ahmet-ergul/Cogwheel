package dev.kima.cogwheel.model;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.UUID;

/**
 * Splits a flow into two outputs based on a single item match. Output 0 ("match") carries
 * {@link #matchItem}; output 1 ("reject") carries everything else.
 *
 * <p>v0.2 caveat: the forward-design solver isn't item-aware, so demand is treated additively
 * the same way as a Splitter. The filter is primarily a planning/documentation node that
 * declares user intent; the in-game contraption configuration determines real flow.
 */
public record FilterNode(
        UUID id,
        Vec2 position,
        ItemStack matchItem
) implements Node {
    public FilterNode {
        if (matchItem == null) matchItem = ItemStack.EMPTY;
    }

    @Override
    public List<Port> inputs() {
        return List.of(new Port(0, PortType.ITEM, "in", ItemStack.EMPTY));
    }

    @Override
    public List<Port> outputs() {
        String matchLabel = matchItem.isEmpty() ? "match" : matchItem.getHoverName().getString();
        return List.of(
                new Port(0, PortType.ITEM, matchLabel, matchItem),
                new Port(1, PortType.ITEM, "reject", ItemStack.EMPTY));
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.COMPARATOR);
    }

    @Override
    public Component title() {
        return Component.translatable("node.cogwheel.filter");
    }

    @Override
    public FilterNode withPosition(Vec2 newPosition) {
        return new FilterNode(id, newPosition, matchItem);
    }

    public FilterNode withMatchItem(ItemStack newItem) {
        return new FilterNode(id, position, newItem == null ? ItemStack.EMPTY : newItem);
    }
}
