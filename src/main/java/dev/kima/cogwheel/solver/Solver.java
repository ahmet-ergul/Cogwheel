package dev.kima.cogwheel.solver;

import dev.kima.cogwheel.model.Design;
import dev.kima.cogwheel.model.Edge;
import dev.kima.cogwheel.model.MergerNode;
import dev.kima.cogwheel.model.Node;
import dev.kima.cogwheel.model.RecipeNode;
import dev.kima.cogwheel.model.SinkNode;
import dev.kima.cogwheel.model.SourceNode;
import dev.kima.cogwheel.model.SplitterNode;
import dev.kima.cogwheel.recipe.IngredientStack;
import dev.kima.cogwheel.recipe.RecipeEntry;
import dev.kima.cogwheel.recipe.RecipeIndex;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Computes the items/min throughput a {@link Design} would have if run at the current node
 * parallelism settings. Forward-design model: every recipe runs at full parallelism; rates are
 * what the design WOULD need / produce, not what's actually achievable given bottlenecks.
 *
 * <p>Bottleneck-aware backward propagation is Phase 9 polish.
 */
public final class Solver {
    /** Cycle-time floor for "instant" recipes (e.g. vanilla crafting which has 0 ticks). 1s = 20 ticks. */
    private static final double DEFAULT_TICKS_FOR_INSTANT = 20.0;
    private static final double TICKS_PER_MINUTE = 1200.0;

    private Solver() {}

    public static SolverResult solve(Design design, RecipeIndex index) {
        List<Node> topo = topoSort(design);
        if (topo == null) {
            return SolverResult.cycle();
        }

        Map<Edge, Double> edgeRates = new HashMap<>();
        Map<UUID, Double> cyclesPerMin = new HashMap<>();
        Map<Item, Double> unmetInputs = new LinkedHashMap<>();
        Map<Item, Double> finalOutputs = new LinkedHashMap<>();
        Map<Item, Double> unusedOutputs = new LinkedHashMap<>();

        // Pass 1: for each recipe node, compute its demand on each input port and supply on
        // each output. Sources don't add anything to the aggregates — they're treated as infinite
        // suppliers.
        for (Node node : topo) {
            if (node instanceof RecipeNode rn) {
                processRecipeNode(design, index, rn, edgeRates, cyclesPerMin, unmetInputs, unusedOutputs);
            }
        }

        // Pass 2: propagate edge demand through Splitter/Merger nodes in REVERSE topo order, so
        // each logic node sees its downstream demand (already populated by Pass 1) when computing
        // its own upstream demand. Chained logic nodes work because reverse-topo walks leaves first.
        for (int i = topo.size() - 1; i >= 0; i--) {
            Node node = topo.get(i);
            if (node instanceof SplitterNode splitter) {
                processSplitter(design, splitter, edgeRates);
            } else if (node instanceof MergerNode merger) {
                processMerger(design, merger, edgeRates);
            }
        }

        // Sinks aggregate their incoming rates as "final outputs".
        for (Node node : design.nodes()) {
            if (node instanceof SinkNode sn) {
                for (int i = 0; i < sn.inputs().size(); i++) {
                    Edge incoming = findIncomingEdge(design, sn.id(), i);
                    if (incoming != null) {
                        Double rate = edgeRates.get(incoming);
                        if (rate != null && rate > 0) {
                            addItem(finalOutputs, sn.item().getItem(), rate);
                        }
                    }
                }
            } else if (node instanceof SourceNode src) {
                // Sources with unused outputs go to unusedOutputs too — they're items the user
                // marked as available but isn't actually using.
                Edge outgoing = findOutgoingEdge(design, src.id(), 0);
                if (outgoing == null) {
                    // We don't know the "rate" of an unused source — skip.
                }
            }
        }

        return new SolverResult(false, edgeRates, cyclesPerMin, unmetInputs, finalOutputs, unusedOutputs);
    }

