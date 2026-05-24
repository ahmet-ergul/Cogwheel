package dev.kima.cogwheel.client.ui.canvas;

import dev.kima.cogwheel.model.Edge;
import dev.kima.cogwheel.model.Vec2;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Pan/zoom state for the editor viewport plus selection/drag bookkeeping. World-space coordinates
 * map to screen-space via {@code screen = world * zoom + pan}. Inputs (mouse) come in screen-space
 * and are converted with {@link #screenToWorld(double, double)}.
 *
 * <p>Zoom is clamped to [{@value #MIN_ZOOM}, {@value #MAX_ZOOM}]; "anchor under cursor stays under
 * cursor" zoom math is in {@link #zoomAt(double, double, double)}.
 *
 * <p>Selection supports multi-select via a {@link LinkedHashSet} — insertion order preserved so
 * the first-added node is the "primary" (used by single-node features like the properties panel).
 * The marquee selection rectangle is also stored here so {@link CanvasRenderer} can draw it.
 */
public final class Canvas {
    public static final double MIN_ZOOM = 0.25;
    public static final double MAX_ZOOM = 4.0;
    public static final double ZOOM_STEP = 1.1;

    private double panX;
    private double panY;
    private double zoom = 1.0;

    private final LinkedHashSet<UUID> selectedNodes = new LinkedHashSet<>();
    private Edge selectedEdge;
    private UUID draggingNodeId;
    /** Offset from a node's top-left corner to the drag-start point, in world space. Only used
     *  for single-node drag math; multi-node drag uses delta-based motion in {@code EditorScreen}. */
    private double dragOffsetX;
    private double dragOffsetY;

    private HitTest.PortHit pendingEdgeFrom;
    private double pendingEdgeWorldX;
    private double pendingEdgeWorldY;

    /** Marquee selection rectangle in world space (null when not being drawn). */
    private Vec2 selectionRectStart;
    private Vec2 selectionRectEnd;

    public double panX() { return panX; }
    public double panY() { return panY; }
    public double zoom() { return zoom; }

    /** Primary selection (first node in selection order), for callers that need a single id. */
    public UUID selectedNodeId() {
        return selectedNodes.isEmpty() ? null : selectedNodes.iterator().next();
    }

    public Set<UUID> selectedNodes() {
        return Collections.unmodifiableSet(selectedNodes);
    }

    public UUID draggingNodeId() { return draggingNodeId; }

    public void setSelectedNodeId(UUID id) {
        selectedNodes.clear();
        if (id != null) {
            selectedNodes.add(id);
            selectedEdge = null;
        }
    }

    public void addToSelection(UUID id) {
        if (id == null) return;
        selectedNodes.add(id);
        selectedEdge = null;
    }

    public void toggleSelection(UUID id) {
        if (id == null) return;
        if (!selectedNodes.remove(id)) selectedNodes.add(id);
        if (!selectedNodes.isEmpty()) selectedEdge = null;
    }

    public Edge selectedEdge() { return selectedEdge; }

    public void setSelectedEdge(Edge edge) {
        this.selectedEdge = edge;
        if (edge != null) selectedNodes.clear();
    }

    public boolean isSelectedEdge(Edge edge) {
        return selectedEdge != null && selectedEdge.equals(edge);
    }

    public void clearSelection() {
        selectedNodes.clear();
        selectedEdge = null;
    }

    public boolean isSelected(UUID id) {
        return id != null && selectedNodes.contains(id);
    }

    /** Begin a node-move drag. {@code worldX/worldY} is the world-space mouse position at drag start. */
    public void startNodeDrag(UUID nodeId, Vec2 nodePos, double worldX, double worldY) {
        this.draggingNodeId = nodeId;
        this.dragOffsetX = worldX - nodePos.x();
        this.dragOffsetY = worldY - nodePos.y();
    }

    /** Computes the new top-left position for the dragging node given the current world-space mouse position. */
    public Vec2 currentDragTarget(double worldX, double worldY) {
        return new Vec2(worldX - dragOffsetX, worldY - dragOffsetY);
    }

    public void endNodeDrag() {
        this.draggingNodeId = null;
    }

    public HitTest.PortHit pendingEdgeFrom() { return pendingEdgeFrom; }
    public double pendingEdgeWorldX() { return pendingEdgeWorldX; }
    public double pendingEdgeWorldY() { return pendingEdgeWorldY; }

    public void startEdgeDrag(HitTest.PortHit from, double worldX, double worldY) {
        this.pendingEdgeFrom = from;
        this.pendingEdgeWorldX = worldX;
        this.pendingEdgeWorldY = worldY;
    }

    public void updatePendingEdge(double worldX, double worldY) {
        this.pendingEdgeWorldX = worldX;
        this.pendingEdgeWorldY = worldY;
    }

    public void endEdgeDrag() {
        this.pendingEdgeFrom = null;
    }

    /** Begin drawing a marquee selection rectangle. */
    public void startSelectionRect(double worldX, double worldY) {
        this.selectionRectStart = new Vec2(worldX, worldY);
        this.selectionRectEnd = new Vec2(worldX, worldY);
    }

    public void updateSelectionRect(double worldX, double worldY) {
        this.selectionRectEnd = new Vec2(worldX, worldY);
    }

    public void endSelectionRect() {
        this.selectionRectStart = null;
        this.selectionRectEnd = null;
    }

    public boolean isDrawingSelectionRect() {
        return selectionRectStart != null;
    }

    public Vec2 selectionRectStart() { return selectionRectStart; }
    public Vec2 selectionRectEnd() { return selectionRectEnd; }

    public void pan(double dx, double dy) {
        panX += dx;
        panY += dy;
    }

    public void zoomAt(double screenX, double screenY, double scrollDelta) {
        Vec2 worldBefore = screenToWorld(screenX, screenY);
        double factor = scrollDelta > 0 ? ZOOM_STEP : 1.0 / ZOOM_STEP;
        double newZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom * factor));
        if (newZoom == zoom) return;
        zoom = newZoom;
        panX = screenX - worldBefore.x() * zoom;
        panY = screenY - worldBefore.y() * zoom;
    }

    public Vec2 screenToWorld(double screenX, double screenY) {
        return new Vec2((screenX - panX) / zoom, (screenY - panY) / zoom);
    }

    public Vec2 worldToScreen(double worldX, double worldY) {
        return new Vec2(worldX * zoom + panX, worldY * zoom + panY);
    }
}
