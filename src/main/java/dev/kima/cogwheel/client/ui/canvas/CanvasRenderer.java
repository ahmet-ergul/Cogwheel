package dev.kima.cogwheel.client.ui.canvas;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kima.cogwheel.model.Design;
import dev.kima.cogwheel.model.Node;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Composes a single editor frame: background → grid → edges → nodes → overlay, clipped to the
 * canvas viewport rectangle.
 *
 * <p>The pose-stack transform (pan + scale) is applied for in-canvas content; the scissor restricts
 * drawing to the viewport so canvas content doesn't bleed into future side panels.
 */
public final class CanvasRenderer {
    public static final int BG_COLOR = 0xFF1A1E2A;

    private CanvasRenderer() {}

    public static void render(GuiGraphics graphics, Canvas canvas, Design design,
                              int x, int y, int w, int h) {
        // Background panel (always under scissor — drawn before transform).
        graphics.fill(x, y, x + w, y + h, BG_COLOR);

        graphics.enableScissor(x, y, x + w, y + h);

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(canvas.panX(), canvas.panY(), 0);
        pose.scale((float) canvas.zoom(), (float) canvas.zoom(), 1);

        // Grid in world-space; we pass screen-space viewport so it can compute visible bounds.
        // GridRenderer needs UNTRANSFORMED world coordinates for the visible-bounds math, but draws
        // via graphics.fill which IS affected by the current pose. So compute bounds from screen
        // coords (passed in) and reuse the pose for the fill calls.
        GridRenderer.render(graphics, canvas, x, y, w, h);

        EdgeRenderer.renderAll(graphics, design);
        if (canvas.pendingEdgeFrom() != null) {
            EdgeRenderer.renderPending(graphics, canvas.pendingEdgeFrom(),
                    canvas.pendingEdgeWorldX(), canvas.pendingEdgeWorldY());
        }
        // Flush so edges land on the framebuffer before nodes overdraw them at the ports.
        graphics.flush();

        for (Node node : design.nodes()) {
            NodeRenderer.render(graphics, node, canvas.isSelected(node.id()));
        }

        pose.popPose();
        graphics.disableScissor();
    }
}
