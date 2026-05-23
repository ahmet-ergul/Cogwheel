package dev.kima.cogwheel.client.ui;

import dev.kima.cogwheel.CogwheelConstants;
import dev.kima.cogwheel.client.ui.canvas.Canvas;
import dev.kima.cogwheel.client.ui.canvas.CanvasRenderer;
import dev.kima.cogwheel.client.ui.canvas.ContextMenu;
import dev.kima.cogwheel.client.ui.canvas.DemoDesigns;
import dev.kima.cogwheel.client.ui.canvas.EdgeValidation;
import dev.kima.cogwheel.client.ui.canvas.HitTest;
import dev.kima.cogwheel.client.ui.panel.LeftPanel;
import dev.kima.cogwheel.client.ui.panel.LeftPanelPage;
import dev.kima.cogwheel.client.ui.panel.PropertiesPanel;
import dev.kima.cogwheel.client.ui.panel.SolverOverlay;
import dev.kima.cogwheel.client.ui.picker.RecipePickerScreen;
import dev.kima.cogwheel.model.Design;
import dev.kima.cogwheel.model.Edge;
import dev.kima.cogwheel.model.Factory;
import dev.kima.cogwheel.model.FactoryStore;
import dev.kima.cogwheel.model.MergerNode;
import dev.kima.cogwheel.model.Node;
import dev.kima.cogwheel.model.OutputNode;
import dev.kima.cogwheel.model.RecipeNode;
import dev.kima.cogwheel.model.SinkNode;
import dev.kima.cogwheel.model.SourceNode;
import dev.kima.cogwheel.model.SplitterNode;
import dev.kima.cogwheel.model.Vec2;
import dev.kima.cogwheel.persistence.FactoryFileStore;
import dev.kima.cogwheel.recipe.RecipeEntry;
import dev.kima.cogwheel.recipe.RecipeNodeFactory;
import dev.kima.cogwheel.recipe.source.RebuildTrigger;
import dev.kima.cogwheel.recipe.spike.RecipeAccessSpike;
import dev.kima.cogwheel.solver.Solver;
import dev.kima.cogwheel.solver.SolverResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

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

    private static final int TOGGLE_SIZE = 22;
    private static final int TOGGLE_MARGIN = 8;

    /** Loaded once per game session — subsequent EditorScreen opens reuse the singleton FactoryStore. */
    private static boolean storeLoaded = false;

    private final Canvas canvas = new Canvas();
    private final ContextMenu contextMenu = new ContextMenu();
    private LeftPanel leftPanel;
    private final PropertiesPanel properties = new PropertiesPanel(
            this::replaceSelectedNode,
            this::onSwapRecipeRequested,
            () -> propertiesPanelClosed = true);
    private Design design;
    private boolean panning = false;
    private int canvasX, canvasY, canvasW, canvasH;

    /** Reset to false whenever the selection changes, so a fresh selection always shows its properties. */
    private boolean propertiesPanelClosed = false;
    private UUID lastSelectionForPanel = null;

    /** Transient banner. Shown for {@link #BANNER_LIFETIME_MS} after each save / load. */
    private String banner;
    private long bannerExpiresAt;
    private static final long BANNER_LIFETIME_MS = 3000;

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

        // Load factory store from disk on the first open in this game session. Subsequent opens
        // reuse the singleton's in-memory state so unsaved edits don't get clobbered.
        if (!storeLoaded) {
            FactoryFileStore.load();
            storeLoaded = true;
        }
        if (FactoryStore.get().current() == null) {
            FactoryStore.get().bootstrapIfEmpty("Default Factory");
            FactoryStore.get().updateCurrent(DemoDesigns.ironSmelting());
        }
        design = FactoryStore.get().current().design();

        LeftPanelPage.WidgetHost widgetHost = new LeftPanelPage.WidgetHost() {
            @Override
            public void register(EditBox widget) {
                EditorScreen.this.addRenderableWidget(widget);
            }
            @Override
            public void unregister(EditBox widget) {
                EditorScreen.this.removeWidget(widget);
            }
        };
        leftPanel = new LeftPanel(
                widgetHost,
                RebuildTrigger.index(),
                new LeftPanel.Callbacks(
                        this::addRecipeNode,
                        this::addSourceNode,
                        this::addSinkNode,
                        this::addSplitterNode,
                        this::addMergerNode,
                        this::addOutputNode,
                        this::switchFactory,
                        this::createFactory,
                        this::deleteFactory,
                        this::renameFactory),
                null);

        recomputeLayout();
    }

    /** Set the current design AND propagate to the factory store so persistence stays in sync. */
    private void setDesign(Design newDesign) {
        this.design = newDesign;
        FactoryStore.get().updateCurrent(newDesign);
    }

    private void switchFactory(java.util.UUID factoryId) {
        FactoryStore.get().switchTo(factoryId);
        Factory now = FactoryStore.get().current();
        if (now != null) {
            this.design = now.design();
            canvas.clearSelection();
            canvas.setSelectedEdge(null);
            showBanner("Switched to: " + now.name());
        }
    }

    private void createFactory(String name) {
        Factory created = FactoryStore.get().createFactory(name);
        this.design = created.design();
        canvas.clearSelection();
        canvas.setSelectedEdge(null);
        showBanner("Created: " + created.name());
    }

    private void deleteFactory(java.util.UUID factoryId) {
        Factory victim = FactoryStore.get().findById(factoryId);
        String name = victim != null ? victim.name() : "factory";
        FactoryStore.get().delete(factoryId);
        Factory now = FactoryStore.get().current();
        if (now == null) {
            FactoryStore.get().bootstrapIfEmpty("Default Factory");
            now = FactoryStore.get().current();
        }
        this.design = now.design();
        canvas.clearSelection();
        canvas.setSelectedEdge(null);
        showBanner("Deleted: " + name);
    }

    private void renameFactory(java.util.UUID factoryId, String newName) {
        FactoryStore.get().rename(factoryId, newName);
        showBanner("Renamed");
    }

    /**
     * Recalculates canvas viewport bounds + panel layouts based on the current open/visible state
     * of the left and right panels. Called every frame from {@link #render} so panel toggling
     * appears smooth.
     */
    private void recomputeLayout() {
        int leftSpace = (leftPanel != null && leftPanel.isOpen())
                ? LeftPanel.WIDTH + CANVAS_MARGIN_OTHER : 0;
        boolean rightShown = isPropertiesPanelShown();
        int rightSpace = rightShown ? PropertiesPanel.WIDTH + CANVAS_MARGIN_OTHER : 0;

        canvasX = CANVAS_MARGIN_OTHER + leftSpace;
        canvasY = CANVAS_MARGIN_TOP;
        canvasW = this.width - canvasX - CANVAS_MARGIN_OTHER - rightSpace;
        canvasH = this.height - CANVAS_MARGIN_TOP - CANVAS_MARGIN_OTHER - SolverOverlay.HEIGHT;

        int panelH = this.height - CANVAS_MARGIN_TOP - CANVAS_MARGIN_OTHER;
        if (leftPanel != null) {
            leftPanel.setLayout(CANVAS_MARGIN_OTHER, CANVAS_MARGIN_TOP, panelH);
        }
        if (rightShown) {
            properties.setLayout(canvasX + canvasW + CANVAS_MARGIN_OTHER, CANVAS_MARGIN_TOP, panelH);
        }
    }

    private boolean isPropertiesPanelShown() {
        return canvas.selectedNodeId() != null && !propertiesPanelClosed;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        // Reset the closed flag whenever the selection changes — a fresh selection always shows
        // its properties, even if the user had closed the panel on the previous selection.
        UUID currentSelection = canvas.selectedNodeId();
        if (!Objects.equals(currentSelection, lastSelectionForPanel)) {
            lastSelectionForPanel = currentSelection;
            propertiesPanelClosed = false;
        }

        recomputeLayout();

        properties.setSelected(design.findNode(canvas.selectedNodeId()));

        CanvasRenderer.render(graphics, canvas, design, canvasX, canvasY, canvasW, canvasH);
        if (leftPanel != null && leftPanel.isOpen()) {
            leftPanel.render(graphics, mouseX, mouseY);
        }
        if (isPropertiesPanelShown()) {
            properties.render(graphics, mouseX, mouseY);
        }

        SolverResult solverResult = Solver.solve(design, RebuildTrigger.index());
        SolverOverlay.render(graphics, solverResult, canvasX, canvasY + canvasH, canvasW);

        renderToggleButton(graphics, mouseX, mouseY);

        graphics.drawCenteredString(this.font, this.title, canvasX + canvasW / 2, 8, 0xFFFFFFFF);
        renderBanner(graphics);

        // Context menu renders on top of everything, in screen space (not pose-transformed).
        contextMenu.render(graphics, mouseX, mouseY);
    }

    private int toggleX() { return canvasX + canvasW - TOGGLE_SIZE - TOGGLE_MARGIN; }
    private int toggleY() { return canvasY + canvasH - TOGGLE_SIZE - TOGGLE_MARGIN; }

    private boolean toggleHit(double sx, double sy) {
        int tx = toggleX();
        int ty = toggleY();
        return sx >= tx && sx <= tx + TOGGLE_SIZE && sy >= ty && sy <= ty + TOGGLE_SIZE;
    }

    private void renderToggleButton(GuiGraphics graphics, int mouseX, int mouseY) {
        int tx = toggleX();
        int ty = toggleY();
        boolean hover = toggleHit(mouseX, mouseY);
        int bg = hover ? 0xFF3A4868 : 0xCC2A3148;
        int border = 0xFF54607A;
        graphics.fill(tx, ty, tx + TOGGLE_SIZE, ty + TOGGLE_SIZE, bg);
        graphics.fill(tx - 1, ty - 1, tx + TOGGLE_SIZE + 1, ty, border);
        graphics.fill(tx - 1, ty + TOGGLE_SIZE, tx + TOGGLE_SIZE + 1, ty + TOGGLE_SIZE + 1, border);
        graphics.fill(tx - 1, ty, tx, ty + TOGGLE_SIZE, border);
        graphics.fill(tx + TOGGLE_SIZE, ty, tx + TOGGLE_SIZE + 1, ty + TOGGLE_SIZE, border);
        String icon = (leftPanel != null && leftPanel.isOpen()) ? "−" : "+";
        int iconW = this.font.width(icon);
        graphics.drawString(this.font, icon,
                tx + (TOGGLE_SIZE - iconW) / 2,
                ty + (TOGGLE_SIZE - 8) / 2,
                0xFFFFFFFF, false);
    }

    private void renderBanner(GuiGraphics graphics) {
        if (banner == null) return;
        if (System.currentTimeMillis() > bannerExpiresAt) {
            banner = null;
            return;
        }
        int w = this.font.width(banner) + 16;
        int bx = canvasX + canvasW - w - 8;
        int by = canvasY + 4;
        graphics.fill(bx, by, bx + w, by + 16, 0xCC1F2233);
        graphics.fill(bx - 1, by - 1, bx + w + 1, by, 0xFF54607A);
        graphics.fill(bx - 1, by + 16, bx + w + 1, by + 17, 0xFF54607A);
        graphics.fill(bx - 1, by, bx, by + 16, 0xFF54607A);
        graphics.fill(bx + w, by, bx + w + 1, by + 16, 0xFF54607A);
        graphics.drawString(this.font, banner, bx + 8, by + 4, 0xFFFFFFFF, false);
    }

    private void showBanner(String text) {
        this.banner = text;
        this.bannerExpiresAt = System.currentTimeMillis() + BANNER_LIFETIME_MS;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Context menu first — must catch its own clicks before anything else.
        if (contextMenu.isVisible()) {
            boolean consumed = contextMenu.handleClick(mouseX, mouseY);
            if (consumed) return true;
            // Menu just closed because click was outside it; fall through.
        }

        // Toggle button — always clickable regardless of panel state.
        if (toggleHit(mouseX, mouseY)) {
            if (leftPanel != null) {
                if (leftPanel.isOpen()) leftPanel.close();
                else leftPanel.open();
            }
            return true;
        }

        // Left panel (only when open).
        if (leftPanel != null && leftPanel.isOpen() && leftPanel.contains(mouseX, mouseY)) {
            boolean superHandled = super.mouseClicked(mouseX, mouseY, button);
            if (superHandled) return true;
            if (button == LEFT_MOUSE_BUTTON) {
                return leftPanel.mouseClicked(mouseX, mouseY, button);
            }
            return true;
        }

        // Right panel (only when shown).
        if (isPropertiesPanelShown() && properties.contains(mouseX, mouseY)) {
            return properties.mouseClicked(mouseX, mouseY, button);
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
            // After nodes, try edges — click an edge to select it (and then delete via Del/Backspace).
            Optional<Edge> edgeHit = HitTest.edgeAt(design, world.x(), world.y(), EDGE_HIT_RADIUS);
            if (edgeHit.isPresent()) {
                canvas.setSelectedEdge(edgeHit.get());
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
                            setDesign(design.withNodeRemoved(node.id()));
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
                        new ContextMenu.Option("Delete edge", () -> setDesign(design.withEdgeRemoved(edge)))));
                return true;
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (isPropertiesPanelShown() && properties.mouseReleased(mouseX, mouseY, button)) return true;
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
                    setDesign(design.withEdgeAdded(new Edge(
                            from.node().id(), from.port().index(),
                            to.node().id(), to.port().index(),
                            0.0)));
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
        if (isPropertiesPanelShown() && properties.mouseDragged(mouseX, mouseY, button, dragX, dragY)) return true;
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
                setDesign(design.withNodeMoved(canvas.draggingNodeId(), newPos));
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (leftPanel != null && leftPanel.isOpen() && leftPanel.contains(mouseX, mouseY)) {
            return leftPanel.mouseScrolled(mouseX, mouseY, scrollY);
        }
        canvas.zoomAt(mouseX, mouseY, scrollY);
        return true;
    }

    private Vec2 canvasCenter() {
        return canvas.screenToWorld(canvasX + canvasW / 2.0, canvasY + canvasH / 2.0);
    }

    private void addRecipeNode(Item item) {
        var index = RebuildTrigger.index();
        List<RecipeEntry> ways = index.waysToProduce(item);
        Vec2 placement = canvasCenter();

        if (ways.isEmpty()) {
            // No recipes — fall through to a source node so the click does SOMETHING.
            addSourceAt(item, placement);
            return;
        }
        if (ways.size() == 1) {
            RecipeNode node = RecipeNodeFactory.fromRecipeEntry(ways.get(0), placement);
            setDesign(design.withNodeAdded(node));
            canvas.setSelectedNodeId(node.id());
            return;
        }
        ItemStack stack = new ItemStack(item);
        Minecraft.getInstance().setScreen(new RecipePickerScreen(
                this,
                Component.literal(stack.getHoverName().getString()),
                ways,
                chosen -> {
                    RecipeNode node = RecipeNodeFactory.fromRecipeEntry(chosen, placement);
                    setDesign(design.withNodeAdded(node));
                    canvas.setSelectedNodeId(node.id());
                }));
    }

    private void addSourceNode(Item item) {
        addSourceAt(item, canvasCenter());
    }

    private void addSourceAt(Item item, Vec2 placement) {
        SourceNode node = RecipeNodeFactory.source(item, placement);
        setDesign(design.withNodeAdded(node));
        canvas.setSelectedNodeId(node.id());
    }

    private void addSinkNode(Item item) {
        SinkNode node = new SinkNode(UUID.randomUUID(), canvasCenter(), new ItemStack(item));
        setDesign(design.withNodeAdded(node));
        canvas.setSelectedNodeId(node.id());
    }

    private void addSplitterNode() {
        SplitterNode node = new SplitterNode(UUID.randomUUID(), canvasCenter(), SplitterNode.DEFAULT_OUTPUTS);
        setDesign(design.withNodeAdded(node));
        canvas.setSelectedNodeId(node.id());
    }

    private void addMergerNode() {
        MergerNode node = new MergerNode(UUID.randomUUID(), canvasCenter(), MergerNode.DEFAULT_INPUTS);
        setDesign(design.withNodeAdded(node));
        canvas.setSelectedNodeId(node.id());
    }

    private void addOutputNode(Item item) {
        OutputNode node = dev.kima.cogwheel.recipe.RecipeNodeFactory.output(item, canvasCenter());
        setDesign(design.withNodeAdded(node));
        canvas.setSelectedNodeId(node.id());
    }

    private void replaceSelectedNode(Node updated) {
        if (canvas.selectedNodeId() == null) return;
        setDesign(design.withNodeReplaced(canvas.selectedNodeId(), updated));
    }

    private void onSwapRecipeRequested() {
        Node selected = design.findNode(canvas.selectedNodeId());
        if (!(selected instanceof RecipeNode rn)) return;
        if (rn.outputs().isEmpty()) return;
        ItemStack primaryOutput = rn.outputs().get(0).display();
        if (primaryOutput.isEmpty()) return;

        List<RecipeEntry> ways = RebuildTrigger.index().waysToProduce(primaryOutput.getItem());
        if (ways.isEmpty()) return;
        if (ways.size() == 1) {
            // Only one path — replace anyway in case the recipe id changed under us.
            RecipeNode replacement = RecipeNodeFactory.fromRecipeEntry(ways.get(0), rn.position())
                    .withParallelism(rn.parallelism());
            setDesign(design.withNodeReplaced(rn.id(), withId(replacement, rn.id())));
            return;
        }
        Minecraft.getInstance().setScreen(new RecipePickerScreen(
                this,
                Component.literal(primaryOutput.getHoverName().getString()),
                ways,
                chosen -> {
                    RecipeNode replacement = RecipeNodeFactory.fromRecipeEntry(chosen, rn.position())
                            .withParallelism(rn.parallelism());
                    setDesign(design.withNodeReplaced(rn.id(), withId(replacement, rn.id())));
                }));
    }

    /**
     * RecipeNodeFactory assigns a fresh UUID; when SWAPPING we want to preserve the old one so any
     * edges connected to this node keep working. Construct a copy of {@code from} with the supplied id.
     */
    private static RecipeNode withId(RecipeNode from, UUID id) {
        return new RecipeNode(id, from.position(), from.recipeId(), from.title(), from.icon(),
                from.inputs(), from.outputs(), from.parallelism());
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Allow the EditBox / other vanilla widgets to consume keys first (typing in the search box
        // shouldn't trigger Ctrl+S or Delete).
        if (super.keyPressed(keyCode, scanCode, modifiers)) return true;

        // Delete / Backspace removes the selected edge or node.
        if (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            Edge sEdge = canvas.selectedEdge();
            if (sEdge != null) {
                setDesign(design.withEdgeRemoved(sEdge));
                canvas.setSelectedEdge(null);
                return true;
            }
            UUID sNode = canvas.selectedNodeId();
            if (sNode != null) {
                setDesign(design.withNodeRemoved(sNode));
                canvas.clearSelection();
                return true;
            }
            return false;
        }

        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        if (ctrl && keyCode == GLFW.GLFW_KEY_S) {
            boolean ok = FactoryFileStore.save();
            showBanner(ok ? "Saved" : "Save failed (see log)");
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_O) {
            if (FactoryFileStore.load()) {
                Factory now = FactoryStore.get().current();
                if (now != null) {
                    this.design = now.design();
                    canvas.clearSelection();
                    canvas.setSelectedEdge(null);
                }
                showBanner("Reloaded from disk");
            } else {
                showBanner("No saved store");
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
