package dev.kima.cogwheel.client.ui.canvas;

import dev.kima.cogwheel.model.Edge;
import dev.kima.cogwheel.model.Vec2;

import java.util.UUID;

/**
 * Pan/zoom state for the editor viewport plus selection/drag bookkeeping. World-space coordinates
 * map to screen-space via {@code screen = world * zoom + pan}. Inputs (mouse) come in screen-space
 * and are converted with {@link #screenToWorld(double, double)}.
 *
 * <p>Zoom is clamped to [{@value #MIN_ZOOM}, {@value #MAX_ZOOM}]; "anchor under cursor stays under
 * cursor" zoom math is in {@link #zoomAt(double, double, double)}.
 *
 * <p>Selection / drag state lives here rather than in {@code EditorScreen} because the renderers
 * need to query it (e.g. {@code NodeRenderer} draws a highlight when {@link #selectedNodeId} matches).
 */
public final class Canvas {
    public static final double MIN_ZOOM = 0.25;
    public static final double MAX_ZOOM = 4.0;
    public static final double ZOOM_STEP = 1.1;

    private double panX;
    private double panY;
    private double zoom = 1.0;

    private UUID selectedNodeId;
    private Edge selectedEdge;
    private UUID draggingNodeId;
    /** Offset from a node's top-left corner to the drag-start point, in world space. */
    private double dragOffsetX;
    private double dragOffsetY;

    private HitTest.PortHit pendingEdgeFrom;
    /** While dragging a new edge, the cursor's world-space position (the floating edge endpoint). */
    private double pendingEdgeWorldX;
    private double pendingEdgeWorldY;

    public double panX() { return panX; }
    public double panY() { return panY; }
    public double zoom() { return zoom; }
    public UUID selectedNodeId() { return selectedNodeId; }
    public UUID draggingNodeId() { return draggingNodeId; }

    public void setSelectedNodeId(UUID id) {
        this.selectedNodeId = id;
        if (id != null) this.selectedEdge = null;
    }

    public Edge selectedEdge() { return selectedEdge; }

    public void setSelectedEdge(Edge edge) {
        this.selectedEdge = edge;
        if (edge != null) this.selectedNodeId = null;
    }

    public boolean isSelectedEdge(Edge edge) {
        return selectedEdge != null && selectedEdge.equals(edge);
    }

    public void clearSelection() {
        this.selectedNodeId = null;
        this.selectedEdge = null;
    }

    public boolean isSelected(UUID id) {
        return id != null && id.equals(selectedNodeId);
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

    public void pan(double dx, double dy) {
        panX += dx;
        panY += dy;
    }

    /**
     * Adjust zoom while keeping the world point under {@code (screenX, screenY)} fixed under that
     * same screen point — the standard "scroll-to-zoom toward cursor" behavior.
     *
     * @param scrollDelta positive zooms in, negative zooms out
     */
    public void zoomAt(double screenX, double screenY, double scrollDelta) {
        Vec2 worldBefore = screenToWorld(screenX, screenY);
        double factor = scrollDelta > 0 ? ZOOM_STEP : 1.0 / ZOOM_STEP;
        double newZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom * factor));
        if (newZoom == zoom) return;
        zoom = newZoom;
        // Re-derive pan so worldBefore still projects to (screenX, screenY).
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
