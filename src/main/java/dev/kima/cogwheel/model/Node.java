package dev.kima.cogwheel.model;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * Anything that can sit on the canvas: a source of items, a recipe applying transformation, a sink.
 *
 * <p>Sealed because the renderer + (later) solver branch on the concrete kind, and a closed
 * variant set lets the compiler enforce exhaustiveness.
 *
 * <p>Optional bottom-side "loop" port via {@link #bottomPort()} — nodes that opt in render a small
 * dot at their bottom edge that the {@link LoopNode}'s bottom emitter can attach to via a bottom-
 * to-bottom edge.
 */
public sealed interface Node permits SourceNode, RecipeNode, SinkNode, SplitterNode, MergerNode, OutputNode, LimiterNode, VoidNode, ClusterNode, LoopNode {
    UUID id();
    Vec2 position();
    List<Port> inputs();
    List<Port> outputs();

    /** Header item icon shown in the top-left of the rendered node. */
    ItemStack icon();

    /** Header text shown next to the icon. */
    Component title();

    /** Returns a new Node instance with the given position. Records are immutable; this is the wither. */
    Node withPosition(Vec2 newPosition);

    /** Loop-control ports rendered at the bottom or top edge of the node depending on its
     *  geometry. Default empty — only nodes that opt into loop gating return ports here.
     *  {@link LoopNode} returns two ports (loop start + end) rendered along its TOP edge so it
     *  visually sits BELOW the chain it gates; receiver nodes return one port rendered at their
     *  BOTTOM edge. */
    default List<Port> bottomPorts() { return List.of(); }
}
