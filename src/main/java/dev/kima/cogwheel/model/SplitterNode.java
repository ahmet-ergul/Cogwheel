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
        int outputCount,
        Mode mode
) implements Node {
    public static final int MIN_OUTPUTS = 2;
    public static final int MAX_OUTPUTS = 8;
    public static final int DEFAULT_OUTPUTS = 2;

    /** How the splitter is INTENDED to distribute items in-game. Cogwheel's forward-design solver
     *  treats them all as demand-additive for now (Phase 10+ bottleneck-aware solver will respect
     *  the difference). The label is shown in the props panel as planning intent. */
    public enum Mode {
        DEMAND,        // route by what each downstream needs
        ROUND_ROBIN,   // alternate items to each output equally
        PRIORITY       // fill output 0 first, then 1, etc.
    }

    public SplitterNode {
        outputCount = Math.max(MIN_OUTPUTS, Math.min(MAX_OUTPUTS, outputCount));
        if (mode == null) mode = Mode.DEMAND;
    }

    /** Back-compat constructor for older callers / saves that don't specify a mode. */
    public SplitterNode(UUID id, Vec2 position, int outputCount) {
        this(id, position, outputCount, Mode.DEMAND);
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
        return new SplitterNode(id, newPosition, outputCount, mode);
    }

    public SplitterNode withOutputCount(int newCount) {
        return new SplitterNode(id, position, newCount, mode);
    }

    public SplitterNode withMode(Mode newMode) {
        return new SplitterNode(id, position, outputCount, newMode);
    }
}
