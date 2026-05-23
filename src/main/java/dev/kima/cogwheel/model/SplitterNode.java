package dev.kima.cogwheel.model;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Logic node that splits a single inbound flow into N outbound flows. Wildcard ports — the
 * solver passes whatever item flows through, sized by the upstream supply.
 *
 * <p>Phase 9e: hardcoded equal distribution (each output receives inputRate / outputCount).
 * Phase 9f+ polish: per-output weights / fixed rates.
 */
public record SplitterNode(
        UUID id,
        Vec2 position,
        int outputCount
) implements Node {
    public static final int MIN_OUTPUTS = 2;
    public static final int MAX_OUTPUTS = 8;
    public static final int DEFAULT_OUTPUTS = 2;

    public SplitterNode {
        outputCount = Math.max(MIN_OUTPUTS, Math.min(MAX_OUTPUTS, outputCount));
    }

    @Override
    public List<Port> inputs() {
        return List.of(new Port(0, PortType.ITEM, "in", ItemStack.EMPTY));
    }

    @Override
    public List<Port> outputs() {
        List<Port> result = new ArrayList<>(outputCount);
        for (int i = 0; i < outputCount; i++) {
            result.add(new Port(i, PortType.ITEM, "out " + (i + 1), ItemStack.EMPTY));
        }
        return result;
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.HOPPER);
    }

    @Override
    public Component title() {
        return Component.translatable("node.cogwheel.splitter");
    }

    @Override
    public SplitterNode withPosition(Vec2 newPosition) {
        return new SplitterNode(id, newPosition, outputCount);
    }

    public SplitterNode withOutputCount(int newCount) {
        return new SplitterNode(id, position, newCount);
    }
}
