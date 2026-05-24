package dev.kima.cogwheel.recipe.adapter.create;

import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedRecipe;
import dev.kima.cogwheel.CogwheelConstants;
import dev.kima.cogwheel.model.ClusterNode;
import dev.kima.cogwheel.model.Design;
import dev.kima.cogwheel.model.Edge;
import dev.kima.cogwheel.model.LoopNode;
import dev.kima.cogwheel.model.Node;
import dev.kima.cogwheel.model.RecipeNode;
import dev.kima.cogwheel.model.SinkNode;
import dev.kima.cogwheel.model.SourceNode;
import dev.kima.cogwheel.model.Vec2;
import dev.kima.cogwheel.model.Port;
import dev.kima.cogwheel.model.PortType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Builds a {@link ClusterNode} (LOCKED) from a Create {@link SequencedAssemblyRecipe}. The cluster's
 * inner {@link Design} contains:
 * <ul>
 *   <li>One {@link SourceNode} per unique external ingredient — the base item plus each sub-step's
 *       "tool" (the held / applied item for deployer steps; nothing for pure pressing steps).</li>
 *   <li>One {@link RecipeNode} per step in the assembly sequence, in order. Synthetic ids so they
 *       don't collide with anything in the recipe index.</li>
 *   <li>One {@link SinkNode} for the final output (first entry in {@code resultPool}).</li>
 *   <li>Edges wiring base → step 1 → step 2 → … → step N → sink, plus per-step tool edges.</li>
 * </ul>
 *
 * <p>The cluster's auto-derived outer inputs/outputs come from the inner SourceNodes and SinkNode.
 * Loop count is folded into the demand by multiplying each tool's effective port count by
 * {@code loops}; the base ingredient stays at 1.
 *
 * <p>Imports Create types directly — only call from a code path gated on
 * {@code ModList.isLoaded("create")}.
 */
public final class CreateSequencedAssemblyClusterBuilder {

    private CreateSequencedAssemblyClusterBuilder() {}

