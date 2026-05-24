package dev.kima.cogwheel.client.ui.canvas;

import dev.kima.cogwheel.model.ClusterNode;
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
    /** Default scale factor applied to item icons and node title text. Overridable via the
     *  Settings page ({@code CONTENT_SCALE}). Items render at 16 px natively; at 0.8 they're
     *  ~13 px — readable without taking too much canvas. Reads live so settings tweaks apply
     *  without a restart. */
    private static float contentScale() {
        return dev.kima.cogwheel.settings.CogwheelSettings.get()
                .num(dev.kima.cogwheel.settings.CogwheelSettings.NumKey.CONTENT_SCALE);
    }
    private static int iconPx() { return Math.round(16 * contentScale()); }

    public static final int BG_COLOR    = 0xF21F2233;
    public static final int BG_DEEP     = 0xF21A1E2A; // slightly darker body bottom for depth
    public static final int BORDER      = 0xFF4A556E;
    public static final int BORDER_SELECTED = 0xFFFFCC55;
    public static final int GLOW_SELECTED = 0x66FFCC55; // soft outer halo when selected
    public static final int HEADER_BG_TOP    = 0xFF323A55;
    public static final int HEADER_BG_BOTTOM = 0xFF272D42;
    public static final int HEADER_SEP  = 0xFF1A1E2A;
    public static final int HEAT_HEATED      = 0xFFE8A040; // orange — Create "heated" condition
    public static final int HEAT_SUPERHEATED = 0xFFE85040; // red — Create "superheated" condition
    public static final int CLUSTER_BORDER   = 0xFFB89AE8; // violet — visual distinction for ClusterNode
    public static final int CLUSTER_HEADER_TOP    = 0xFF483F66;
    public static final int CLUSTER_HEADER_BOTTOM = 0xFF3A3252;
    public static final int SHADOW      = 0x40000000; // 25% black drop shadow
    public static final int PORT_ITEM   = 0xFFE8B86E;
    public static final int PORT_ITEM_RING = 0xFF9A7842;
    public static final int PORT_FLUID  = 0xFF6EBBE8;
    public static final int PORT_FLUID_RING = 0xFF457999;
    public static final int PORT_LOOP   = 0xFFB89AE8; // violet, matches CLUSTER_BORDER family
    public static final int PORT_LOOP_RING = 0xFF5E4D7A;
    public static final int TEXT        = 0xFFEAEAEA;

    private NodeRenderer() {}

    public static int heightOf(Node node) {
        // LoopNode is a compact gate — no input/output rows. Reserve room for: header (title) +
        // one row for the "×N" loop-count line below. Bottom ports here render at the TOP edge,
        // so no extra bottom padding needed.
        if (node instanceof dev.kima.cogwheel.model.LoopNode) {
            return HEADER_HEIGHT + PORT_ROW_HEIGHT + 6;
        }
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

    /** World-space center of one of a node's loop-control ports. Returns {@code null} if the index
     *  is out of range for this node's {@link Node#bottomPorts()} list.
     *
     *  <p>Geometry: a {@link dev.kima.cogwheel.model.LoopNode} renders its two ports on the TOP
     *  edge (left + right corners) because it visually sits BELOW the chain it gates. All other
     *  gateable nodes render their single port at the BOTTOM-CENTER. */
    public static Vec2 bottomPortCenter(Node node, int portIndex) {
        java.util.List<Port> ports = node.bottomPorts();
        if (portIndex < 0 || portIndex >= ports.size()) return null;
        Vec2 pos = node.position();
        if (node instanceof dev.kima.cogwheel.model.LoopNode) {
            // Two ports on the top edge — index 0 = left, index 1 = right.
            int inset = 10;
            int x = (int) pos.x() + (portIndex == 0 ? inset : NODE_WIDTH - inset);
            int y = (int) pos.y();
            return new Vec2(x, y);
        }
        // Default: single port at bottom-center.
        int x = (int) pos.x() + NODE_WIDTH / 2;
        int y = (int) pos.y() + heightOf(node);
        return new Vec2(x, y);
    }

    /** Back-compat overload with no SU info. */
    public static void render(GuiGraphics graphics, Node node, boolean selected,
                              PortDisplayResolver resolver, RecipeIndex index) {
        render(graphics, node, selected, resolver, index, 0.0);
    }

    public static void render(GuiGraphics graphics, Node node, boolean selected,
                              PortDisplayResolver resolver, RecipeIndex index, double suDraw) {
        Font font = Minecraft.getInstance().font;
        int x = (int) node.position().x();
        int y = (int) node.position().y();
        int h = heightOf(node);
        int w = NODE_WIDTH;

        // Soft drop shadow — two offset rects for a 2-pixel layered shadow.
        graphics.fill(x + 2, y + h, x + w + 2, y + h + 2, SHADOW);
        graphics.fill(x + w, y + 2, x + w + 2, y + h, SHADOW);

        // Selected halo — a 2-px-wider tinted rect behind the node + chamfered corners.
        if (selected) {
            graphics.fill(x - 2, y - 2, x + w + 2, y + h + 2, GLOW_SELECTED);
        }

        // Body with chamfered corners: 1-px corners knocked out so it looks faintly rounded.
        // Colors pulled from settings so the user can recolor everything from the Settings page.
        var st = dev.kima.cogwheel.settings.CogwheelSettings.get();
        int nodeBg = st.color(dev.kima.cogwheel.settings.CogwheelSettings.Key.NODE_BG);
        graphics.fill(x + 1, y, x + w - 1, y + h, BG_DEEP);
        graphics.fill(x, y + 1, x + w, y + h - 1, nodeBg);

        boolean isCluster = node instanceof ClusterNode;
        int defaultBorder = isCluster
                ? st.color(dev.kima.cogwheel.settings.CogwheelSettings.Key.CLUSTER_BORDER)
                : st.color(dev.kima.cogwheel.settings.CogwheelSettings.Key.NODE_BORDER);
        int borderColor = selected
                ? st.color(dev.kima.cogwheel.settings.CogwheelSettings.Key.NODE_BORDER_SEL)
                : defaultBorder;
        int headerTop = isCluster
                ? st.color(dev.kima.cogwheel.settings.CogwheelSettings.Key.CLUSTER_HDR_TOP)
                : st.color(dev.kima.cogwheel.settings.CogwheelSettings.Key.NODE_HEADER_TOP);
        int headerBottom = isCluster
                ? st.color(dev.kima.cogwheel.settings.CogwheelSettings.Key.CLUSTER_HDR_BOT)
                : st.color(dev.kima.cogwheel.settings.CogwheelSettings.Key.NODE_HEADER_BOT);

        graphics.fill(x + 1, y, x + w - 1, y + 1, borderColor);                 // top
        graphics.fill(x + 1, y + h - 1, x + w - 1, y + h, borderColor);         // bottom
        graphics.fill(x, y + 1, x + 1, y + h - 1, borderColor);                 // left
        graphics.fill(x + w - 1, y + 1, x + w, y + h - 1, borderColor);         // right

        // Header — two-tone gradient (lighter top → darker bottom) + bottom separator line.
        graphics.fill(x + 1, y + 1, x + w - 1, y + HEADER_HEIGHT / 2 + 1, headerTop);
        graphics.fill(x + 1, y + HEADER_HEIGHT / 2 + 1, x + w - 1, y + HEADER_HEIGHT, headerBottom);
        graphics.fill(x + 1, y + HEADER_HEIGHT, x + w - 1, y + HEADER_HEIGHT + 1, HEADER_SEP);

        if (!node.icon().isEmpty()) {
            renderScaledItem(graphics, node.icon(), x + 2, y + 2);
        }
        int titleX = x + iconPx() + 4;
        // Tiny lock glyph on locked clusters — reserved space on the right edge of the header.
        int rightReserve = 0;
        if (node instanceof ClusterNode cn && cn.kind() == ClusterNode.Kind.LOCKED) {
            rightReserve = 10;
            int lockX = x + w - 9;
            graphics.drawString(font, "🔒", lockX, y + 4, 0xFFEAEAEA, false);
        }
        int scaledTitleMax = (int) ((w - titleX + x - rightReserve) / contentScale());
        renderScaledText(graphics, font, truncate(font, node.title().getString(), scaledTitleMax),
                titleX, y + 5, TEXT);

        // Resolve the recipe entry once for count-badge lookup. Null for non-RecipeNodes or when
        // the index doesn't have the recipe (mod removed, pre-rebuild, etc.) — falls back to no badge.
        RecipeEntry entry = (index != null && node instanceof RecipeNode rn) ? index.byId(rn.recipeId()) : null;

        // Heat stripe — colored bar replacing the header separator when the recipe needs heat.
        if (entry != null && entry.heatRequirement().isPresent()) {
            int heatColor = "SUPERHEATED".equals(entry.heatRequirement().get())
                    ? HEAT_SUPERHEATED : HEAT_HEATED;
            graphics.fill(x + 1, y + HEADER_HEIGHT - 1, x + w - 1, y + HEADER_HEIGHT + 1, heatColor);
        }

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

        // Loop-control ports — small violet dots. Geometry per port:
        //  - LoopNode: top-left + top-right corners
        //  - All others: bottom-center
        for (int i = 0; i < node.bottomPorts().size(); i++) {
            renderBottomPortAt(graphics, node, i);
        }

        // SU draw badge: small "SU N" tag just below the bottom edge of the node, drawn for any
        // Create-backed node the solver attributed a positive SU value to. Makes per-node power
        // contributions visible at a glance without opening the properties panel.
        if (suDraw > 0) {
            String suStr = "SU " + formatSuShort(suDraw);
            // Scale matches the rest of the node text scale so badges shrink/grow with CONTENT_SCALE.
            float s = contentScale();
            int textWidth = Math.round(font.width(suStr) * s);
            int badgeX = x + (w - textWidth) / 2;
            int badgeY = y + h + 2;
            // Subtle dark background pill behind the text for legibility over the grid pattern.
            graphics.fill(badgeX - 2, badgeY - 1, badgeX + textWidth + 2, badgeY + Math.round(9 * s) - 1,
                    0xCC131726);
            renderScaledText(graphics, font, suStr, badgeX, badgeY, 0xFFE8B86E);
        }

        // LoopNode also shows the iteration count "×N" below its title line.
        if (node instanceof dev.kima.cogwheel.model.LoopNode loop) {
            String countStr = "×" + loop.loopCount();
            int countWidth = (int) (font.width(countStr) * contentScale());
            int cx = x + (w - countWidth) / 2;
            int cy = y + HEADER_HEIGHT + 4;
            renderScaledText(graphics, font, countStr, cx, cy, TEXT);
        }
    }

    private static String formatSuShort(double su) {
        if (su >= 1_000_000) return String.format("%.2fM", su / 1_000_000);
        if (su >= 1_000) return String.format("%.1fk", su / 1_000);
        return String.format("%.0f", su);
    }

    private static void renderBottomPortAt(GuiGraphics graphics, Node node, int portIndex) {
        Vec2 center = bottomPortCenter(node, portIndex);
        if (center == null) return;
        int cx = (int) center.x();
        int cy = (int) center.y();
        int r = PORT_DOT_RADIUS;
        graphics.fill(cx - r + 1, cy - r, cx + r - 1, cy + r, PORT_LOOP_RING);
        graphics.fill(cx - r, cy - r + 1, cx + r, cy + r - 1, PORT_LOOP_RING);
        graphics.fill(cx - r + 1, cy - r + 1, cx + r - 1, cy + r - 1, PORT_LOOP);
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
        boolean fluid = port.type() == PortType.FLUID;
        int fill = fluid ? PORT_FLUID : PORT_ITEM;
        int ring = fluid ? PORT_FLUID_RING : PORT_ITEM_RING;

        // Chamfered round-ish dot: outer ring (chamfered square) + inner filled square.
        int r = PORT_DOT_RADIUS;
        graphics.fill(cx - r + 1, cy - r, cx + r - 1, cy + r, ring);
        graphics.fill(cx - r, cy - r + 1, cx + r, cy + r - 1, ring);
        graphics.fill(cx - r + 1, cy - r + 1, cx + r - 1, cy + r - 1, fill);

        // Item icon next to the dot, inside the node body. effectiveDisplay falls back to
        // Port.display() if the resolver couldn't infer anything (e.g. unconnected wildcard).
        int gap = 3;
        int ipx = iconPx();
        int iconX = output ? cx - PORT_DOT_RADIUS - gap - ipx : cx + PORT_DOT_RADIUS + gap;
        int iconY = cy - ipx / 2;
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
        pose.scale(contentScale(), contentScale(), 1f);
        graphics.renderItem(stack, 0, 0);
        pose.popPose();
    }

    private static void renderScaledItemDecorations(GuiGraphics graphics, Font font, ItemStack stack,
                                                    int x, int y, String text) {
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(contentScale(), contentScale(), 1f);
        graphics.renderItemDecorations(font, stack, 0, 0, text);
        pose.popPose();
    }

    private static void renderScaledText(GuiGraphics graphics, Font font, String text,
                                         int x, int y, int color) {
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(contentScale(), contentScale(), 1f);
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
