package dev.kima.cogwheel.client.ui.canvas;

import dev.kima.cogwheel.model.Node;
import dev.kima.cogwheel.model.Port;
import dev.kima.cogwheel.model.PortType;
import dev.kima.cogwheel.model.RecipeNode;
import dev.kima.cogwheel.model.Vec2;
import dev.kima.cogwheel.recipe.IngredientStack;
import dev.kima.cogwheel.recipe.RecipeEntry;
import dev.kima.cogwheel.recipe.RecipeIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/**
 * Renders a single node as a panel with header (icon + title) and rows of input/output ports.
 * Plain rectangle + 1px border — rounded corners can come in Phase 9 polish.
 */
public final class NodeRenderer {
    public static final int NODE_WIDTH = 116;
    public static final int HEADER_HEIGHT = 16;
    public static final int PORT_ROW_HEIGHT = 16;
    public static final int PORT_DOT_RADIUS = 3;
    /** Scale factor applied to item icons and node title text. Items render at 16 px natively;
     *  at 0.8 they're ~13 px — noticeably smaller without becoming illegible. Text is the vanilla
     *  9-px font scaled the same way. */
    private static final float CONTENT_SCALE = 0.8f;
    private static final int ICON_PX = Math.round(16 * CONTENT_SCALE);

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

    public static void render(GuiGraphics graphics, Node node, boolean selected,
                              PortDisplayResolver resolver, RecipeIndex index) {
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
            renderScaledItem(graphics, node.icon(), x + 2, y + 2);
        }
        int titleX = x + ICON_PX + 4;
        int scaledTitleMax = (int) ((w - titleX + x) / CONTENT_SCALE);
        renderScaledText(graphics, font, truncate(font, node.title().getString(), scaledTitleMax),
                titleX, y + 5, TEXT);

        // Resolve the recipe entry once for count-badge lookup. Null for non-RecipeNodes or when
        // the index doesn't have the recipe (mod removed, pre-rebuild, etc.) — falls back to no badge.
        RecipeEntry entry = (index != null && node instanceof RecipeNode rn) ? index.byId(rn.recipeId()) : null;

        // Input ports (left side). Use the resolver's effective display so wildcard ports
        // (Splitter/Merger) show whatever's flowing through them.
        for (Port p : node.inputs()) {
            ItemStack effective = resolver != null
                    ? resolver.inputDisplay(node.id(), p.index())
                    : p.display();
            int count = lookupInputCount(entry, p.index());
            renderPort(graphics, font, node, p, false, effective, count);
        }
        for (Port p : node.outputs()) {
            ItemStack effective = resolver != null
                    ? resolver.outputDisplay(node.id(), p.index())
                    : p.display();
            // Outputs already encode count via ItemStack.getCount(); badge from that.
            int count = lookupOutputCount(entry, p.index(), effective);
            renderPort(graphics, font, node, p, true, effective, count);
        }
    }

    private static int lookupInputCount(RecipeEntry entry, int portIndex) {
        if (entry == null) return 1;
        var inputs = entry.inputs();
        if (portIndex < 0 || portIndex >= inputs.size()) return 1;
        IngredientStack stack = inputs.get(portIndex);
        return Math.max(1, stack.count());
    }

    private static int lookupOutputCount(RecipeEntry entry, int portIndex, ItemStack effective) {
        if (entry != null) {
            var outputs = entry.outputs();
            if (portIndex >= 0 && portIndex < outputs.size()) {
                int c = outputs.get(portIndex).getCount();
                if (c > 0) return c;
            }
        }
        return Math.max(1, effective.getCount());
    }

    private static void renderPort(GuiGraphics graphics, Font font, Node node, Port port,
                                   boolean output, ItemStack effectiveDisplay, int count) {
        Vec2 center = portCenter(node, port.index(), output);
        int cx = (int) center.x();
        int cy = (int) center.y();
        int color = port.type() == PortType.FLUID ? PORT_FLUID : PORT_ITEM;

        // Filled square approximates a dot.
        int r = PORT_DOT_RADIUS;
        graphics.fill(cx - r, cy - r, cx + r, cy + r, color);

        // Item icon next to the dot, inside the node body. effectiveDisplay falls back to
        // Port.display() if the resolver couldn't infer anything (e.g. unconnected wildcard).
        int gap = 3;
        int iconX = output ? cx - PORT_DOT_RADIUS - gap - ICON_PX : cx + PORT_DOT_RADIUS + gap;
        int iconY = cy - ICON_PX / 2;
        ItemStack toRender = effectiveDisplay.isEmpty() ? port.display() : effectiveDisplay;
        if (!toRender.isEmpty()) {
            renderScaledItem(graphics, toRender, iconX, iconY);
            if (count > 1) {
                // renderItemDecorations writes at the item's high Z layer, so the badge sits over
                // the icon instead of vanishing beneath it.
                renderScaledItemDecorations(graphics, font, toRender, iconX, iconY, "×" + count);
            }
        }
    }

    private static void renderScaledItem(GuiGraphics graphics, ItemStack stack, int x, int y) {
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(CONTENT_SCALE, CONTENT_SCALE, 1f);
        graphics.renderItem(stack, 0, 0);
        pose.popPose();
    }

    private static void renderScaledItemDecorations(GuiGraphics graphics, Font font, ItemStack stack,
                                                    int x, int y, String text) {
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(CONTENT_SCALE, CONTENT_SCALE, 1f);
        graphics.renderItemDecorations(font, stack, 0, 0, text);
        pose.popPose();
    }

    private static void renderScaledText(GuiGraphics graphics, Font font, String text,
                                         int x, int y, int color) {
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(CONTENT_SCALE, CONTENT_SCALE, 1f);
        graphics.drawString(font, text, 0, 0, color, false);
        pose.popPose();
    }

    private static String truncate(Font font, String s, int maxWidth) {
        if (font.width(s) <= maxWidth) return s;
        while (s.length() > 0 && font.width(s + "…") > maxWidth) {
            s = s.substring(0, s.length() - 1);
        }
        return s + "…";
    }
}
