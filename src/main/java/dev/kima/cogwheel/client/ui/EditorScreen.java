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
import dev.kima.cogwheel.integration.jei.JeiBridge;
import dev.kima.cogwheel.model.Design;
import dev.kima.cogwheel.model.Edge;
import dev.kima.cogwheel.model.Factory;
import dev.kima.cogwheel.model.FactoryStore;
import dev.kima.cogwheel.model.ClusterNode;
import dev.kima.cogwheel.model.VoidNode;
import dev.kima.cogwheel.model.LimiterNode;
import dev.kima.cogwheel.model.LoopNode;
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
            this::replaceEditingNode,
            this::onSwapRecipeRequested,
            () -> editingNodeId = null,
            RebuildTrigger::index,
            // inCluster supplier — drives the "Inherit RPM from <factory|cluster>" label.
            this::inCluster,
            // ambient RPM supplier — walks the cluster stack to find the right "parent" RPM.
            () -> effectiveRpmForCurrentContext(FactoryStore.get().current()));
    private Design design;
    private boolean panning = false;
    private int canvasX, canvasY, canvasW, canvasH;

    /** Selection is a passive highlight (single click). Editing is the active focus that opens the
     *  properties panel (double-click or context-menu "Edit"). They're independent so clicking around
     *  the canvas doesn't keep popping the right sidebar open. */
    private UUID editingNodeId = null;

    /** Stack of (parent design, cluster id, cluster label) while navigating into ClusterNodes.
     *  Empty when viewing the top-level factory design. Inside a cluster, edits to the design field
     *  don't propagate to the FactoryStore (LOCKED clusters are view-only). */
    private record ClusterFrame(Design parentDesign, UUID clusterId, String clusterLabel, ClusterNode.Kind kind) {}
    private final java.util.ArrayDeque<ClusterFrame> clusterStack = new java.util.ArrayDeque<>();
    private boolean inCluster() { return !clusterStack.isEmpty(); }
    private boolean inLockedCluster() {
        return inCluster() && clusterStack.peek().kind() == ClusterNode.Kind.LOCKED;
    }
    /** Last left-click on a node — used to detect double-click within {@link #DOUBLE_CLICK_MS}. */
    private UUID lastClickedNodeId = null;
    private long lastClickedAt = 0;
    private static final long DOUBLE_CLICK_MS = 350;

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
            FactoryFileStore.loadAll();
            storeLoaded = true;
        }
        if (FactoryStore.get().current() == null) {
            FactoryStore.get().bootstrapIfEmpty("Default Factory");
            FactoryStore.get().updateCurrent(DemoDesigns.ironSmelting());
        }
        // Only re-read the root design when we're not inside a cluster. Otherwise (re-init from
        // picker close, window resize, etc.) preserve the active cluster's inner design so drops
        // and edits still land in the cluster instead of leaking to the root.
        if (!inCluster()) {
            design = FactoryStore.get().current().design();
        }

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
        // Preserve panel state across screen re-inits (e.g. JEI took over and returned, or window
        // resized): keep the field, just re-bind the widget host so the page's search box etc.
        // get re-registered.
        if (leftPanel == null) {
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
                            this::renameFactory,
                            this::refreshFactories,
                            FactoryFileStore::openFactoriesFolder,
                            this::addLimiterNode,
                            this::addVoidNode,
                            this::addClusterNode,
                            this::addLoopNode,
                            this::applyFactoryPower),
                    null);
            leftPanel.open();
        } else {
            leftPanel.reattachWidgetHost(widgetHost);
        }

        recomputeLayout();
    }

    /** Set the current design AND propagate to the factory store so persistence stays in sync.
     *  Per-file auto-save keeps {@code cogwheel/factories/<name>.json} consistent with in-memory
     *  state so users can share factories without thinking about Ctrl+S.
     *
     *  <p>Inside a USER cluster, edits walk the cluster stack rebuilding each parent design (each
     *  cluster has its {@code innerDesign} replaced with the level below). LOCKED clusters block
     *  propagation — local edits stay until the user exits and are then discarded. */
    private void setDesign(Design newDesign) {
        this.design = newDesign;
        if (!inCluster()) {
            FactoryStore.get().updateCurrent(newDesign);
            Factory now = FactoryStore.get().current();
            if (now != null) FactoryFileStore.saveFactory(now);
            return;
        }
        if (inLockedCluster()) {
            // Locked — edits don't persist. this.design is updated transiently for the in-view
            // render but never reaches the FactoryStore.
            return;
        }
        // USER cluster: rebuild each frame with the updated parent design and flush to the store.
        Design currentInner = newDesign;
        java.util.ArrayDeque<ClusterFrame> rebuilt = new java.util.ArrayDeque<>();
        for (ClusterFrame frame : clusterStack) { // innermost-first
            Node n = frame.parentDesign().findNode(frame.clusterId());
            if (n instanceof ClusterNode cn) {
                ClusterNode updatedCluster = cn.withInnerDesign(currentInner);
                Design newParent = frame.parentDesign().withNodeReplaced(frame.clusterId(), updatedCluster);
                rebuilt.addLast(new ClusterFrame(newParent, frame.clusterId(), frame.clusterLabel(), frame.kind()));
                currentInner = newParent;
            } else {
                rebuilt.addLast(frame); // defensive — shouldn't happen
            }
        }
        clusterStack.clear();
        java.util.Iterator<ClusterFrame> it = rebuilt.descendingIterator();
        while (it.hasNext()) clusterStack.push(it.next());
        FactoryStore.get().updateCurrent(currentInner);
        Factory now = FactoryStore.get().current();
        if (now != null) FactoryFileStore.saveFactory(now);
    }

    private void enterCluster(ClusterNode cluster) {
        clusterStack.push(new ClusterFrame(this.design, cluster.id(), cluster.label(), cluster.kind()));
        this.design = cluster.innerDesign();
        canvas.clearSelection();
        canvas.setSelectedEdge(null);
        editingNodeId = null;
    }

    private void exitCluster() {
        if (clusterStack.isEmpty()) return;
        ClusterFrame frame = clusterStack.pop();
        this.design = frame.parentDesign;
        canvas.clearSelection();
        canvas.setSelectedEdge(null);
        editingNodeId = null;
    }

    private void switchFactory(java.util.UUID factoryId) {
        FactoryStore.get().switchTo(factoryId);
        Factory now = FactoryStore.get().current();
        if (now != null) {
            this.design = now.design();
            canvas.clearSelection();
            canvas.setSelectedEdge(null);
            editingNodeId = null;
            showBanner("Switched to: " + now.name());
        }
    }

    private void createFactory(String name) {
        Factory created = FactoryStore.get().createFactory(name);
        this.design = created.design();
        canvas.clearSelection();
        canvas.setSelectedEdge(null);
        editingNodeId = null;
        FactoryFileStore.saveFactory(created);
        showBanner("Created: " + created.name());
    }

    private void deleteFactory(java.util.UUID factoryId) {
        Factory victim = FactoryStore.get().findById(factoryId);
        String name = victim != null ? victim.name() : "factory";
        FactoryStore.get().delete(factoryId);
        FactoryFileStore.deleteFactoryFile(factoryId);
        Factory now = FactoryStore.get().current();
        if (now == null) {
            FactoryStore.get().bootstrapIfEmpty("Default Factory");
            now = FactoryStore.get().current();
        }
        this.design = now.design();
        canvas.clearSelection();
        canvas.setSelectedEdge(null);
        editingNodeId = null;
        showBanner("Deleted: " + name);
    }

    private void renameFactory(java.util.UUID factoryId, String newName) {
        FactoryStore.get().rename(factoryId, newName);
        Factory renamed = FactoryStore.get().findById(factoryId);
        if (renamed != null) FactoryFileStore.saveFactory(renamed);
        showBanner("Renamed");
    }

    private void refreshFactories() {
        int n = FactoryFileStore.loadAll();
        Factory now = FactoryStore.get().current();
        if (now == null) {
            FactoryStore.get().bootstrapIfEmpty("Default Factory");
            now = FactoryStore.get().current();
        }
        this.design = now.design();
        canvas.clearSelection();
        canvas.setSelectedEdge(null);
        editingNodeId = null;
        showBanner("Refreshed: " + n + " factories");
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
        canvasH = this.height - CANVAS_MARGIN_TOP - CANVAS_MARGIN_OTHER
                - SolverOverlay.HEIGHT - SolverOverlay.POWER_STRIP_HEIGHT;

        int panelH = this.height - CANVAS_MARGIN_TOP - CANVAS_MARGIN_OTHER;
        if (leftPanel != null) {
            leftPanel.setLayout(CANVAS_MARGIN_OTHER, CANVAS_MARGIN_TOP, panelH);
        }
        if (rightShown) {
            properties.setLayout(canvasX + canvasW + CANVAS_MARGIN_OTHER, CANVAS_MARGIN_TOP, panelH);
        }
    }

    private boolean isPropertiesPanelShown() {
        return editingNodeId != null && design.findNode(editingNodeId) != null;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        recomputeLayout();

        if (isPropertiesPanelShown()) {
            properties.setSelected(design.findNode(editingNodeId));
        } else {
            properties.setSelected(null);
        }

        // Run the solver FIRST so the per-node SU map is available to the canvas renderer (which
        // draws a small SU badge under each Create-backed node so the user can verify exactly what
        // each node contributes to the total — useful for diagnosing setting mismatches).
        Factory currentFactory = FactoryStore.get().current();
        int currentRpm = effectiveRpmForCurrentContext(currentFactory);
        SolverResult solverResult = Solver.solve(design, RebuildTrigger.index(), currentRpm);

        CanvasRenderer.render(graphics, canvas, design, canvasX, canvasY, canvasW, canvasH,
                inLockedCluster(), solverResult.nodeSuDraw());
        if (leftPanel != null && leftPanel.isOpen()) {
            leftPanel.render(graphics, mouseX, mouseY);
        }
        if (isPropertiesPanelShown()) {
            properties.render(graphics, mouseX, mouseY);
        }

        SolverOverlay.render(graphics, solverResult, canvasX, canvasY + canvasH, canvasW,
                currentFactory);

        renderToggleButton(graphics, mouseX, mouseY);

        renderCanvasHeader(graphics);
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

    /**
     * Header strip at the top of the canvas viewport. When inside a cluster, appends a breadcrumb:
     * {@code Cogwheel · MyFactory ▸ Precision Mechanism ×5}.
     */
    private void renderCanvasHeader(GuiGraphics graphics) {
        Factory cur = FactoryStore.get().current();
        String factoryName = cur != null ? cur.name() : "—";
        StringBuilder title = new StringBuilder();
        title.append(this.title.getString()).append(" · ").append(factoryName);
        // Iterate stack oldest-first so the breadcrumb reads left-to-right by entry order.
        java.util.Iterator<ClusterFrame> it = clusterStack.descendingIterator();
        while (it.hasNext()) {
            title.append("  ▸  ").append(it.next().clusterLabel());
        }
        String t = title.toString();
        int tw = this.font.width(t);
        int tx = canvasX + (canvasW - tw) / 2;
        graphics.drawString(this.font, t, tx, 8, 0xFFFFFFFF, true);
        if (inCluster()) {
            // Tiny "← back" hint on the right edge of the header.
            String hint = "← Esc";
            graphics.drawString(this.font, hint,
                    canvasX + canvasW - this.font.width(hint) - 8, 8, 0xFFA8A8B8, false);
            // Top-left lock badge inside a LOCKED cluster.
            if (inLockedCluster()) {
                String lock = "🔒 Locked";
                graphics.drawString(this.font, lock, canvasX + 4, 8, 0xFFE8A040, true);
            }
        }
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
            boolean ctrl = hasControlDown();
            boolean locked = inLockedCluster();

            // Priority: ports → nodes → background. Edges + right-click handled separately.
            // Locked clusters never start edge drags.
            // Bottom-side loop ports also start a drag — direction (source vs receiver) is
            // resolved at drop time by EdgeValidation, which checks which side is the LoopNode.
            Optional<HitTest.PortHit> portHit = HitTest.portAt(design, world.x(), world.y(), PORT_HIT_RADIUS);
            if (portHit.isPresent() && (portHit.get().output() || portHit.get().bottom()) && !locked) {
                canvas.startEdgeDrag(portHit.get(), world.x(), world.y());
                return true;
            }

            Optional<Node> hit = HitTest.nodeAt(design, world.x(), world.y());
            if (hit.isPresent()) {
                Node node = hit.get();
                long now = System.currentTimeMillis();
                boolean isDoubleClick = node.id().equals(lastClickedNodeId)
                        && (now - lastClickedAt) < DOUBLE_CLICK_MS;
                lastClickedNodeId = node.id();
                lastClickedAt = now;
                if (ctrl) {
                    canvas.toggleSelection(node.id());
                    return true;
                }
                // Shift / Alt + click: quick-connect from the singly-selected node to the clicked one.
                if (!locked && (hasShiftDown() || hasAltDown())
                        && canvas.selectedNodes().size() == 1
                        && !canvas.isSelected(node.id())) {
                    UUID firstId = canvas.selectedNodeId();
                    Node first = design.findNode(firstId);
                    if (first != null) {
                        Edge connection = hasShiftDown()
                                ? trySmartConnect(first, node)
                                : trySmartConnect(node, first);
                        if (connection != null) setDesign(design.withEdgeAdded(connection));
                    }
                    canvas.setSelectedNodeId(node.id());
                    return true;
                }
                if (!canvas.isSelected(node.id())) {
                    canvas.setSelectedNodeId(node.id());
                    // If the properties panel is currently open on a DIFFERENT node, re-target it
                    // to the just-clicked one. Without this re-sync, the user can visually select
                    // a new node but the panel + slider drags would still target the previously
                    // opened node — silent "I'm editing the wrong thing" bug.
                    if (!locked && editingNodeId != null && !editingNodeId.equals(node.id())) {
                        editingNodeId = node.id();
                    }
                }
                if (isDoubleClick) {
                    // Double-clicking still navigates INTO nested clusters even when the outer
                    // is locked — the user is just browsing, not editing.
                    if (node instanceof ClusterNode cluster) {
                        enterCluster(cluster);
                        return true;
                    }
                    // Editing the props panel only makes sense for editable contexts.
                    if (!locked) editingNodeId = node.id();
                    return true;
                }
                // Locked: selection only, no drag-to-move.
                if (!locked) canvas.startNodeDrag(node.id(), node.position(), world.x(), world.y());
                return true;
            }
            // After nodes, try edges — click an edge to select it (and then delete via Del/Backspace).
            Optional<Edge> edgeHit = HitTest.edgeAt(design, world.x(), world.y(), EDGE_HIT_RADIUS);
            if (edgeHit.isPresent()) {
                canvas.setSelectedEdge(edgeHit.get());
                return true;
            }
            // Empty canvas: clear selection (unless Ctrl preserves it) and start a marquee.
            if (!ctrl) canvas.clearSelection();
            canvas.startSelectionRect(world.x(), world.y());
            return true;
        }
        if (button == RIGHT_MOUSE_BUTTON) {
            Vec2 world = canvas.screenToWorld(mouseX, mouseY);
            Optional<Node> nodeHit = HitTest.nodeAt(design, world.x(), world.y());
            if (nodeHit.isPresent()) {
                Node node = nodeHit.get();
                if (!canvas.isSelected(node.id())) canvas.setSelectedNodeId(node.id());
                boolean multi = canvas.selectedNodes().size() > 1;
                boolean locked = inLockedCluster();
                List<ContextMenu.Option> opts = new java.util.ArrayList<>();
                // Inside a locked cluster: read-only menu. JEI lookups and "Enter cluster" still
                // make sense; structural edits do not.
                if (locked) {
                    if (node instanceof ClusterNode) {
                        opts.add(new ContextMenu.Option("Enter cluster", () -> {
                            if (node instanceof ClusterNode cl) enterCluster(cl);
                        }));
                    }
                    if (node instanceof RecipeNode rn && JeiBridge.getRuntime() != null) {
                        RecipeEntry entry = RebuildTrigger.index().byId(rn.recipeId());
                        if (entry != null) {
                            opts.add(new ContextMenu.Option("View in JEI",
                                    () -> JeiBridge.showRecipeInJei(entry.typeId(), entry.id())));
                        }
                    }
                    if (!opts.isEmpty()) contextMenu.show((int) mouseX, (int) mouseY, opts);
                    return true;
                }
                if (!multi) {
                    opts.add(new ContextMenu.Option("Edit", () -> {
                        canvas.setSelectedNodeId(node.id());
                        editingNodeId = node.id();
                    }));
                    if (node instanceof ClusterNode cn) {
                        String label = cn.kind() == ClusterNode.Kind.LOCKED ? "Unlock cluster" : "Lock cluster";
                        opts.add(new ContextMenu.Option(label, () -> {
                            ClusterNode.Kind newKind = cn.kind() == ClusterNode.Kind.LOCKED
                                    ? ClusterNode.Kind.USER : ClusterNode.Kind.LOCKED;
                            setDesign(design.withNodeReplaced(cn.id(), cn.withKind(newKind)));
                        }));
                    }
                }
                if (multi) {
                    opts.add(new ContextMenu.Option("Group into Cluster", this::groupSelectedIntoCluster));
                }
                opts.add(new ContextMenu.Option(multi ? "Clone all" : "Clone", () -> {
                    Design updated = design;
                    java.util.List<UUID> newIds = new java.util.ArrayList<>();
                    for (UUID id : canvas.selectedNodes()) {
                        Node src = updated.findNode(id);
                        if (src == null) continue;
                        Node copy = cloneNodeAtOffset(src, 24, 24);
                        updated = updated.withNodeAdded(copy);
                        newIds.add(copy.id());
                    }
                    setDesign(updated);
                    canvas.clearSelection();
                    for (UUID id : newIds) canvas.addToSelection(id);
                }));
                if (!multi && node instanceof RecipeNode rn && JeiBridge.getRuntime() != null) {
                    RecipeEntry entry = RebuildTrigger.index().byId(rn.recipeId());
                    if (entry != null) {
                        opts.add(new ContextMenu.Option("View in JEI",
                                () -> JeiBridge.showRecipeInJei(entry.typeId(), entry.id())));
                    }
                }
                opts.add(new ContextMenu.Option(multi ? "Delete all" : "Delete", () -> {
                    Design updated = design;
                    for (UUID id : canvas.selectedNodes()) {
                        updated = updated.withNodeRemoved(id);
                        if (id.equals(editingNodeId)) editingNodeId = null;
                    }
                    setDesign(updated);
                    canvas.clearSelection();
                }));
                contextMenu.show((int) mouseX, (int) mouseY, opts);
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
        // Drag-from-sidebar release — leftPanel's page decides whether it was a click or a drag,
        // and if dropped on the canvas, fires the drop callback that adds a node.
        if (leftPanel != null && leftPanel.mouseReleased(mouseX, mouseY, button)) return true;
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
                    // Carry fromBottom/toBottom flags so bottom-port loop edges round-trip through
                    // the codec correctly and the renderer picks the violet vertical curve.
                    setDesign(design.withEdgeAdded(new Edge(
                            from.node().id(), from.port().index(),
                            to.node().id(), to.port().index(),
                            0.0, from.bottom(), to.bottom())));
                }
                canvas.endEdgeDrag();
                return true;
            }
            if (canvas.isDrawingSelectionRect()) {
                // Finalize marquee: add every node whose top-left corner OR bottom-right falls
                // within the rectangle to the selection set.
                Vec2 s = canvas.selectionRectStart();
                Vec2 e = canvas.selectionRectEnd();
                double x1 = Math.min(s.x(), e.x());
                double y1 = Math.min(s.y(), e.y());
                double x2 = Math.max(s.x(), e.x());
                double y2 = Math.max(s.y(), e.y());
                for (Node n : design.nodes()) {
                    int w = dev.kima.cogwheel.client.ui.canvas.NodeRenderer.NODE_WIDTH;
                    int h = dev.kima.cogwheel.client.ui.canvas.NodeRenderer.heightOf(n);
                    double nx = n.position().x();
                    double ny = n.position().y();
                    if (nx + w >= x1 && nx <= x2 && ny + h >= y1 && ny <= y2) {
                        canvas.addToSelection(n.id());
                    }
                }
                canvas.endSelectionRect();
                return true;
            }
            canvas.endNodeDrag();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        // Always notify the panel of mouse movement so its drag tracker can promote a held click
        // into a drag (the panel itself only acts on release).
        if (leftPanel != null) leftPanel.mouseDragged(mouseX, mouseY);
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
            if (canvas.isDrawingSelectionRect()) {
                canvas.updateSelectionRect(world.x(), world.y());
                return true;
            }
            if (canvas.draggingNodeId() != null) {
                // Multi-node drag: move all selected nodes by the same world-space delta. Single
                // selection uses the absolute-position math so the cursor stays anchored to the
                // grabbed node exactly.
                if (canvas.selectedNodes().size() > 1 && canvas.isSelected(canvas.draggingNodeId())) {
                    double dxWorld = dragX / canvas.zoom();
                    double dyWorld = dragY / canvas.zoom();
                    Design updated = design;
                    for (UUID id : canvas.selectedNodes()) {
                        Node n = updated.findNode(id);
                        if (n == null) continue;
                        updated = updated.withNodeMoved(id,
                                new Vec2(n.position().x() + dxWorld, n.position().y() + dyWorld));
                    }
                    setDesign(updated);
                } else {
                    Vec2 newPos = canvas.currentDragTarget(world.x(), world.y());
                    setDesign(design.withNodeMoved(canvas.draggingNodeId(), newPos));
                }
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

    /** Apply the user's "Hide default Minecraft recipes" setting. When ON, recipes in the
     *  {@code minecraft} namespace are dropped before reaching the picker — so a modded factory
     *  doesn't get its picker drowned in vanilla compaction recipes. Off → identity. */
    private static List<RecipeEntry> filterHiddenRecipes(List<RecipeEntry> ways) {
        if (!dev.kima.cogwheel.settings.CogwheelSettings.get().bool(
                dev.kima.cogwheel.settings.CogwheelSettings.BoolKey.HIDE_VANILLA_RECIPES)) {
            return ways;
        }
        return ways.stream()
                .filter(e -> e.id() == null || !"minecraft".equals(e.id().getNamespace()))
                .toList();
    }

    /** Walks the cluster stack outermost → innermost, threading the effective RPM through each
     *  cluster's optional override. At the root: factory RPM. Inside a cluster: the cluster's
     *  rpmOverride if set, else inherited from the level above. Used as the {@code factoryRpm}
     *  parameter to {@link Solver#solve(Design, RecipeIndex, int)} so RPM cascades correctly
     *  when the user is viewing a cluster's inner design. */
    private int effectiveRpmForCurrentContext(Factory currentFactory) {
        int rpm = currentFactory != null ? currentFactory.rpm() : Factory.DEFAULT_RPM;
        // clusterStack is innermost-first; iterate descending (outermost-first) so each frame's
        // rpmOverride can override the running value.
        java.util.Iterator<ClusterFrame> it = clusterStack.descendingIterator();
        while (it.hasNext()) {
            ClusterFrame frame = it.next();
            Node n = frame.parentDesign().findNode(frame.clusterId());
            if (n instanceof ClusterNode cn && cn.rpmOverride().isPresent()) {
                rpm = cn.rpmOverride().get();
            }
        }
        return rpm;
    }

    /** Drop a recipe node at the cursor's drop position. Screen coords → world coords via the
     *  canvas's pan/zoom transform — drops always land where the user released, not at a
     *  fixed canvas center. */
    private void addRecipeNode(Item item, double screenX, double screenY) {
        var index = RebuildTrigger.index();
        List<RecipeEntry> ways = filterHiddenRecipes(index.waysToProduce(item));
        Vec2 placement = canvas.screenToWorld(screenX, screenY);

        if (ways.isEmpty()) {
            // No recipes — fall through to a source node so the drop does SOMETHING.
            addSourceAt(item, placement);
            return;
        }
        if (ways.size() == 1) {
            placeRecipeNode(ways.get(0), placement);
            return;
        }
        ItemStack stack = new ItemStack(item);
        Minecraft.getInstance().setScreen(new RecipePickerScreen(
                this,
                Component.literal(stack.getHoverName().getString()),
                ways,
                chosen -> placeRecipeNode(chosen, placement)));
    }

    /** Drop the right node type for the chosen recipe — sequenced_assembly recipes become a LOCKED
     *  Cluster (their multi-step shape doesn't fit a single RecipeNode), everything else stays a
     *  plain RecipeNode. */
    private void placeRecipeNode(RecipeEntry chosen, Vec2 placement) {
        Node node;
        if (chosen.typeId().toString().equals("create:sequenced_assembly")
                && net.neoforged.fml.ModList.get().isLoaded("create")) {
            Node cluster = dev.kima.cogwheel.recipe.adapter.create
                    .CreateSequencedAssemblyClusterBuilder.tryBuild(chosen.id(), placement);
            node = (cluster != null) ? cluster : RecipeNodeFactory.fromRecipeEntry(chosen, placement);
        } else {
            node = RecipeNodeFactory.fromRecipeEntry(chosen, placement);
        }
        setDesign(design.withNodeAdded(node));
        canvas.setSelectedNodeId(node.id());
    }

    private void addSourceNode(Item item, double sx, double sy) {
        addSourceAt(item, canvas.screenToWorld(sx, sy));
    }

    private void addSourceAt(Item item, Vec2 placement) {
        SourceNode node = RecipeNodeFactory.source(item, placement);
        setDesign(design.withNodeAdded(node));
        canvas.setSelectedNodeId(node.id());
    }

    private void addSinkNode(Item item, double sx, double sy) {
        SinkNode node = new SinkNode(UUID.randomUUID(), canvas.screenToWorld(sx, sy), new ItemStack(item));
        setDesign(design.withNodeAdded(node));
        canvas.setSelectedNodeId(node.id());
    }

    private void addSplitterNode(double sx, double sy) {
        SplitterNode node = new SplitterNode(UUID.randomUUID(),
                canvas.screenToWorld(sx, sy), SplitterNode.DEFAULT_OUTPUTS);
        setDesign(design.withNodeAdded(node));
        canvas.setSelectedNodeId(node.id());
    }

    private void addMergerNode(double sx, double sy) {
        MergerNode node = new MergerNode(UUID.randomUUID(),
                canvas.screenToWorld(sx, sy), MergerNode.DEFAULT_INPUTS);
        setDesign(design.withNodeAdded(node));
        canvas.setSelectedNodeId(node.id());
    }

    private void addClusterNode(double sx, double sy) {
        Design empty = new Design("Cluster", List.of(), List.of());
        ClusterNode node = new ClusterNode(UUID.randomUUID(),
                canvas.screenToWorld(sx, sy),
                "New Cluster", empty, ClusterNode.Kind.USER, 1);
        setDesign(design.withNodeAdded(node));
        canvas.setSelectedNodeId(node.id());
    }

    /**
     * Wrap every currently-selected node in a fresh USER cluster. Boundary edges are auto-promoted:
     * each selected node's input that was fed by something OUTSIDE the selection (or unwired) gets
     * a {@link SourceNode} inside the cluster + a redirected outer edge to the cluster's matching
     * input port. Each selected output that fed something outside gets a {@link SinkNode} inside +
     * a redirected outer edge from the cluster's matching output port.
     *
     * <p>This means dropping a "Group into Cluster" on the middle of a chain leaves the surrounding
     * wiring intact — items still flow through as if nothing changed, just visually wrapped.
     */
    private void groupSelectedIntoCluster() {
        java.util.Set<UUID> selected = canvas.selectedNodes();
        if (selected.size() < 2) return;

        java.util.LinkedHashMap<UUID, Node> originals = new java.util.LinkedHashMap<>();
        double cx = 0, cy = 0;
        for (Node n : design.nodes()) {
            if (selected.contains(n.id())) {
                originals.put(n.id(), n);
                cx += n.position().x();
                cy += n.position().y();
            }
        }
        if (originals.isEmpty()) return;
        cx /= originals.size();
        cy /= originals.size();

        List<Node> innerNodes = new java.util.ArrayList<>(originals.values());
        List<Edge> innerEdges = new java.util.ArrayList<>();
        for (Edge e : design.edges()) {
            if (selected.contains(e.from()) && selected.contains(e.to())) {
                innerEdges.add(e);
            }
        }

        // Pending boundary rewires — applied AFTER we build the cluster (we need cluster.id +
        // port index lookup). Records snapshotted on existing data; safe across the loop.
        record PendingInput(UUID externalSourceId, int externalSourcePort, UUID innerSourceId) {}
        record PendingOutput(UUID innerSinkId, UUID externalTargetId, int externalTargetPort) {}
        List<PendingInput> pendingInputs = new java.util.ArrayList<>();
        List<PendingOutput> pendingOutputs = new java.util.ArrayList<>();

        // Build a source node for every selected node's unconnected (in this selection) input.
        for (Node n : originals.values()) {
            for (dev.kima.cogwheel.model.Port p : n.inputs()) {
                final int pIdx = p.index();
                boolean internalLink = innerEdges.stream().anyMatch(e ->
                        e.to().equals(n.id()) && e.toPort() == pIdx);
                if (internalLink) continue;
                Edge externalIn = null;
                for (Edge e : design.edges()) {
                    if (e.to().equals(n.id()) && e.toPort() == pIdx && !selected.contains(e.from())) {
                        externalIn = e;
                        break;
                    }
                }
                ItemStack item = p.display();
                if (item.isEmpty()) continue; // wildcard ports (splitter/merger) skip
                UUID srcId = UUID.randomUUID();
                Vec2 srcPos = new Vec2(n.position().x() - 140, n.position().y() + pIdx * 28);
                innerNodes.add(new SourceNode(srcId, srcPos, item, SourceNode.Kind.MANUAL));
                innerEdges.add(new Edge(srcId, 0, n.id(), pIdx, 0.0));
                if (externalIn != null) {
                    pendingInputs.add(new PendingInput(externalIn.from(), externalIn.fromPort(), srcId));
                }
            }
        }
        // Build a sink for every selected node's output that fed something external.
        for (Node n : originals.values()) {
            for (dev.kima.cogwheel.model.Port p : n.outputs()) {
                final int pIdx = p.index();
                List<Edge> externalsOut = new java.util.ArrayList<>();
                for (Edge e : design.edges()) {
                    if (e.from().equals(n.id()) && e.fromPort() == pIdx && !selected.contains(e.to())) {
                        externalsOut.add(e);
                    }
                }
                if (externalsOut.isEmpty()) continue;
                ItemStack item = p.display();
                if (item.isEmpty()) continue;
                UUID sinkId = UUID.randomUUID();
                Vec2 sinkPos = new Vec2(n.position().x() + 220, n.position().y() + pIdx * 28);
                innerNodes.add(new SinkNode(sinkId, sinkPos, item));
                innerEdges.add(new Edge(n.id(), pIdx, sinkId, 0, 0.0));
                for (Edge ext : externalsOut) {
                    pendingOutputs.add(new PendingOutput(sinkId, ext.to(), ext.toPort()));
                }
            }
        }

        Design innerDesign = new Design("Cluster", List.copyOf(innerNodes), List.copyOf(innerEdges));
        ClusterNode cluster = new ClusterNode(UUID.randomUUID(),
                new Vec2(cx, cy), "New Cluster", innerDesign, ClusterNode.Kind.USER, 1);

        // Map inner Source/Sink IDs to the cluster's outer port indices (iteration-order-derived).
        java.util.Map<UUID, Integer> sourceToPort = new java.util.HashMap<>();
        java.util.Map<UUID, Integer> sinkToPort = new java.util.HashMap<>();
        int inIdx = 0, outIdx = 0;
        for (Node n : innerDesign.nodes()) {
            if (n instanceof SourceNode src) sourceToPort.put(src.id(), inIdx++);
            else if (n instanceof SinkNode sk) sinkToPort.put(sk.id(), outIdx++);
            else if (n instanceof OutputNode on) sinkToPort.put(on.id(), outIdx++);
        }

        Design updated = design;
        for (UUID id : selected) updated = updated.withNodeRemoved(id);
        updated = updated.withNodeAdded(cluster);
        for (PendingInput pi : pendingInputs) {
            Integer port = sourceToPort.get(pi.innerSourceId());
            if (port != null) {
                updated = updated.withEdgeAdded(new Edge(pi.externalSourceId(), pi.externalSourcePort(),
                        cluster.id(), port, 0.0));
            }
        }
        for (PendingOutput po : pendingOutputs) {
            Integer port = sinkToPort.get(po.innerSinkId());
            if (port != null) {
                updated = updated.withEdgeAdded(new Edge(cluster.id(), port,
                        po.externalTargetId(), po.externalTargetPort(), 0.0));
            }
        }
        setDesign(updated);
        canvas.clearSelection();
        canvas.setSelectedNodeId(cluster.id());
    }

    private void addLimiterNode(double sx, double sy) {
        LimiterNode node = new LimiterNode(UUID.randomUUID(),
                canvas.screenToWorld(sx, sy), LimiterNode.DEFAULT_LIMIT);
        setDesign(design.withNodeAdded(node));
        canvas.setSelectedNodeId(node.id());
    }

    private void addVoidNode(double sx, double sy) {
        VoidNode node = new VoidNode(UUID.randomUUID(),
                canvas.screenToWorld(sx, sy), VoidNode.DEFAULT_INPUTS);
        setDesign(design.withNodeAdded(node));
        canvas.setSelectedNodeId(node.id());
    }

    private void addLoopNode(double sx, double sy) {
        LoopNode node = new LoopNode(UUID.randomUUID(),
                canvas.screenToWorld(sx, sy), LoopNode.DEFAULT_LOOPS);
        setDesign(design.withNodeAdded(node));
        canvas.setSelectedNodeId(node.id());
    }

    /** Apply a power-source + RPM change to a factory. Doesn't touch its design — just rewrites
     *  the wrapping {@link Factory} record and flushes to disk. */
    private void applyFactoryPower(UUID factoryId, Optional<UUID> powerSourceId, int rpm) {
        Factory f = FactoryStore.get().findById(factoryId);
        if (f == null) return;
        Factory updated = f.withPowerSource(powerSourceId).withRpm(rpm);
        // Mutate via the store's internal list directly — FactoryStore doesn't expose a generic
        // "replace by id" helper, but updateCurrent does it for the current factory. For non-current
        // factories we have to nudge through the list.
        if (factoryId.equals(FactoryStore.get().currentId())) {
            // Keep design in sync; updateCurrent uses the new field set via withDesign.
            FactoryStore.get().replaceFactory(updated);
        } else {
            FactoryStore.get().replaceFactory(updated);
        }
        dev.kima.cogwheel.persistence.FactoryFileStore.saveFactory(updated);
    }

    private void addOutputNode(Item item, double sx, double sy) {
        OutputNode node = dev.kima.cogwheel.recipe.RecipeNodeFactory.output(item, canvas.screenToWorld(sx, sy));
        setDesign(design.withNodeAdded(node));
        canvas.setSelectedNodeId(node.id());
    }

    private void replaceEditingNode(Node updated) {
        if (editingNodeId == null) return;
        setDesign(design.withNodeReplaced(editingNodeId, updated));
    }

    /** Find the first (output, input) port pair between {@code from} and {@code to} that matches
     *  on item type (wildcard ports always pass) and isn't already wired. Returns the edge to add,
     *  or null if no compatible free pair exists. */
    private Edge trySmartConnect(Node from, Node to) {
        if (from.outputs().isEmpty() || to.inputs().isEmpty()) return null;
        for (int fp = 0; fp < from.outputs().size(); fp++) {
            ItemStack outItem = from.outputs().get(fp).display();
            for (int tp = 0; tp < to.inputs().size(); tp++) {
                if (hasIncomingEdge(to.id(), tp)) continue;
                ItemStack inItem = to.inputs().get(tp).display();
                boolean wildcard = outItem.isEmpty() || inItem.isEmpty();
                if (wildcard || outItem.getItem() == inItem.getItem()) {
                    return new Edge(from.id(), fp, to.id(), tp, 0.0);
                }
            }
        }
        return null;
    }

    private boolean hasIncomingEdge(UUID nodeId, int portIndex) {
        for (Edge e : design.edges()) {
            if (e.to().equals(nodeId) && e.toPort() == portIndex) return true;
        }
        return false;
    }

    private Node cloneNodeAtOffset(Node source, int dx, int dy) {
        Vec2 newPos = new Vec2(source.position().x() + dx, source.position().y() + dy);
        UUID newId = UUID.randomUUID();
        if (source instanceof RecipeNode rn) {
            // Use the full constructor so the clone preserves rpmOverride — the back-compat
            // 8-arg form silently drops it, which is a real source-of-confusion when the user
            // sees the original's RPM override but the clone is silently running at factory RPM.
            return new RecipeNode(newId, newPos, rn.recipeId(), rn.title(), rn.icon(),
                    rn.inputs(), rn.outputs(), rn.parallelism(), rn.rpmOverride());
        } else if (source instanceof SourceNode sn) {
            return new SourceNode(newId, newPos, sn.item(), sn.kind(), sn.externalRef());
        } else if (source instanceof SinkNode sn) {
            return new SinkNode(newId, newPos, sn.item());
        } else if (source instanceof SplitterNode sp) {
            return new SplitterNode(newId, newPos, sp.outputCount(), sp.mode());
        } else if (source instanceof MergerNode mn) {
            return new MergerNode(newId, newPos, mn.inputCount());
        } else if (source instanceof OutputNode on) {
            return new OutputNode(newId, newPos, on.item(), on.exportName());
        } else if (source instanceof LimiterNode ln) {
            return new LimiterNode(newId, newPos, ln.limitPerMin());
        } else if (source instanceof VoidNode vn) {
            return new VoidNode(newId, newPos, vn.inputCount());
        } else if (source instanceof LoopNode ln) {
            return new LoopNode(newId, newPos, ln.loopCount());
        } else if (source instanceof ClusterNode cn) {
            // Inner design is shared by reference — cluster contents are immutable records, so
            // both copies see the same subgraph until the user mutates either's innerDesign field.
            return new ClusterNode(newId, newPos, cn.label(), cn.innerDesign(), cn.kind(), cn.parallelism());
        }
        return source;
    }

    private void onSwapRecipeRequested() {
        Node selected = design.findNode(canvas.selectedNodeId());
        if (!(selected instanceof RecipeNode rn)) return;
        if (rn.outputs().isEmpty()) return;
        ItemStack primaryOutput = rn.outputs().get(0).display();
        if (primaryOutput.isEmpty()) return;

        List<RecipeEntry> ways = filterHiddenRecipes(
                RebuildTrigger.index().waysToProduce(primaryOutput.getItem()));
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
        // Esc while inside a cluster: pop back to parent design instead of closing the screen.
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && inCluster()) {
            exitCluster();
            return true;
        }
        // R/U on a hovered sidebar item → open JEI recipes / uses. Check this BEFORE vanilla
        // widget consumption so it works while the search box exists but isn't focused.
        if (leftPanel != null && leftPanel.isOpen()) {
            int mx = (int) Minecraft.getInstance().mouseHandler.xpos()
                    * this.width / Math.max(1, Minecraft.getInstance().getWindow().getScreenWidth());
            int my = (int) Minecraft.getInstance().mouseHandler.ypos()
                    * this.height / Math.max(1, Minecraft.getInstance().getWindow().getScreenHeight());
            if (leftPanel.contains(mx, my)
                    && (keyCode == GLFW.GLFW_KEY_R || keyCode == GLFW.GLFW_KEY_U)) {
                if (leftPanel.keyPressed(keyCode, scanCode, modifiers, mx, my)) return true;
            }
        }
        // Allow the EditBox / other vanilla widgets to consume keys first (typing in the search box
        // shouldn't trigger Ctrl+S or Delete).
        if (super.keyPressed(keyCode, scanCode, modifiers)) return true;

        // Delete / Backspace removes the selected edge or node. Blocked inside locked clusters.
        if ((keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) && inLockedCluster()) {
            return true; // consume so it doesn't bubble
        }
        if (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            Edge sEdge = canvas.selectedEdge();
            if (sEdge != null) {
                setDesign(design.withEdgeRemoved(sEdge));
                canvas.setSelectedEdge(null);
                return true;
            }
            if (!canvas.selectedNodes().isEmpty()) {
                Design updated = design;
                for (UUID id : canvas.selectedNodes()) {
                    updated = updated.withNodeRemoved(id);
                    if (id.equals(editingNodeId)) editingNodeId = null;
                }
                setDesign(updated);
                canvas.clearSelection();
                return true;
            }
            return false;
        }

        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        if (ctrl && keyCode == GLFW.GLFW_KEY_S) {
            boolean ok = FactoryFileStore.saveAll();
            showBanner(ok ? "Saved " + FactoryStore.get().all().size() + " factories" : "Save failed (see log)");
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_O) {
            int n = FactoryFileStore.loadAll();
            Factory now = FactoryStore.get().current();
            if (now != null) {
                this.design = now.design();
                canvas.clearSelection();
                canvas.setSelectedEdge(null);
                editingNodeId = null;
            }
            showBanner("Loaded " + n + " factories from disk");
            return true;
        }
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
