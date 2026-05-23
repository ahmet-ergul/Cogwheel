package dev.kima.cogwheel.client.ui.canvas;

import dev.kima.cogwheel.model.Design;
import dev.kima.cogwheel.model.Edge;
import dev.kima.cogwheel.model.FilterNode;
import dev.kima.cogwheel.model.LimiterNode;
import dev.kima.cogwheel.model.MergerNode;
import dev.kima.cogwheel.model.Node;
import dev.kima.cogwheel.model.Port;
import dev.kima.cogwheel.model.SplitterNode;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Walks the design in topological order to figure out what item should be DISPLAYED at each port,
 * even when the port's declared display is empty (which is the wildcard case for Splitter and
 * Merger).
 *
 * <p>Rules:
 * <ul>
 *   <li>Wildcard input port + incoming edge → adopt upstream output's effective display.</li>
 *   <li>Splitter: all output ports inherit the input port's effective display.</li>
 *   <li>Merger: output port inherits the first non-empty input port display.</li>
 * </ul>
 *
 * <p>Non-logic node ports (Source/Sink/Recipe) keep their declared display unchanged — the
 * resolver only ever fills wildcards, never overrides typed ports. If the graph has a cycle, the
 * resolver bails out and returns base-port displays for everything.
 */
public final class PortDisplayResolver {
    private final Map<UUID, ItemStack[]> inputDisplays = new HashMap<>();
    private final Map<UUID, ItemStack[]> outputDisplays = new HashMap<>();

    public PortDisplayResolver(Design design) {
        // Step 1: seed with base port displays.
        for (Node node : design.nodes()) {
            List<Port> ins = node.inputs();
            ItemStack[] inputs = new ItemStack[ins.size()];
            for (int i = 0; i < ins.size(); i++) {
                inputs[i] = ins.get(i).display();
            }
            inputDisplays.put(node.id(), inputs);

            List<Port> outs = node.outputs();
            ItemStack[] outputs = new ItemStack[outs.size()];
            for (int i = 0; i < outs.size(); i++) {
                outputs[i] = outs.get(i).display();
            }
            outputDisplays.put(node.id(), outputs);
        }

        // Step 2: topo-sort the graph. If it has a cycle, leave displays at their base values.
        List<Node> topo = topoSort(design);
        if (topo == null) return;

        // Step 3: walk forward, filling wildcards.
        for (Node node : topo) {
            propagateInputs(design, node);
            if (node instanceof SplitterNode splitter) {
                propagateSplitter(splitter);
            } else if (node instanceof MergerNode merger) {
                propagateMerger(merger);
            } else if (node instanceof LimiterNode limiter) {
                propagatePassthrough(limiter.id());
            } else if (node instanceof FilterNode filter) {
                propagateFilter(filter);
            }
        }
    }

    /** Limiter is 1-in / 1-out passthrough: output adopts the input's effective item. */
    private void propagatePassthrough(UUID nodeId) {
        ItemStack[] inputs = inputDisplays.get(nodeId);
        ItemStack[] outputs = outputDisplays.get(nodeId);
        if (inputs == null || outputs == null || inputs.length == 0 || outputs.length == 0) return;
        ItemStack inDisp = inputs[0];
        if (inDisp == null || inDisp.isEmpty()) return;
        if (outputs[0] == null || outputs[0].isEmpty()) outputs[0] = inDisp;
    }

    /** Filter: out[0] keeps the configured matchItem (already set in Port.display); out[1] (reject)
     *  inherits the input. If matchItem isn't set yet, both outputs inherit. */
    private void propagateFilter(FilterNode filter) {
        ItemStack[] inputs = inputDisplays.get(filter.id());
        ItemStack[] outputs = outputDisplays.get(filter.id());
        if (inputs == null || outputs == null || inputs.length == 0) return;
        ItemStack inDisp = inputs[0];
        if (inDisp == null || inDisp.isEmpty()) return;
        // Reject port (index 1) is wildcard — inherits input.
        if (outputs.length > 1 && (outputs[1] == null || outputs[1].isEmpty())) outputs[1] = inDisp;
        // Match port (index 0) is wildcard ONLY if no matchItem set yet — fall back to input.
        if (outputs.length > 0 && (outputs[0] == null || outputs[0].isEmpty())) outputs[0] = inDisp;
    }

