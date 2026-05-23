package dev.kima.cogwheel.client.ui;

import dev.kima.cogwheel.CogwheelConstants;
import dev.kima.cogwheel.client.ui.canvas.Canvas;
import dev.kima.cogwheel.client.ui.canvas.CanvasRenderer;
import dev.kima.cogwheel.client.ui.canvas.ContextMenu;
import dev.kima.cogwheel.client.ui.canvas.DemoDesigns;
import dev.kima.cogwheel.client.ui.canvas.EdgeValidation;
import dev.kima.cogwheel.client.ui.canvas.HitTest;
import dev.kima.cogwheel.client.ui.panel.ToolboxPanel;
import dev.kima.cogwheel.client.ui.picker.RecipePickerScreen;
import dev.kima.cogwheel.model.Design;
import dev.kima.cogwheel.model.Edge;
import dev.kima.cogwheel.model.Node;
import dev.kima.cogwheel.model.RecipeNode;
import dev.kima.cogwheel.model.SourceNode;
import dev.kima.cogwheel.model.Vec2;
import dev.kima.cogwheel.recipe.RecipeEntry;
import dev.kima.cogwheel.recipe.RecipeNodeFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import dev.kima.cogwheel.recipe.source.RebuildTrigger;
import dev.kima.cogwheel.recipe.spike.RecipeAccessSpike;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Optional;

public class EditorScreen extends Screen {
    private static final int CANVAS_MARGIN_TOP = 28;
    private static final int CANVAS_MARGIN_OTHER = 8;
    private static final int LEFT_MOUSE_BUTTON = 0;
    private static final int RIGHT_MOUSE_BUTTON = 1;
    private static final int MIDDLE_MOUSE_BUTTON = 2;
    /** World-space distance threshold for hit-testing edges (cubic bezier point→segment). */
    private static final double EDGE_HIT_RADIUS = 6;

    /** World-space radius for port hit-testing. Slightly larger than the visual port dot for forgiving clicks. */
    private static final double PORT_HIT_RADIUS = 10;

    private final Canvas canvas = new Canvas();
    private final ContextMenu contextMenu = new ContextMenu();
    private final ToolboxPanel toolbox = new ToolboxPanel(this::onToolboxItemClicked);
    private Design design = DemoDesigns.ironSmelting();
    private boolean panning = false;
    private int canvasX, canvasY, canvasW, canvasH;

    public EditorScreen() {
        super(Component.translatable("screen.cogwheel.editor.title"));
    }