    private static void processRecipeNode(Design design, RecipeIndex index, RecipeNode rn,
                                          Map<Edge, Double> edgeRates,
                                          Map<UUID, Double> cyclesPerMin,
                                          Map<Item, Double> unmetInputs,
                                          Map<Item, Double> unusedOutputs) {
        RecipeEntry entry = index.byId(rn.recipeId());
        if (entry == null) {
            // Recipe lookup failed (mod removed, etc.) — record one cycle/min as a fallback so the
            // node still shows up. Better than crashing the whole solve.
            cyclesPerMin.put(rn.id(), 0.0);
            return;
        }

        double ticks = entry.processingTimeTicks() <= 0
                ? DEFAULT_TICKS_FOR_INSTANT
                : entry.processingTimeTicks();
        double cycles = (TICKS_PER_MINUTE / ticks) * rn.parallelism();
        cyclesPerMin.put(rn.id(), cycles);

        // Inputs: each ingredient's count * cycles = demand on input port [i].
        List<IngredientStack> inputs = entry.inputs();
        for (int i = 0; i < inputs.size(); i++) {
            IngredientStack ing = inputs.get(i);
            double demand = ing.count() * cycles;
            Edge incoming = findIncomingEdge(design, rn.id(), i);
            if (incoming != null) {
                edgeRates.merge(incoming, demand, Double::sum);
            } else if (!ing.matchingItems().isEmpty()) {
                addItem(unmetInputs, ing.matchingItems().get(0), demand);
            }
        }

        // Outputs: each output's count * cycles = supply on output port [j].
        List<ItemStack> outputs = entry.outputs();
        for (int j = 0; j < outputs.size(); j++) {
            ItemStack out = outputs.get(j);
            if (out.isEmpty()) continue;
            double supply = out.getCount() * cycles;
            Edge outgoing = findOutgoingEdge(design, rn.id(), j);
            if (outgoing == null) {
                addItem(unusedOutputs, out.getItem(), supply);
            }
            // Outgoing edges' rates are determined by the DOWNSTREAM demand, set when processing
            // that downstream node. We don't write supply to the outgoing edge here.
        }
    }

    /**
     * Splitter aggregates demand from all of its outgoing edges (across all output ports and all
     * consumers per port) into a single demand on its single input edge.
     */
    private static void processSplitter(Design design, SplitterNode splitter, Map<Edge, Double> edgeRates) {
        double sumOutDemand = 0;
        for (Edge e : design.edges()) {
            if (e.from().equals(splitter.id())) {
                sumOutDemand += edgeRates.getOrDefault(e, 0.0);
            }
        }
        Edge inEdge = findIncomingEdge(design, splitter.id(), 0);
        if (inEdge != null && sumOutDemand > 0) {
            edgeRates.put(inEdge, sumOutDemand);
        }
    }

    /**
     * Merger distributes its single output's demand equally across all input ports. (Phase 9e
     * MVP — Phase 9f+ polish: per-input weights.)
     */
    private static void processMerger(Design design, MergerNode merger, Map<Edge, Double> edgeRates) {
        double sumOutDemand = 0;
        for (Edge e : design.edges()) {
            if (e.from().equals(merger.id())) {
                sumOutDemand += edgeRates.getOrDefault(e, 0.0);
            }
        }
        if (sumOutDemand <= 0) return;

        double perInput = sumOutDemand / merger.inputCount();
        for (int j = 0; j < merger.inputCount(); j++) {
            Edge inEdge = findIncomingEdge(design, merger.id(), j);
            if (inEdge != null) {
                edgeRates.put(inEdge, perInput);
            }
        }
    }

    private static Edge findIncomingEdge(Design design, UUID nodeId, int portIndex) {
        for (Edge e : design.edges()) {
            if (e.to().equals(nodeId) && e.toPort() == portIndex) return e;
        }
        return null;
    }

    private static Edge findOutgoingEdge(Design design, UUID nodeId, int portIndex) {
        for (Edge e : design.edges()) {
            if (e.from().equals(nodeId) && e.fromPort() == portIndex) return e;
        }
        return null;
    }

    private static void addItem(Map<Item, Double> map, Item item, double amount) {
        map.merge(item, amount, Double::sum);
    }

    /**
     * Kahn's algorithm. Returns null if the graph contains a cycle (impossible to fully order).
     * Nodes with no edges are still included in arbitrary insertion order.
     */
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

        java.util.ArrayList<Node> result = new java.util.ArrayList<>(design.nodes().size());
        while (!ready.isEmpty()) {
            Node n = ready.pop();
            result.add(n);
            for (Edge e : design.edges()) {
                if (!e.from().equals(n.id())) continue;
                int newDeg = inDegree.get(e.to()) - 1;
                inDegree.put(e.to(), newDeg);
                if (newDeg == 0) {
                    Node target = design.findNode(e.to());
                    if (target != null) ready.add(target);
                }
            }
        }

        return result.size() == design.nodes().size() ? result : null;
    }
}
