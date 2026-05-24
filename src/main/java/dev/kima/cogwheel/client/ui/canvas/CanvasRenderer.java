package dev.kima.cogwheel.client.ui.canvas;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kima.cogwheel.model.Design;
import dev.kima.cogwheel.model.Node;
import dev.kima.cogwheel.model.Vec2;
import dev.kima.cogwheel.recipe.source.RebuildTrigger;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Composes a single editor frame: background → grid → edges → nodes → overlay, clipped to the
 * canvas viewport rectangle.
 *
 * <p>The pose-stack transform (pan + scale) is applied for in-canvas content; the scissor restricts
 * drawing to the viewport so canvas content doesn't bleed into future side panels.
 */
public final class CanvasRenderer {
    /** Defaults — actual values pulled per frame from {@link dev.kima.cogwheel.settings.CogwheelSettings}. */
    public static final int BG_COLOR = 0xFF1A1E2A;
    public static final int BG_LOCKED = 0xFF222632;

    private CanvasRenderer() {}

    public static void render(GuiGraphics graphics, Canvas canvas, Design design,
                              int x, int y, int w, int h) {
        render(graphics, canvas, design, x, y, w, h, false, java.util.Map.of());
    }

    public static void render(GuiGraphics graphics, Canvas canvas, Design design,
                              int x, int y, int w, int h, boolean lockedBackground) {
        render(graphics, canvas, design, x, y, w, h, lockedBackground, java.util.Map.of());
    }

    public static void render(GuiGraphics graphics, Canvas canvas, Design design,
                              int x, int y, int w, int h, boolean lockedBackground,
                              java.util.Map<java.util.UUID, Double> nodeSuDraw) {
        int bg = dev.kima.cogwheel.settings.CogwheelSettings.c(
                lockedBackground
                        ? dev.kima.cogwheel.settings.CogwheelSettings.Key.CANVAS_BG_LOCKED
                        : dev.kima.cogwheel.settings.CogwheelSettings.Key.CANVAS_BG);
        graphics.fill(x, y, x + w, y + h, bg);

        // Resolve effective port displays once per frame so the Splitter/Merger wildcard ports
        // visually adopt whatever item is flowing through them.
        PortDisplayResolver resolver = new PortDisplayResolver(design);

        graphics.enableScissor(x, y, x + w, y + h);

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(canvas.panX(), canvas.panY(), 0);
        pose.scale((float) canvas.zoom(), (float) canvas.zoom(), 1);

        GridRenderer.render(graphics, canvas, x, y, w, h);

        EdgeRenderer.renderAll(graphics, design, canvas);
        if (canvas.pendingEdgeFrom() != null) {
            EdgeRenderer.renderPending(graphics, canvas.pendingEdgeFrom(),
                    canvas.pendingEdgeWorldX(), canvas.pendingEdgeWorldY());
        }
        // Flush so edges land on the framebuffer before nodes overdraw them at the ports.
        graphics.flush();

        for (Node node : design.nodes()) {
            NodeRenderer.render(graphics, node, canvas.isSelected(node.id()), resolver,
                    RebuildTrigger.index(), nodeSuDraw.getOrDefault(node.id(), 0.0));
        }

        // Marquee selection rectangle (world space, so it grows/shrinks with zoom naturally).
        if (canvas.isDrawingSelectionRect()) {
            Vec2 s = canvas.selectionRectStart();
            Vec2 e = canvas.selectionRectEnd();
            int x1 = (int) Math.min(s.x(), e.x());
            int y1 = (int) Math.min(s.y(), e.y());
            int x2 = (int) Math.max(s.x(), e.x());
            int y2 = (int) Math.max(s.y(), e.y());
            graphics.fill(x1, y1, x2, y2, 0x33FFCC55);
            graphics.fill(x1, y1, x2, y1 + 1, 0xFFFFCC55);
            graphics.fill(x1, y2 - 1, x2, y2, 0xFFFFCC55);
            graphics.fill(x1, y1, x1 + 1, y2, 0xFFFFCC55);
            graphics.fill(x2 - 1, y1, x2, y2, 0xFFFFCC55);
        }

        pose.popPose();
        graphics.disableScissor();
    }
}