    @Override
    protected void init() {
        RecipeAccessSpike.probe("EditorScreen.init");
        RebuildTrigger.ensureBuilt();
        CogwheelConstants.LOG.info(
                "EditorScreen opened with RecipeIndex size = {}",
                RebuildTrigger.index().size());

        toolbox.buildCatalog(RebuildTrigger.index());
        toolbox.setLayout(CANVAS_MARGIN_OTHER, CANVAS_MARGIN_TOP,
                this.height - CANVAS_MARGIN_TOP - CANVAS_MARGIN_OTHER);
        this.addRenderableWidget(toolbox.createSearchBox(this.font,
                CANVAS_MARGIN_OTHER, CANVAS_MARGIN_TOP));

        canvasX = CANVAS_MARGIN_OTHER + ToolboxPanel.WIDTH + CANVAS_MARGIN_OTHER;
        canvasY = CANVAS_MARGIN_TOP;
        canvasW = this.width - canvasX - CANVAS_MARGIN_OTHER;
        canvasH = this.height - CANVAS_MARGIN_TOP - CANVAS_MARGIN_OTHER;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        CanvasRenderer.render(graphics, canvas, design, canvasX, canvasY, canvasW, canvasH);
        toolbox.render(graphics, mouseX, mouseY);

        graphics.drawCenteredString(this.font, this.title, canvasX + canvasW / 2, 8, 0xFFFFFFFF);

        // Context menu renders on top of everything, in screen space (not pose-transformed).
        contextMenu.render(graphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Any click first interacts with the context menu if it's open.
        if (contextMenu.isVisible()) {
            boolean consumed = contextMenu.handleClick(mouseX, mouseY);
            if (consumed) return true;
            // Menu just closed because click was outside it; fall through so the click still hits canvas.
        }

        // Toolbox panel gets first chance — clicks inside it never reach the canvas.
        if (toolbox.contains(mouseX, mouseY)) {
            // Let vanilla widgets (the search EditBox) handle focus first.
            boolean superHandled = super.mouseClicked(mouseX, mouseY, button);
            if (superHandled) return true;
            if (button == LEFT_MOUSE_BUTTON) {
                return toolbox.mouseClicked(mouseX, mouseY, button);
            }
            return true;
        }

        if (button == MIDDLE_MOUSE_BUTTON) {
            panning = true;
            return true;
        }
        if (button == LEFT_MOUSE_BUTTON) {
            Vec2 world = canvas.screenToWorld(mouseX, mouseY);

            // Priority: ports → nodes → background. Edges + right-click handled separately.
            Optional<HitTest.PortHit> portHit = HitTest.portAt(design, world.x(), world.y(), PORT_HIT_RADIUS);
            if (portHit.isPresent() && portHit.get().output()) {
                canvas.startEdgeDrag(portHit.get(), world.x(), world.y());
                return true;
            }

            Optional<Node> hit = HitTest.nodeAt(design, world.x(), world.y());
            if (hit.isPresent()) {
                Node node = hit.get();
                canvas.setSelectedNodeId(node.id());
                canvas.startNodeDrag(node.id(), node.position(), world.x(), world.y());
                return true;
            }
            // Click on empty canvas clears selection.
            canvas.clearSelection();
            return true;
        }
        if (button == RIGHT_MOUSE_BUTTON) {
            Vec2 world = canvas.screenToWorld(mouseX, mouseY);
            Optional<Node> nodeHit = HitTest.nodeAt(design, world.x(), world.y());
            if (nodeHit.isPresent()) {
                Node node = nodeHit.get();
                contextMenu.show((int) mouseX, (int) mouseY, List.of(
                        new ContextMenu.Option("Delete node", () -> {
                            design = design.withNodeRemoved(node.id());
                            if (node.id().equals(canvas.selectedNodeId())) {
                                canvas.clearSelection();
                            }
                        })));
                return true;
            }
            Optional<Edge> edgeHit = HitTest.edgeAt(design, world.x(), world.y(), EDGE_HIT_RADIUS);
            if (edgeHit.isPresent()) {
                Edge edge = edgeHit.get();
                contextMenu.show((int) mouseX, (int) mouseY, List.of(
                        new ContextMenu.Option("Delete edge", () -> design = design.withEdgeRemoved(edge))));
                return true;
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == MIDDLE_MOUSE_BUTTON) {
            panning = false;
            return true;
        }
        if (button == LEFT_MOUSE_BUTTON) {
            if (canvas.pendingEdgeFrom() != null) {
                Vec2 world = canvas.screenToWorld(mouseX, mouseY);
                Optional<HitTest.PortHit> drop = HitTest.portAt(design, world.x(), world.y(), PORT_HIT_RADIUS);
                if (drop.isPresent() && EdgeValidation.canConnect(design, canvas.pendingEdgeFrom(), drop.get())) {
                    HitTest.PortHit from = canvas.pendingEdgeFrom();
                    HitTest.PortHit to = drop.get();
                    design = design.withEdgeAdded(new Edge(
                            from.node().id(), from.port().index(),
                            to.node().id(), to.port().index(),
                            0.0));
                }
                canvas.endEdgeDrag();
                return true;
            }
            canvas.endNodeDrag();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (panning) {
            canvas.pan(dragX, dragY);
            return true;
        }
        if (button == LEFT_MOUSE_BUTTON) {
            Vec2 world = canvas.screenToWorld(mouseX, mouseY);
            if (canvas.pendingEdgeFrom() != null) {
                canvas.updatePendingEdge(world.x(), world.y());
                return true;
            }
            if (canvas.draggingNodeId() != null) {
                Vec2 newPos = canvas.currentDragTarget(world.x(), world.y());
                design = design.withNodeMoved(canvas.draggingNodeId(), newPos);
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (toolbox.contains(mouseX, mouseY)) {
            return toolbox.mouseScrolled(mouseX, mouseY, scrollY);
        }
        canvas.zoomAt(mouseX, mouseY, scrollY);
        return true;
    }

    private void onToolboxItemClicked(Item item) {
        var index = RebuildTrigger.index();
        List<RecipeEntry> ways = index.waysToProduce(item);
        Vec2 placement = canvas.screenToWorld(canvasX + canvasW / 2.0, canvasY + canvasH / 2.0);

        if (ways.isEmpty()) {
            // No recipes produce this — treat as a raw input source.
            SourceNode node = RecipeNodeFactory.source(item, placement);
            design = design.withNodeAdded(node);
            canvas.setSelectedNodeId(node.id());
            return;
        }
        if (ways.size() == 1) {
            RecipeNode node = RecipeNodeFactory.fromRecipeEntry(ways.get(0), placement);
            design = design.withNodeAdded(node);
            canvas.setSelectedNodeId(node.id());
            return;
        }
        // Multiple options — open the picker.
        ItemStack stack = new ItemStack(item);
        Minecraft.getInstance().setScreen(new RecipePickerScreen(
                this,
                Component.literal(stack.getHoverName().getString()),
                ways,
                chosen -> {
                    RecipeNode node = RecipeNodeFactory.fromRecipeEntry(chosen, placement);
                    design = design.withNodeAdded(node);
                    canvas.setSelectedNodeId(node.id());
                }));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
