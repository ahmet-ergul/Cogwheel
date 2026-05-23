package dev.kima.cogwheel.client.ui.canvas;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kima.cogwheel.model.Design;
import dev.kima.cogwheel.model.Node;
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
    public static final int BG_COLOR = 0xFF1A1E2A;

    private CanvasRenderer() {}

    public static void render(GuiGraphics graphics, Canvas canvas, Design design,
                              int x, int y, int w, int h) {
        // Background panel (always under scissor — drawn before transform).
        graphics.fill(x, y, x + w, y + h, BG_COLOR);

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
            NodeRenderer.render(graphics, node, canvas.isSelected(node.id()), resolver, RebuildTrigger.index());
        }

        pose.popPose();
        graphics.disableScissor();
    }
}
