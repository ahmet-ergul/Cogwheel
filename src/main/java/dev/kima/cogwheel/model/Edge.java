package dev.kima.cogwheel.model;

import java.util.UUID;

/**
 * A connection between two node ports. Default semantics: {@code from} is an output, {@code to} is
 * an input, and {@code fromPort/toPort} index into {@code outputs()}/{@code inputs()}.
 *
 * <p>When {@link #fromBottom} (or {@link #toBottom}) is {@code true}, that side instead references
 * the node's {@link Node#bottomPort()} — used for loop-control attachments where a {@link LoopNode}
 * connects its bottom emitter to other nodes' bottom receivers.
 *
 * @param from       Source node id.
 * @param fromPort   Index into {@code from.outputs()} (or {@code 0} when {@code fromBottom}).
 * @param to         Destination node id.
 * @param toPort     Index into {@code to.inputs()} (or {@code 0} when {@code toBottom}).
 * @param rate       Throughput per the design's chosen unit (items/min by default).
 * @param fromBottom True if {@code fromPort} indexes {@code from.bottomPort()} instead of outputs.
 * @param toBottom   True if {@code toPort} indexes {@code to.bottomPort()} instead of inputs.
 */
public record Edge(UUID from, int fromPort, UUID to, int toPort, double rate,
                   boolean fromBottom, boolean toBottom) {
    /** Back-compat constructor for the original 5-arg shape (regular output→input edges). */
    public Edge(UUID from, int fromPort, UUID to, int toPort, double rate) {
        this(from, fromPort, to, toPort, rate, false, false);
    }

    /** True when this is a bottom-to-bottom loop attachment (both endpoints are bottom ports). */
    public boolean isLoopEdge() {
        return fromBottom && toBottom;
    }
}
