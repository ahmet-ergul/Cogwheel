package dev.kima.cogwheel.client.ui.canvas;

import dev.kima.cogwheel.model.Design;
import dev.kima.cogwheel.model.Edge;
import dev.kima.cogwheel.model.Node;
import dev.kima.cogwheel.model.Port;
import dev.kima.cogwheel.model.PortType;
import dev.kima.cogwheel.model.RecipeNode;
import dev.kima.cogwheel.model.SinkNode;
import dev.kima.cogwheel.model.SourceNode;
import dev.kima.cogwheel.model.Vec2;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.UUID;

/**
 * Hardcoded Design used in Phase 3 to prove the canvas renders end-to-end. Phase 4 replaces this
 * with an empty Design + interactive node creation through the toolbox.
 *
 * <p>Uses vanilla items only so the canvas renders without Create in the runtime classpath.
 */
public final class DemoDesigns {
    private DemoDesigns() {}

    public static Design ironSmelting() {
        UUID sourceId = UUID.randomUUID();
        UUID recipeId = UUID.randomUUID();
        UUID sinkId = UUID.randomUUID();

        Node source = new SourceNode(
                sourceId,
                new Vec2(60, 80),
                new ItemStack(Items.RAW_IRON),
                SourceNode.Kind.MANUAL);

        Node recipe = new RecipeNode(
                recipeId,
                new Vec2(260, 80),
                ResourceLocation.fromNamespaceAndPath("minecraft", "iron_ingot_from_blasting"),
                Component.literal("Blasting: Raw Iron"),
                new ItemStack(Items.BLAST_FURNACE),
                List.of(new Port(0, PortType.ITEM, "Raw Iron", new ItemStack(Items.RAW_IRON))),
                List.of(new Port(0, PortType.ITEM, "Iron Ingot", new ItemStack(Items.IRON_INGOT))),
                1);

        Node sink = new SinkNode(
                sinkId,
                new Vec2(460, 80),
                new ItemStack(Items.IRON_INGOT));

        Edge e1 = new Edge(sourceId, 0, recipeId, 0, 1.0);
        Edge e2 = new Edge(recipeId, 0, sinkId, 0, 1.0);

        return new Design("Demo: Iron Smelting", List.of(source, recipe, sink), List.of(e1, e2));
    }
}
