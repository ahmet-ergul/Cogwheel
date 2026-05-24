package dev.kima.cogwheel.solver;

import dev.kima.cogwheel.model.Edge;
import net.minecraft.world.item.Item;

import java.util.Map;
import java.util.UUID;

/**
 * Output of {@link Solver#solve}. All rate values are items per minute.
 *
 * @param hasCycle      true if the graph contains a cycle (no further fields are reliable in this case).
 * @param edgeRates     for each edge, how many items/min flow through it (from the downstream recipe's demand).
 * @param nodeCyclesPerMin for each RecipeNode, how many recipe cycles per minute it runs at its current parallelism.
 * @param unmetInputs   items required by recipe nodes whose input ports have no incoming edge, keyed by item.
 * @param finalOutputs  items reaching SinkNodes, keyed by item.
 * @param unusedOutputs items produced by recipe nodes whose output ports have no outgoing edge, keyed by item.
 */
public record SolverResult(
        boolean hasCycle,
        Map<Edge, Double> edgeRates,
        Map<UUID, Double> nodeCyclesPerMin,
        Map<Item, Double> unmetInputs,
        Map<Item, Double> finalOutputs,
        Map<Item, Double> unusedOutputs,
        Map<UUID, Double> nodeSuDraw,
        double totalSu
) {
    /** Back-compat constructor for callers that don't care about SU. */
    public SolverResult(boolean hasCycle, Map<Edge, Double> edgeRates,
                         Map<UUID, Double> nodeCyclesPerMin, Map<Item, Double> unmetInputs,
                         Map<Item, Double> finalOutputs, Map<Item, Double> unusedOutputs) {
        this(hasCycle, edgeRates, nodeCyclesPerMin, unmetInputs, finalOutputs, unusedOutputs,
                Map.of(), 0.0);
    }

    public static SolverResult cycle() {
        return new SolverResult(true, Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                Map.of(), 0.0);
    }
}