    public ItemStack inputDisplay(UUID nodeId, int portIndex) {
        return get(inputDisplays, nodeId, portIndex);
    }

    public ItemStack outputDisplay(UUID nodeId, int portIndex) {
        return get(outputDisplays, nodeId, portIndex);
    }

    private static ItemStack get(Map<UUID, ItemStack[]> map, UUID id, int portIndex) {
        ItemStack[] arr = map.get(id);
        if (arr == null || portIndex < 0 || portIndex >= arr.length) return ItemStack.EMPTY;
        ItemStack value = arr[portIndex];
        return value == null ? ItemStack.EMPTY : value;
    }

    private void propagateInputs(Design design, Node node) {
        ItemStack[] inputs = inputDisplays.get(node.id());
        if (inputs == null) return;
        for (int i = 0; i < inputs.length; i++) {
            if (!inputs[i].isEmpty()) continue;
            Edge incoming = findIncomingEdge(design, node.id(), i);
            if (incoming == null) continue;
            ItemStack[] upOut = outputDisplays.get(incoming.from());
            if (upOut == null || incoming.fromPort() < 0 || incoming.fromPort() >= upOut.length) continue;
            ItemStack upstream = upOut[incoming.fromPort()];
            if (upstream != null && !upstream.isEmpty()) {
                inputs[i] = upstream;
            }
        }
    }

    private void propagateSplitter(SplitterNode splitter) {
        ItemStack[] inputs = inputDisplays.get(splitter.id());
        ItemStack[] outputs = outputDisplays.get(splitter.id());
        if (inputs == null || outputs == null || inputs.length == 0) return;
        ItemStack inDisp = inputs[0];
        if (inDisp.isEmpty()) return;
        for (int j = 0; j < outputs.length; j++) {
            if (outputs[j] == null || outputs[j].isEmpty()) {
                outputs[j] = inDisp;
            }
        }
    }

    private void propagateMerger(MergerNode merger) {
        ItemStack[] inputs = inputDisplays.get(merger.id());
        ItemStack[] outputs = outputDisplays.get(merger.id());
        if (inputs == null || outputs == null || outputs.length == 0) return;
        for (ItemStack in : inputs) {
            if (in != null && !in.isEmpty()) {
                if (outputs[0] == null || outputs[0].isEmpty()) {
                    outputs[0] = in;
                }
                return;
            }
        }
    }

    private static Edge findIncomingEdge(Design design, UUID nodeId, int portIndex) {
        for (Edge e : design.edges()) {
            if (e.to().equals(nodeId) && e.toPort() == portIndex) return e;
        }
        return null;
    }

    /** Kahn's-algorithm topo sort. Returns null on cycle. Copy of {@code Solver.topoSort} — kept local
     *  to avoid a render-time dependency on the solver package. */
    private static List<Node> topoSort(Design design) {
        Map<UUID, Integer> inDegree = new HashMap<>();
        for (Node n : design.nodes()) inDegree.put(n.id(), 0);
        for (Edge e : design.edges()) {
            Integer cur = inDegree.get(e.to());
            if (cur != null) inDegree.put(e.to(), cur + 1);
        }

        Deque<Node> ready = new ArrayDeque<>();
        for (Node n : design.nodes()) {
            if (inDegree.get(n.id()) == 0) ready.add(n);
        }

        List<Node> result = new ArrayList<>(design.nodes().size());
        while (!ready.isEmpty()) {
            Node n = ready.pop();
            result.add(n);
            for (Edge e : design.edges()) {
                if (!e.from().equals(n.id())) continue;
                Integer cur = inDegree.get(e.to());
                if (cur == null) continue;
                int next = cur - 1;
                inDegree.put(e.to(), next);
                if (next == 0) {
                    Node target = design.findNode(e.to());
                    if (target != null) ready.add(target);
                }
            }
        }

        return result.size() == design.nodes().size() ? result : null;
    }
}