    /** Returns {@code null} if the recipe id doesn't resolve to a SequencedAssemblyRecipe. */
    public static ClusterNode tryBuild(ResourceLocation recipeId, Vec2 outerPosition) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        Optional<RecipeHolder<?>> holderOpt = mc.level.getRecipeManager().byKey(recipeId);
        if (holderOpt.isEmpty()) return null;
        if (!(holderOpt.get().value() instanceof SequencedAssemblyRecipe sar)) return null;
        return build(recipeId, sar, outerPosition);
    }

    private static ClusterNode build(ResourceLocation recipeId, SequencedAssemblyRecipe sar, Vec2 outerPosition) {
        try {
            return buildInner(recipeId, sar, outerPosition);
        } catch (Throwable t) {
            CogwheelConstants.LOG.warn("CreateSequencedAssemblyClusterBuilder: build failed for {}: {}",
                    recipeId, t.toString());
            return null;
        }
    }

    private static ClusterNode buildInner(ResourceLocation recipeId, SequencedAssemblyRecipe sar, Vec2 outerPosition) {
        Ingredient base = sar.getIngredient();
        List<SequencedRecipe<?>> sequence = sar.getSequence();
        List<ProcessingOutput> results = sar.resultPool;
        int loops = sar.getLoops();
        ItemStack transitional = sar.getTransitionalItem();

        ItemStack baseStack = firstItem(base);
        ItemStack finalOutput = (results == null || results.isEmpty()) ? ItemStack.EMPTY : results.get(0).getStack();

        // Layout constants — inner-design world coordinates (cluster has its own coordinate space).
        final int LEFT_X = 40;
        final int CENTER_X_BASE = 240;
        final int STEP_DX = 200;
        final int ROW_Y = 60;
        final int SOURCE_DY = 80;

        List<Node> nodes = new ArrayList<>();
        List<Edge> edges = new ArrayList<>();

        // Base source node (gold ingot, etc.) — flows through every step.
        SourceNode baseSource = new SourceNode(UUID.randomUUID(), new Vec2(LEFT_X, ROW_Y),
                baseStack.isEmpty() ? new ItemStack(net.minecraft.world.item.Items.STONE) : baseStack,
                SourceNode.Kind.MANUAL);
        nodes.add(baseSource);

        // Step nodes: one RecipeNode per element in the sequence. Each takes the in-progress
        // (transitional) item from the previous step plus its own tool (if any), and outputs the
        // in-progress item to the next step (or the final result on the last step).
        // We also collect tool sources separately, deduplicated by item, so the cluster's outer
        // input ports compress (3 iron nuggets across 3 loops = ONE iron-nugget input port).
        Map<Item, ItemStack> toolSources = new LinkedHashMap<>();
        List<UUID> stepIds = new ArrayList<>();
        List<RecipeNode> stepNodes = new ArrayList<>();
        UUID prevStepId = null;

        for (int i = 0; i < sequence.size(); i++) {
            SequencedRecipe<?> sub = sequence.get(i);
            ProcessingRecipe<?, ?> processing = sub.getRecipe();
            ItemStack toolItem = extractTool(processing, transitional);

            // Inputs for this step's RecipeNode: transitional in port 0, tool in port 1 (if present).
            List<Port> stepInputs = new ArrayList<>();
            ItemStack inDisplay = (prevStepId == null) ? baseStack : transitional;
            stepInputs.add(new Port(0, PortType.ITEM,
                    inDisplay.isEmpty() ? "in" : inDisplay.getHoverName().getString(), inDisplay));
            if (!toolItem.isEmpty()) {
                stepInputs.add(new Port(1, PortType.ITEM, toolItem.getHoverName().getString(), toolItem));
                toolSources.putIfAbsent(toolItem.getItem(), toolItem);
            }
            // Output: either the transitional (continuing) or the final result (last step).
            ItemStack stepOutItem = (i == sequence.size() - 1 && !finalOutput.isEmpty()) ? finalOutput : transitional;
            List<Port> stepOutputs = List.of(new Port(0, PortType.ITEM,
                    stepOutItem.isEmpty() ? "out" : stepOutItem.getHoverName().getString(), stepOutItem));

            ResourceLocation typeId = net.minecraft.core.registries.BuiltInRegistries.RECIPE_TYPE.getKey(processing.getType());
            ResourceLocation synthId = ResourceLocation.fromNamespaceAndPath("cogwheel_synthetic",
                    sanitizePath(recipeId.toString() + "/step_" + (i + 1) + "_" + (typeId == null ? "step" : typeId.getPath())));
            Component title = Component.literal((typeId == null ? "step " + (i + 1) : typeId.getPath()) + " " + (i + 1));
            RecipeNode step = new RecipeNode(
                    UUID.randomUUID(),
                    new Vec2(CENTER_X_BASE + i * STEP_DX, ROW_Y),
                    synthId, title,
                    !inDisplay.isEmpty() ? inDisplay : (toolItem.isEmpty() ? ItemStack.EMPTY : toolItem),
                    stepInputs, stepOutputs, 1);
            nodes.add(step);
            stepIds.add(step.id());
            stepNodes.add(step);

            // Edge: previous step (or base source) → this step input port 0.
            UUID upstreamId = (prevStepId == null) ? baseSource.id() : prevStepId;
            edges.add(new Edge(upstreamId, 0, step.id(), 0, 0.0));
            prevStepId = step.id();
        }

        // Tool sources stacked vertically below the base source. Each tool wires to every step
        // that consumes that item — we connect via the step's port-1 (tool) port.
        int sourceY = ROW_Y + SOURCE_DY;
        Map<Item, UUID> toolSourceIds = new LinkedHashMap<>();
        for (Map.Entry<Item, ItemStack> e : toolSources.entrySet()) {
            SourceNode src = new SourceNode(UUID.randomUUID(), new Vec2(LEFT_X, sourceY),
                    e.getValue(), SourceNode.Kind.MANUAL);
            nodes.add(src);
            toolSourceIds.put(e.getKey(), src.id());
            sourceY += SOURCE_DY;
        }
        // Wire tool sources to step input port 1.
        for (int i = 0; i < stepNodes.size(); i++) {
            RecipeNode step = stepNodes.get(i);
            if (step.inputs().size() < 2) continue;
            ItemStack toolDisplay = step.inputs().get(1).display();
            if (toolDisplay.isEmpty()) continue;
            UUID src = toolSourceIds.get(toolDisplay.getItem());
            if (src != null) {
                edges.add(new Edge(src, 0, step.id(), 1, 0.0));
            }
        }

        // Final sink — produces the cluster's external output port via the last step's output edge.
        if (!finalOutput.isEmpty() && !stepNodes.isEmpty()) {
            SinkNode sink = new SinkNode(UUID.randomUUID(),
                    new Vec2(CENTER_X_BASE + sequence.size() * STEP_DX, ROW_Y),
                    finalOutput);
            nodes.add(sink);
            edges.add(new Edge(stepNodes.get(stepNodes.size() - 1).id(), 0, sink.id(), 0, 0.0));
        }

        // Embed a LoopNode gating the assembled segment when the assembly repeats. The loop sits
        // BELOW the row of steps; its TWO loop ports (top-left = start, top-right = end) attach
        // to the first and last step's bottom ports respectively, defining the loop boundary.
        // Skip when loops <= 1 — a single-iteration cluster doesn't benefit from a visible gate.
        if (loops > 1 && !stepNodes.isEmpty()) {
            RecipeNode first = stepNodes.get(0);
            RecipeNode last = stepNodes.get(stepNodes.size() - 1);
            // Centered horizontally between first and last; ~100 px below the step row.
            int loopX = (int) ((first.position().x() + last.position().x()) / 2);
            int loopY = ROW_Y + 100;
            LoopNode loop = new LoopNode(UUID.randomUUID(), new Vec2(loopX, loopY), loops);
            nodes.add(loop);
            // Loop's port 0 (left/start) → first step's bottom port 0.
            edges.add(new Edge(loop.id(), 0, first.id(), 0, 0.0, true, true));
            // Loop's port 1 (right/end) → last step's bottom port 0. If there's only one step
            // the same step gets both attachments (loop wraps a single step).
            edges.add(new Edge(loop.id(), 1, last.id(), 0, 0.0, true, true));
        }

        Design inner = new Design("Sequenced Assembly", List.copyOf(nodes), List.copyOf(edges));

        String shortPath = recipeId.getPath();
        int slash = shortPath.lastIndexOf('/');
        if (slash >= 0) shortPath = shortPath.substring(slash + 1);
        String label = (finalOutput.isEmpty() ? shortPath : finalOutput.getHoverName().getString())
                + " ×" + loops;

        return new ClusterNode(UUID.randomUUID(), outerPosition, label, inner,
                ClusterNode.Kind.LOCKED, 1);
    }

    /** A sub-step's "tool" is the ingredient whose matching items don't include the transitional
     *  item. For pure pressing/cutting (single-ingredient steps that just transform the base),
     *  there's no tool — return empty. */
    private static ItemStack extractTool(ProcessingRecipe<?, ?> step, ItemStack transitional) {
        Item transitionalItem = transitional == null ? null : transitional.getItem();
        for (Ingredient ing : step.getIngredients()) {
            if (ing == null || ing.isEmpty()) continue;
            ItemStack[] stacks = ing.getItems();
            if (stacks.length == 0) continue;
            // If this ingredient matches the transitional, it's the in-progress item — skip.
            boolean matchesTransitional = false;
            for (ItemStack s : stacks) {
                if (transitionalItem != null && s.getItem() == transitionalItem) {
                    matchesTransitional = true;
                    break;
                }
            }
            if (matchesTransitional) continue;
            return stacks[0].copy();
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack firstItem(Ingredient base) {
        if (base == null || base.isEmpty()) return ItemStack.EMPTY;
        ItemStack[] items = base.getItems();
        return items.length == 0 ? ItemStack.EMPTY : items[0].copy();
    }

    private static String sanitizePath(String s) {
        return s.toLowerCase().replaceAll("[^a-z0-9/._-]", "_");
    }
}
