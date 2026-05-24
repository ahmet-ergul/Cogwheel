package dev.kima.cogwheel.model;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.UUID;

/**
 * Gating / iteration control node. Visually a small node with a single bottom-side "loop output"
 * port that attaches via a bottom-to-bottom edge to one or more other nodes' bottom-side "loop
 * input" ports. The {@link #loopCount} parameter says "run the attached subgraph this many
 * times per cycle"; the solver wires this through as a parallelism multiplier on each attached
 * node.
 *
 * <p>No regular input/output ports — flow doesn't pass through a LoopNode, only the gating signal
 * does. The unique geometry (signal exits the <em>bottom</em>, not the right) lives in
 * {@link #bottomPort()}.
 */
public record LoopNode(
        UUID id,
        Vec2 position,
        int loopCount
) implements Node {
    public static final int DEFAULT_LOOPS = 1;
    public static final int MIN_LOOPS = 1;
    public static final int MAX_LOOPS = 256;

    public LoopNode {
        loopCount = Math.max(MIN_LOOPS, Math.min(MAX_LOOPS, loopCount));
    }

    @Override
    public List<Port> inputs() {
        return List.of();
    }

    @Override
    public List<Port> outputs() {
        return List.of();
    }

    /** Two loop-control ports rendered at the top edge of the node (left + right corners). The
     *  user wires the LEFT port to the first gated node's bottom port and the RIGHT port to the
     *  last gated node's bottom port — the segment between them iterates {@link #loopCount} times. */
    @Override
    public List<Port> bottomPorts() {
        return List.of(
                new Port(0, PortType.LOOP, "start", ItemStack.EMPTY),
                new Port(1, PortType.LOOP, "end", ItemStack.EMPTY)
        );
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.CLOCK);
    }

    @Override
    public Component title() {
        return Component.translatable("node.cogwheel.loop");
    }

    @Override
    public LoopNode withPosition(Vec2 newPosition) {
        return new LoopNode(id, newPosition, loopCount);
    }

    public LoopNode withLoopCount(int newCount) {
        return new LoopNode(id, position, newCount);
    }
}
