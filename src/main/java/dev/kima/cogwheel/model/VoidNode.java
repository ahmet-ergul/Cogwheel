package dev.kima.cogwheel.model;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Logic node that accepts {@link #inputCount} wildcard inputs and produces nothing — anything
 * routed here is discarded. Useful for explicitly modeling "the rest of this overflow is wasted".
 *
 * <p>Solver treats incoming demand as the upstream's supply with no propagation back: each input
 * edge gets whatever rate flows into it; the void absorbs it with no demand pulled.
 */
public record VoidNode(
        UUID id,
        Vec2 position,
        int inputCount
) implements Node {
    public static final int MIN_INPUTS = 1;
    public static final int MAX_INPUTS = 8;
    public static final int DEFAULT_INPUTS = 2;

    public VoidNode {
        inputCount = Math.max(MIN_INPUTS, Math.min(MAX_INPUTS, inputCount));
    }

    @Override
    public List<Port> inputs() {
        List<Port> result = new ArrayList<>(inputCount);
        for (int i = 0; i < inputCount; i++) {
            result.add(new Port(i, PortType.ITEM, "in " + (i + 1), ItemStack.EMPTY));
        }
        return result;
    }

    @Override
    public List<Port> outputs() {
        return List.of();
    }

    @Override
    public List<Port> bottomPorts() {
        return List.of(new Port(0, PortType.LOOP, "loop", ItemStack.EMPTY));
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.LAVA_BUCKET);
    }

    @Override
    public Component title() {
        return Component.translatable("node.cogwheel.void");
    }

    @Override
    public VoidNode withPosition(Vec2 newPosition) {
        return new VoidNode(id, newPosition, inputCount);
    }

    public VoidNode withInputCount(int newCount) {
        return new VoidNode(id, position, newCount);
    }
}
