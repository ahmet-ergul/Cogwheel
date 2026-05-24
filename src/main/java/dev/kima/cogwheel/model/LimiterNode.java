package dev.kima.cogwheel.model;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.UUID;

/**
 * Caps the flow passing through it. Anything the downstream demands above {@link #limitPerMin}
 * is dropped (modeled as "voided" — the upstream still has to supply at least the lower of
 * downstream demand or limit, no more).
 *
 * <p>Wildcard ports — the solver passes whatever item flows through.
 */
public record LimiterNode(
        UUID id,
        Vec2 position,
        double limitPerMin
) implements Node {
    public static final double DEFAULT_LIMIT = 60.0;
    public static final double MIN_LIMIT = 1.0;
    public static final double MAX_LIMIT = 10_000.0;

    public LimiterNode {
        limitPerMin = Math.max(MIN_LIMIT, Math.min(MAX_LIMIT, limitPerMin));
    }

    @Override
    public List<Port> inputs() {
        return List.of(new Port(0, PortType.ITEM, "in", ItemStack.EMPTY));
    }

    @Override
    public List<Port> outputs() {
        return List.of(new Port(0, PortType.ITEM, "out", ItemStack.EMPTY));
    }

    @Override
    public List<Port> bottomPorts() {
        return List.of(new Port(0, PortType.LOOP, "loop", ItemStack.EMPTY));
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.REDSTONE_TORCH);
    }

    @Override
    public Component title() {
        return Component.translatable("node.cogwheel.limiter");
    }

    @Override
    public LimiterNode withPosition(Vec2 newPosition) {
        return new LimiterNode(id, newPosition, limitPerMin);
    }

    public LimiterNode withLimit(double newLimit) {
        return new LimiterNode(id, position, newLimit);
    }
}
