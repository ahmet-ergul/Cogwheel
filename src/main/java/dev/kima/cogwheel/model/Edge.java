package dev.kima.cogwheel.model;

import java.util.UUID;

/**
 * A connection from one node's output port to another's input port.
 *
 * @param from     Source node id.
 * @param fromPort Index into {@code from.outputs()}.
 * @param to       Destination node id.
 * @param toPort   Index into {@code to.inputs()}.
 * @param rate     Throughput per the design's chosen unit (items/min by default).
 */
public record Edge(UUID from, int fromPort, UUID to, int toPort, double rate) {}
