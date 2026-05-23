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
 */
public sealed interface Node permits SourceNode, RecipeNode, SinkNode {
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
}
