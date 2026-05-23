package dev.kima.cogwheel.client.ui.panel;

import dev.kima.cogwheel.model.Node;
import dev.kima.cogwheel.model.Port;
import dev.kima.cogwheel.model.RecipeNode;
import dev.kima.cogwheel.model.SinkNode;
import dev.kima.cogwheel.model.SourceNode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

/**
 * Right sidebar showing properties for the currently selected node.
 *
 * <ul>
 *   <li>{@link SourceNode}: a Kind cycle button (MANUAL / EXTERNAL_FACTORY / DROP / INFINITE).</li>
 *   <li>{@link RecipeNode}: parallelism slider (1–256, linear), swap-recipe button, inputs/outputs.</li>
 *   <li>{@link SinkNode}: just the item; nothing to configure yet.</li>
 * </ul>
 *
 * <p>Renders manually (no vanilla widgets) so we don't have to wrestle the screen widget lifecycle
 * every time the selection changes. Mouse events come in via {@link #mouseClicked} /
 * {@link #mouseDragged} / {@link #mouseReleased}.
 */
public final class PropertiesPanel {
    public static final int WIDTH = 180;
    private static final int PADDING = 6;
    private static final int HEADER_HEIGHT = 22;
    private static final int ROW_HEIGHT = 14;
    private static final int SLIDER_HEIGHT = 10;
    private static final int BUTTON_HEIGHT = 16;

    private static final int BG_COLOR = 0xFF131726;
    private static final int BORDER = 0xFF2A3148;
    private static final int HEADER_BG = 0xFF2A3148;
    private static final int TEXT = 0xFFEAEAEA;
    private static final int TEXT_DIM = 0xFFA8A8B8;
    private static final int SLIDER_TRACK = 0xFF2A3148;
    private static final int SLIDER_FILL = 0xFFE8B86E;
    private static final int BUTTON_BG = 0xFF2A3148;
    private static final int BUTTON_HOVER_BG = 0xFF3A4868;

    private static final int PARALLELISM_MIN = 1;
    private static final int PARALLELISM_MAX = 256;

    private final Consumer<Node> onNodeUpdated;
    private final Runnable onSwapRecipe;

    private Node selected;
    private int panelX;
    private int panelY;
    private int panelH;
    private boolean draggingSlider;

    public PropertiesPanel(Consumer<Node> onNodeUpdated, Runnable onSwapRecipe) {
        this.onNodeUpdated = onNodeUpdated;
        this.onSwapRecipe = onSwapRecipe;
    }

    public void setSelected(Node node) {
        this.selected = node;
    }

    public void setLayout(int x, int y, int h) {
        this.panelX = x;
        this.panelY = y;
        this.panelH = h;
    }

    public boolean contains(double sx, double sy) {
        return sx >= panelX && sx <= panelX + WIDTH && sy >= panelY && sy <= panelY + panelH;
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(panelX, panelY, panelX + WIDTH, panelY + panelH, BG_COLOR);
        graphics.fill(panelX - 1, panelY, panelX, panelY + panelH, BORDER);

        Font font = Minecraft.getInstance().font;
        if (selected == null) {
            graphics.drawString(font, "Select a node",
                    panelX + PADDING, panelY + PADDING + 4, TEXT_DIM, false);
            return;
        }

        // Header.
        int hx = panelX;
        int hy = panelY;
        graphics.fill(hx, hy, hx + WIDTH, hy + HEADER_HEIGHT, HEADER_BG);
        if (!selected.icon().isEmpty()) {
            graphics.renderItem(selected.icon(), hx + PADDING, hy + 3);
        }
        graphics.drawString(font,
                truncate(font, selected.title().getString(), WIDTH - 24 - PADDING * 2),
                hx + PADDING + 20, hy + 7, TEXT, false);

        int contentY = panelY + HEADER_HEIGHT + PADDING;
        if (selected instanceof SourceNode src) {
            renderSource(graphics, font, src, contentY, mouseX, mouseY);
        } else if (selected instanceof RecipeNode rn) {
            renderRecipe(graphics, font, rn, contentY, mouseX, mouseY);
        } else if (selected instanceof SinkNode sn) {
            renderSink(graphics, font, sn, contentY);
        }
    }

    private void renderSource(GuiGraphics graphics, Font font, SourceNode src, int y, int mouseX, int mouseY) {
        graphics.drawString(font, "Kind", panelX + PADDING, y, TEXT_DIM, false);
        int btnY = y + ROW_HEIGHT;
        drawButton(graphics, panelX + PADDING, btnY, WIDTH - PADDING * 2, BUTTON_HEIGHT,
                src.kind().name(), mouseX, mouseY);

        int itemY = btnY + BUTTON_HEIGHT + PADDING * 2;
        graphics.drawString(font, "Item", panelX + PADDING, itemY, TEXT_DIM, false);
        graphics.renderItem(src.item(), panelX + PADDING, itemY + ROW_HEIGHT);
        graphics.drawString(font, src.item().getHoverName().getString(),
                panelX + PADDING + 20, itemY + ROW_HEIGHT + 4, TEXT, false);
    }

