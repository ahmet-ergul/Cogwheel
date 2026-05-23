package dev.kima.cogwheel.client.ui.canvas;

import dev.kima.cogwheel.model.Design;
import dev.kima.cogwheel.model.Edge;
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
 *   <li>The exact same edge (same from/fromPort/to/toPort) must not already exist.</li>
 * </ul>
 *
 * <p>Tag-aware compatibility (an output should also match a tag-backed input if the output is in
 * the tag) is Phase 9c — needs adding a tag/accepted-items field to {@link
 * dev.kima.cogwheel.model.Port}.
 */
public final class EdgeValidation {
    private EdgeValidation() {}

    public static boolean canConnect(Design design, HitTest.PortHit from, HitTest.PortHit to) {
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

        // Edge equality is by from/fromPort/to/toPort; identical endpoints would be a duplicate.
        for (Edge existing : design.edges()) {
            if (existing.from().equals(from.node().id())
                    && existing.fromPort() == from.port().index()
                    && existing.to().equals(to.node().id())
                    && existing.toPort() == to.port().index()) {
                return false;
            }
        }
        return true;
    }

    public static boolean isItemPort(HitTest.PortHit hit) {
        return hit.port().type() == PortType.ITEM;
    }
}
