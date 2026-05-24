package dev.kima.cogwheel.client.ui.canvas;

import dev.kima.cogwheel.model.Design;
import dev.kima.cogwheel.model.Edge;
import dev.kima.cogwheel.model.Node;
import dev.kima.cogwheel.model.PortType;
import net.minecraft.world.item.ItemStack;

/**
 * Rules that decide whether the user's drag-from-port-to-port gesture is allowed to materialize
 * into an {@link Edge}.
 *
 * <ul>
 *   <li>Source must be an output, target must be an input.</li>
 *   <li>Source and target must be on different nodes (no self-loops in v1).</li>
 *   <li>Port types must match: ITEM↔ITEM, FLUID↔FLUID — never cross-type.</li>
 *   <li>For ITEM ports, the upstream output's item must match the downstream input's item —
 *       prevents nonsense like "potion → iron nugget".</li>
 *   <li><b>1-to-1 cardinality</b>: an output port may have at most one outgoing edge, and an
 *       input port may have at most one incoming edge. Fan-out / fan-in must go through an
 *       explicit Splitter or Merger node.</li>
 * </ul>
 *
 * <p>Tag-aware compatibility (an output should also match a tag-backed input if the output is in
 * the tag) is Phase 9c — needs adding a tag/accepted-items field to {@link
 * dev.kima.cogwheel.model.Port}.
 */
public final class EdgeValidation {
    private EdgeValidation() {}

    public static boolean canConnect(Design design, HitTest.PortHit from, HitTest.PortHit to) {
        // Bottom-port loop edges use their own rules: bottom↔bottom only, between a LoopNode
        // (which has bottomPort) and any other node that has bottomPort.
        if (from.bottom() || to.bottom()) {
            return canConnectLoop(design, from, to);
        }
        if (!from.output()) return false;
        if (to.output()) return false;
        if (from.node().id().equals(to.node().id())) return false;
        if (from.port().type() != to.port().type()) return false;

        if (from.port().type() == PortType.ITEM) {
            ItemStack outputItem = from.port().display();
            ItemStack inputItem = to.port().display();
            if (!outputItem.isEmpty() && !inputItem.isEmpty()
                    && outputItem.getItem() != inputItem.getItem()) {
                return false;
            }
        }

        // 1-to-1 cardinality: reject if either endpoint is already engaged.
        for (Edge existing : design.edges()) {
            if (existing.fromBottom() || existing.toBottom()) continue;
            if (existing.from().equals(from.node().id()) && existing.fromPort() == from.port().index()) {
                return false; // output port already has an outgoing edge
            }
            if (existing.to().equals(to.node().id()) && existing.toPort() == to.port().index()) {
                return false; // input port already has an incoming edge
            }
        }
        return true;
    }

    /** Bottom-port edge rules: both sides must be bottom ports, types must match (both LOOP),
     *  exactly one side must be a {@link dev.kima.cogwheel.model.LoopNode} (the signal source),
     *  and each receiver bottom port may have at most one incoming attachment. */
    private static boolean canConnectLoop(Design design, HitTest.PortHit from, HitTest.PortHit to) {
        if (!from.bottom() || !to.bottom()) return false;
        if (from.node().id().equals(to.node().id())) return false;
        if (from.port().type() != PortType.LOOP || to.port().type() != PortType.LOOP) return false;

        boolean fromIsLoop = from.node() instanceof dev.kima.cogwheel.model.LoopNode;
        boolean toIsLoop = to.node() instanceof dev.kima.cogwheel.model.LoopNode;
        // Exactly one side must be the LoopNode (the emitter). Two LoopNodes or zero LoopNodes
        // wouldn't have meaningful semantics.
        if (fromIsLoop == toIsLoop) return false;

        // 1-to-1 cardinality, both sides:
        //  - The receiver bottom port may have at most one incoming loop edge (per (nodeId, portIndex)).
        //  - The LoopNode's two ports (0=start, 1=end) may each have at most one outgoing loop edge.
        java.util.UUID receiverId = fromIsLoop ? to.node().id() : from.node().id();
        int receiverPort = fromIsLoop ? to.port().index() : from.port().index();
        java.util.UUID loopId = fromIsLoop ? from.node().id() : to.node().id();
        int loopPort = fromIsLoop ? from.port().index() : to.port().index();
        for (Edge existing : design.edges()) {
            if (!existing.isLoopEdge()) continue;
            Node exFrom = design.findNode(existing.from());
            Node exTo = design.findNode(existing.to());
            if (exFrom == null || exTo == null) continue;
            boolean exFromIsLoop = exFrom instanceof dev.kima.cogwheel.model.LoopNode;
            java.util.UUID exLoop = exFromIsLoop ? existing.from() : existing.to();
            int exLoopPort = exFromIsLoop ? existing.fromPort() : existing.toPort();
            java.util.UUID exReceiver = exFromIsLoop ? existing.to() : existing.from();
            int exReceiverPort = exFromIsLoop ? existing.toPort() : existing.fromPort();
            if (exReceiver.equals(receiverId) && exReceiverPort == receiverPort) return false;
            if (exLoop.equals(loopId) && exLoopPort == loopPort) return false;
        }
        return true;
    }

    public static boolean isItemPort(HitTest.PortHit hit) {
        return hit.port().type() == PortType.ITEM;
    }
}
