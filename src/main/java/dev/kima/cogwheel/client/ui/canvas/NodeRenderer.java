package dev.kima.cogwheel.client.ui.canvas;

import dev.kima.cogwheel.model.Node;
import dev.kima.cogwheel.model.Port;
import dev.kima.cogwheel.model.PortType;
import dev.kima.cogwheel.model.Vec2;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/**
 * Renders a single node as a panel with header (icon + title) and rows of input/output ports.
 * Plain rectangle + 1px border — rounded corners can come in Phase 9 polish.
 */
public final class NodeRenderer {
    public static final int NODE_WIDTH = 144;
    public static final int HEADER_HEIGHT = 22;
    public static final int PORT_ROW_HEIGHT = 18;
    public static final int PORT_DOT_RADIUS = 4;

    public static final int BG_COLOR    = 0xEE1F2233;
    public static final int BORDER      = 0xFF54607A;
    public static final int BORDER_SELECTED = 0xFFFFCC55;
    public static final int HEADER_BG   = 0xFF2A3148;
    public static final int PORT_ITEM   = 0xFFE8B86E;
    public static final int PORT_FLUID  = 0xFF6EBBE8;
    public static final int TEXT        = 0xFFEAEAEA;

    private NodeRenderer() {}

    public static int heightOf(Node node) {
        int portRows = Math.max(node.inputs().size(), node.outputs().size());
        return HEADER_HEIGHT + Math.max(1, portRows) * PORT_ROW_HEIGHT + 6;
    }

    /**
     * World-space position of a port's center dot, suitable for routing edges.
     */
    public static Vec2 portCenter(Node node, int portIndex, boolean output) {
        Vec2 pos = node.position();
        int x = (int) pos.x() + (output ? NODE_WIDTH : 0);
        int y = (int) pos.y() + HEADER_HEIGHT + 6 + portIndex * PORT_ROW_HEIGHT + PORT_ROW_HEIGHT / 2;
        return new Vec2(x, y);
    }

    public static void render(GuiGraphics graphics, Node node, boolean selected, PortDisplayResolver resolver) {
        Font font = Minecraft.getInstance().font;
        int x = (int) node.position().x();
        int y = (int) node.position().y();
        int h = heightOf(node);
        int w = NODE_WIDTH;
        int borderColor = selected ? BORDER_SELECTED : BORDER;
        int borderThickness = selected ? 2 : 1;

        // Body + border.
        graphics.fill(x, y, x + w, y + h, BG_COLOR);
        graphics.fill(x - borderThickness, y - borderThickness, x + w + borderThickness, y, borderColor);  // top
        graphics.fill(x - borderThickness, y + h, x + w + borderThickness, y + h + borderThickness, borderColor);  // bottom
        graphics.fill(x - borderThickness, y, x, y + h, borderColor);  // left
        graphics.fill(x + w, y, x + w + borderThickness, y + h, borderColor);  // right

        // Header.
        graphics.fill(x, y, x + w, y + HEADER_HEIGHT, HEADER_BG);
        if (!node.icon().isEmpty()) {
            graphics.renderItem(node.icon(), x + 3, y + 3);
        }
        graphics.drawString(font, truncate(font, node.title().getString(), w - 22), x + 22, y + 7, TEXT, false);

        // Input ports (left side). Use the resolver's effective display so wildcard ports
        // (Splitter/Merger) show whatever's flowing through them.
        for (Port p : node.inputs()) {
            ItemStack effective = resolver != null
                    ? resolver.inputDisplay(node.id(), p.index())
                    : p.display();
            renderPort(graphics, node, p, false, effective);
        }
        for (Port p : node.outputs()) {
            ItemStack effective = resolver != null
                    ? resolver.outputDisplay(node.id(), p.index())
                    : p.display();
            renderPort(graphics, node, p, true, effective);
        }
    }

    private static void renderPort(GuiGraphics graphics, Node node, Port port, boolean output, ItemStack effectiveDisplay) {
        Vec2 center = portCenter(node, port.index(), output);
        int cx = (int) center.x();
        int cy = (int) center.y();
        int color = port.type() == PortType.FLUID ? PORT_FLUID : PORT_ITEM;

        // Filled square approximates a dot.
        int r = PORT_DOT_RADIUS;
        graphics.fill(cx - r, cy - r, cx + r, cy + r, color);

        // Item icon next to the dot, inside the node body. effectiveDisplay falls back to
        // Port.display() if the resolver couldn't infer anything (e.g. unconnected wildcard).
        int iconX = output ? cx - 22 : cx + 6;
        ItemStack toRender = effectiveDisplay.isEmpty() ? port.display() : effectiveDisplay;
        if (!toRender.isEmpty()) {
            graphics.renderItem(toRender, iconX, cy - 8);
        }
    }

    private static String truncate(Font font, String s, int maxWidth) {
        if (font.width(s) <= maxWidth) return s;
        while (s.length() > 0 && font.width(s + "…") > maxWidth) {
            s = s.substring(0, s.length() - 1);
        }
        return s + "…";
    }
}
