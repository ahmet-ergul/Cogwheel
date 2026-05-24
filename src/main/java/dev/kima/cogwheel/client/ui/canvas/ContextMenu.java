package dev.kima.cogwheel.client.ui.canvas;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * Tiny right-click popup, rendered in SCREEN space (not transformed by the canvas pan/zoom).
 * Holds a list of (label, action) entries. Click on an entry fires its action and closes the menu;
 * click anywhere outside closes without firing.
 */
public final class ContextMenu {
    public record Option(String label, Runnable action) {}

    private static final int PADDING = 4;
    private static final int LINE_HEIGHT = 12;
    private static final int MENU_BG = 0xEE1F2233;
    private static final int MENU_BORDER = 0xFF54607A;
    private static final int LABEL_FG = 0xFFEAEAEA;
    private static final int LABEL_HOVER_BG = 0xFF3A4868;

    private boolean visible;
    private int x;
    private int y;
    private List<Option> options = List.of();

    public boolean isVisible() {
        return visible;
    }

    public void show(int screenX, int screenY, List<Option> opts) {
        this.x = screenX;
        this.y = screenY;
        this.options = opts;
        this.visible = !opts.isEmpty();
    }

    public void hide() {
        this.visible = false;
        this.options = List.of();
    }

    /** Bounds of the rendered menu. */
    public int width() {
        Font font = Minecraft.getInstance().font;
        int w = 0;
        for (Option o : options) w = Math.max(w, font.width(o.label()));
        return w + PADDING * 2;
    }

    public int height() {
        return options.size() * LINE_HEIGHT + PADDING * 2;
    }

    public boolean contains(double sx, double sy) {
        return visible && sx >= x && sx <= x + width() && sy >= y && sy <= y + height();
    }

    /**
     * Returns true if the click landed on a menu entry (and fires it); false otherwise. Either way,
     * the menu is closed after the call.
     */
    public boolean handleClick(double sx, double sy) {
        if (!visible) return false;
        if (!contains(sx, sy)) {
            hide();
            return false;
        }
        int relY = (int) (sy - y - PADDING);
        int index = relY / LINE_HEIGHT;
        if (index >= 0 && index < options.size()) {
            Runnable action = options.get(index).action();
            hide();
            action.run();
            return true;
        }
        hide();
        return false;
    }

    public void render(GuiGraphics graphics, double mouseX, double mouseY) {
        if (!visible) return;
        Font font = Minecraft.getInstance().font;
        int w = width();
        int h = height();

        // Push the menu's Z above the typical item-icon Z range (~150-250). Item rendering uses
        // a 3D model with its own Z values that overdraw later 2D fills — without translating up
        // here, item icons elsewhere on the screen bleed through this menu's backdrop. Flush
        // batched draws first so the translate applies cleanly to everything we draw below.
        graphics.flush();
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(0, 0, 400);

        graphics.fill(x, y, x + w, y + h, MENU_BG);
        graphics.fill(x - 1, y - 1, x + w + 1, y, MENU_BORDER);
        graphics.fill(x - 1, y + h, x + w + 1, y + h + 1, MENU_BORDER);
        graphics.fill(x - 1, y, x, y + h, MENU_BORDER);
        graphics.fill(x + w, y, x + w + 1, y + h, MENU_BORDER);

        for (int i = 0; i < options.size(); i++) {
            int rowY = y + PADDING + i * LINE_HEIGHT;
            boolean hover = mouseY >= rowY && mouseY < rowY + LINE_HEIGHT
                    && mouseX >= x && mouseX <= x + w;
            if (hover) {
                graphics.fill(x + 1, rowY, x + w - 1, rowY + LINE_HEIGHT, LABEL_HOVER_BG);
            }
            graphics.drawString(font, options.get(i).label(), x + PADDING, rowY + 2, LABEL_FG, false);
        }

        graphics.flush();
        pose.popPose();
    }
}