    private void renderRecipe(GuiGraphics graphics, Font font, RecipeNode rn, int y, int mouseX, int mouseY) {
        // Parallelism label + slider.
        graphics.drawString(font, "Parallelism: " + rn.parallelism(),
                panelX + PADDING, y, TEXT, false);
        int sliderY = y + ROW_HEIGHT;
        renderSlider(graphics, panelX + PADDING, sliderY, WIDTH - PADDING * 2, rn.parallelism());

        // Swap-recipe button.
        int btnY = sliderY + SLIDER_HEIGHT + PADDING * 2;
        drawButton(graphics, panelX + PADDING, btnY, WIDTH - PADDING * 2, BUTTON_HEIGHT,
                "Swap recipe…", mouseX, mouseY);

        // Inputs.
        int inY = btnY + BUTTON_HEIGHT + PADDING * 2;
        graphics.drawString(font, "Inputs", panelX + PADDING, inY, TEXT_DIM, false);
        int rowY = inY + ROW_HEIGHT;
        for (Port p : rn.inputs()) {
            renderPortRow(graphics, font, p, rowY);
            rowY += ROW_HEIGHT + 2;
        }

        // Outputs.
        rowY += PADDING;
        graphics.drawString(font, "Outputs", panelX + PADDING, rowY, TEXT_DIM, false);
        rowY += ROW_HEIGHT;
        for (Port p : rn.outputs()) {
            renderPortRow(graphics, font, p, rowY);
            rowY += ROW_HEIGHT + 2;
        }
    }

    private void renderSink(GuiGraphics graphics, Font font, SinkNode sn, int y) {
        graphics.drawString(font, "Item", panelX + PADDING, y, TEXT_DIM, false);
        graphics.renderItem(sn.item(), panelX + PADDING, y + ROW_HEIGHT);
        graphics.drawString(font, sn.item().getHoverName().getString(),
                panelX + PADDING + 20, y + ROW_HEIGHT + 4, TEXT, false);
    }

    private void renderPortRow(GuiGraphics graphics, Font font, Port port, int y) {
        if (!port.display().isEmpty()) {
            graphics.renderItem(port.display(), panelX + PADDING, y);
        }
        graphics.drawString(font, truncate(font, port.label(), WIDTH - 22 - PADDING * 2),
                panelX + PADDING + 20, y + 4, TEXT, false);
    }

    private void renderSlider(GuiGraphics graphics, int x, int y, int w, int value) {
        graphics.fill(x, y, x + w, y + SLIDER_HEIGHT, SLIDER_TRACK);
        double frac = (double) (value - PARALLELISM_MIN) / (PARALLELISM_MAX - PARALLELISM_MIN);
        int fillW = (int) Math.round(w * frac);
        graphics.fill(x, y, x + fillW, y + SLIDER_HEIGHT, SLIDER_FILL);
    }

    private void drawButton(GuiGraphics graphics, int x, int y, int w, int h, String label,
                            int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY < y + h;
        graphics.fill(x, y, x + w, y + h, hover ? BUTTON_HOVER_BG : BUTTON_BG);
        Font font = Minecraft.getInstance().font;
        int textWidth = font.width(label);
        graphics.drawString(font, label, x + (w - textWidth) / 2, y + (h - 8) / 2, TEXT, false);
    }

    public boolean mouseClicked(double sx, double sy, int button) {
        if (!contains(sx, sy) || selected == null) return false;
        if (button != 0) return true; // consume but ignore non-left clicks

        int contentY = panelY + HEADER_HEIGHT + PADDING;
        if (selected instanceof SourceNode src) {
            int btnY = contentY + ROW_HEIGHT;
            if (hits(sx, sy, panelX + PADDING, btnY, WIDTH - PADDING * 2, BUTTON_HEIGHT)) {
                SourceNode.Kind[] kinds = SourceNode.Kind.values();
                int next = (src.kind().ordinal() + 1) % kinds.length;
                onNodeUpdated.accept(src.withKind(kinds[next]));
                return true;
            }
        } else if (selected instanceof RecipeNode rn) {
            int sliderY = contentY + ROW_HEIGHT;
            if (hits(sx, sy, panelX + PADDING, sliderY, WIDTH - PADDING * 2, SLIDER_HEIGHT)) {
                draggingSlider = true;
                applySliderDrag(sx, rn);
                return true;
            }
            int btnY = sliderY + SLIDER_HEIGHT + PADDING * 2;
            if (hits(sx, sy, panelX + PADDING, btnY, WIDTH - PADDING * 2, BUTTON_HEIGHT)) {
                onSwapRecipe.run();
                return true;
            }
        }
        return true; // consume clicks inside the panel even when no widget hit
    }

    public boolean mouseDragged(double sx, double sy, int button, double dragX, double dragY) {
        if (!draggingSlider) return false;
        if (selected instanceof RecipeNode rn) {
            applySliderDrag(sx, rn);
            return true;
        }
        return false;
    }

    public boolean mouseReleased(double sx, double sy, int button) {
        if (draggingSlider) {
            draggingSlider = false;
            return true;
        }
        return false;
    }

    private void applySliderDrag(double sx, RecipeNode rn) {
        int x = panelX + PADDING;
        int w = WIDTH - PADDING * 2;
        double frac = Math.max(0, Math.min(1, (sx - x) / w));
        int newValue = (int) Math.round(PARALLELISM_MIN + frac * (PARALLELISM_MAX - PARALLELISM_MIN));
        if (newValue != rn.parallelism()) {
            onNodeUpdated.accept(rn.withParallelism(newValue));
        }
    }

    private static boolean hits(double sx, double sy, int x, int y, int w, int h) {
        return sx >= x && sx <= x + w && sy >= y && sy < y + h;
    }

    private static String truncate(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        while (text.length() > 0 && font.width(text + "…") > maxWidth) {
            text = text.substring(0, text.length() - 1);
        }
        return text + "…";
    }
}
