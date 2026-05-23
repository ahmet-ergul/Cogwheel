package dev.kima.cogwheel.model;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Logic node that merges N inbound flows into a single outbound flow. Wildcard ports — the
 * solver sums whatever items flow in. (No item-type checking between inputs, since the user
 * controls which streams converge here.)
 *
 * <p>Phase 9e: input demand is distributed equally across input ports (outDemand / inputCount).
 * Phase 9f+ polish: priority/weight per input.
 */
public record MergerNode(
        UUID id,
        Vec2 position,
        int inputCount
) implements Node {
    public static final int MIN_INPUTS = 2;
    public static final int MAX_INPUTS = 8;
    public static final int DEFAULT_INPUTS = 2;

    public MergerNode {
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
        return List.of(new Port(0, PortType.ITEM, "out", ItemStack.EMPTY));
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.DROPPER);
    }

    @Override
    public Component title() {
        return Component.translatable("node.cogwheel.merger");
    }

    @Override
    public MergerNode withPosition(Vec2 newPosition) {
        return new MergerNode(id, newPosition, inputCount);
    }

    public MergerNode withInputCount(int newCount) {
        return new MergerNode(id, position, newCount);
    }
}
