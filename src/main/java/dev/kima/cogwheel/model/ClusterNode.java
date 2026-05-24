package dev.kima.cogwheel.model;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A container node holding its own {@link Design} (a recursive subgraph). External I/O ports are
 * derived from the inner design's {@link SourceNode}s (inputs) and {@link SinkNode}s /
 * {@link OutputNode}s (outputs) — anything inside that has no internal source becomes an external
 * input port, anything inside that has no internal consumer becomes an external output port.
 *
 * <ul>
 *   <li>{@link Kind#LOCKED} — auto-built (e.g. a sequenced assembly recipe expanded into its
 *       sub-steps). Inner design is fixed; the user can't edit the contents but can still adjust
 *       the outer cluster's parallelism / position.</li>
 *   <li>{@link Kind#USER} — created via "Group selected" on the canvas. User is free to edit
 *       the inner design after navigating in.</li>
 * </ul>
 *
 * <p>Visual differentiation: ClusterNode renders with a violet border + slightly different header
 * gradient (see {@code NodeRenderer}).
 */
public record ClusterNode(
        UUID id,
        Vec2 position,
        String label,
        Design innerDesign,
        Kind kind,
        int parallelism
) implements Node {

    public enum Kind { USER, LOCKED }

    public ClusterNode {
        if (label == null) label = "Cluster";
        if (kind == null) kind = Kind.USER;
        parallelism = Math.max(1, Math.min(256, parallelism));
    }

    @Override
    public List<Port> inputs() {
        // Every SourceNode inside whose item appears in no internal edge target = external input.
        List<Port> result = new ArrayList<>();
        int idx = 0;
        for (Node n : innerDesign.nodes()) {
            if (n instanceof SourceNode src) {
                result.add(new Port(idx++, PortType.ITEM, src.item().getHoverName().getString(), src.item()));
            }
        }
        return result;
    }

    @Override
    public List<Port> outputs() {
        List<Port> result = new ArrayList<>();
        int idx = 0;
        for (Node n : innerDesign.nodes()) {
            if (n instanceof SinkNode sink) {
                result.add(new Port(idx++, PortType.ITEM, sink.item().getHoverName().getString(), sink.item()));
            } else if (n instanceof OutputNode out) {
                result.add(new Port(idx++, PortType.ITEM, out.item().getHoverName().getString(), out.item()));
            }
        }
        return result;
    }

    @Override
    public List<Port> bottomPorts() {
        return List.of(new Port(0, PortType.LOOP, "loop", ItemStack.EMPTY));
    }

    @Override
    public ItemStack icon() {
        // First output's item makes the most intuitive icon; fall back to a generic mark.
        for (Node n : innerDesign.nodes()) {
            if (n instanceof SinkNode sink && !sink.item().isEmpty()) return sink.item();
            if (n instanceof OutputNode out && !out.item().isEmpty()) return out.item();
        }
        return new ItemStack(Items.CHEST_MINECART);
    }

    @Override
    public Component title() {
        return Component.literal(label);
    }

    @Override
    public ClusterNode withPosition(Vec2 newPosition) {
        return new ClusterNode(id, newPosition, label, innerDesign, kind, parallelism);
    }

    public ClusterNode withInnerDesign(Design newInner) {
        return new ClusterNode(id, position, label, newInner, kind, parallelism);
    }

    public ClusterNode withLabel(String newLabel) {
        return new ClusterNode(id, position, newLabel == null ? "Cluster" : newLabel, innerDesign, kind, parallelism);
    }

    public ClusterNode withParallelism(int newParallelism) {
        return new ClusterNode(id, position, label, innerDesign, kind, newParallelism);
    }

    public ClusterNode withKind(Kind newKind) {
        return new ClusterNode(id, position, label, innerDesign, newKind, parallelism);
    }
}
