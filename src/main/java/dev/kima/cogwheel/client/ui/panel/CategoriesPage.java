package dev.kima.cogwheel.client.ui.panel;

import dev.kima.cogwheel.client.ui.ScaledUi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * Root page of {@link LeftPanel}: a vertical list of category cards. A category can either be a
 * <b>nav category</b> (click pushes a subpage / opens a modal — {@link Category#onClick}) or a
 * <b>drag-to-add</b> category (drag off the panel onto the canvas to add a node at the cursor
 * location — {@link Category#onDropOnCanvas}). A category with only an {@code onDropOnCanvas}
 * callback never adds on plain click — the user MUST drag it to a canvas position, matching the
 * ItemListPage drop semantics.
 *
 * <p>A disabled category renders grayed-out and ignores both gestures.
 */
public final class CategoriesPage implements LeftPanelPage {

    /** Receives screen-space drop coordinates. EditorScreen converts to world via Canvas. */
    public interface DropTarget extends BiConsumer<Double, Double> {}

    public record Category(ItemStack icon, Component label, Component subtitle, boolean enabled,
                            boolean separatorAbove, Runnable onClick, DropTarget onDropOnCanvas) {
        /** Nav-only constructor (no drag). */
        public Category(ItemStack icon, Component label, Component subtitle, boolean enabled, Runnable onClick) {
            this(icon, label, subtitle, enabled, false, onClick, null);
        }

        /** Nav-only with separator. */
        public Category(ItemStack icon, Component label, Component subtitle, boolean enabled,
                         boolean separatorAbove, Runnable onClick) {
            this(icon, label, subtitle, enabled, separatorAbove, onClick, null);
        }

        /** Drag-to-add: no click handler, click-and-release with no drag does nothing. */
        public static Category dragOnly(ItemStack icon, Component label, Component subtitle,
                                          DropTarget onDropOnCanvas) {
            return new Category(icon, label, subtitle, true, false, null, onDropOnCanvas);
        }
    }

    private static final int ROW_HEIGHT = 28;
    private static final int PADDING = 6;
    private static final double DRAG_THRESHOLD = 4.0;
    private static final int BG_HOVER = 0xFF2A3148;
    private static final int TEXT = 0xFFEAEAEA;
    private static final int TEXT_DIM = 0xFFA8A8B8;
    private static final int TEXT_DISABLED = 0xFF5A6075;
    private static final int DIVIDER = 0xFF2A3148;

    private final Component title;
    private final List<Category> categories;
    private int x, y, width, height;

    /** Drag tracking. A pending drag turns into a real one once the cursor crosses
     *  {@link #DRAG_THRESHOLD}; release outside the panel fires {@link Category#onDropOnCanvas}. */
    private Category pendingDragCategory;
    private double pressX, pressY;
    private boolean dragStarted;

    public CategoriesPage(Component title, List<Category> categories) {
        this.title = title;
        this.categories = categories;
    }

    @Override
    public Component title() {
        return title;
    }

    @Override public void onShow(WidgetHost host) {}
    @Override public void onHide(WidgetHost host) {
        pendingDragCategory = null;
        dragStarted = false;
    }

    @Override
    public void setLayout(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        Font font = Minecraft.getInstance().font;
        int rowY = y + PADDING;
        for (Category cat : categories) {
            if (cat.separatorAbove()) {
                rowY += 4;
                graphics.fill(x + PADDING, rowY - 3, x + width - PADDING, rowY - 2, DIVIDER);
                rowY += 2;
            }
            boolean hover = cat.enabled() && mouseX >= x && mouseX <= x + width
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            if (hover) {
                graphics.fill(x + 2, rowY, x + width - 2, rowY + ROW_HEIGHT, BG_HOVER);
            }
            if (!cat.icon().isEmpty()) {
                graphics.renderItem(cat.icon(), x + PADDING, rowY + 6);
            }
            int labelColor = cat.enabled() ? TEXT : TEXT_DISABLED;
            int textX = x + PADDING + 22;
            int textMaxW = width - (textX - x) - PADDING - 12;
            ScaledUi.drawString(graphics, font, ScaledUi.truncate(font, cat.label().getString(), textMaxW),
                    textX, rowY + 5, labelColor);
            ScaledUi.drawString(graphics, font, ScaledUi.truncate(font, cat.subtitle().getString(), textMaxW),
                    textX, rowY + 16, cat.enabled() ? TEXT_DIM : TEXT_DISABLED);
            if (cat.enabled()) {
                // Use a different glyph hint for drag-to-add vs nav so users learn the gesture.
                String hint = cat.onClick() != null ? "›" : "⋮";
                ScaledUi.drawString(graphics, font, hint, x + width - 14, rowY + 10, TEXT_DIM);
            }
            rowY += ROW_HEIGHT;
        }

        // Drag ghost: follow cursor with the icon during an active drag.
        if (dragStarted && pendingDragCategory != null && !pendingDragCategory.icon().isEmpty()) {
            graphics.renderItem(pendingDragCategory.icon(), mouseX - 8, mouseY - 8);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        Category hit = categoryAt(mouseX, mouseY);
        if (hit == null || !hit.enabled()) return false;
        pendingDragCategory = hit;
        pressX = mouseX;
        pressY = mouseY;
        dragStarted = false;
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button != 0 || pendingDragCategory == null) return false;
        Category cat = pendingDragCategory;
        boolean wasDragged = dragStarted;
        pendingDragCategory = null;
        dragStarted = false;
        if (wasDragged) {
            // Released outside the panel → fire drop callback (with screen coords).
            if (!withinPanel(mouseX, mouseY) && cat.onDropOnCanvas() != null) {
                cat.onDropOnCanvas().accept(mouseX, mouseY);
            }
            return true;
        }
        // Plain click: only fire onClick if the category has one. Drag-only categories do nothing.
        if (cat.onClick() != null) cat.onClick().run();
        return true;
    }

    /** Called by {@link LeftPanel#mouseDragged} to promote a click into a drag once the cursor
     *  moves past {@link #DRAG_THRESHOLD}. */
    public void mouseDragged(double mouseX, double mouseY) {
        if (pendingDragCategory == null || dragStarted) return;
        // Only drag-capable categories produce a ghost.
        if (pendingDragCategory.onDropOnCanvas() == null) return;
        double dx = mouseX - pressX;
        double dy = mouseY - pressY;
        if ((dx * dx + dy * dy) >= (DRAG_THRESHOLD * DRAG_THRESHOLD)) {
            dragStarted = true;
        }
    }

    private boolean withinPanel(double mx, double my) {
        return mx >= x && mx <= x + width && my >= y && my <= y + height;
    }

    private Category categoryAt(double mouseX, double mouseY) {
        int rowY = y + PADDING;
        for (Category cat : categories) {
            if (cat.separatorAbove()) rowY += 6;
            if (mouseY >= rowY && mouseY < rowY + ROW_HEIGHT
                    && mouseX >= x && mouseX <= x + width) {
                return cat;
            }
            rowY += ROW_HEIGHT;
        }
        return null;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        return false;
    }
}
