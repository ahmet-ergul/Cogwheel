package dev.kima.cogwheel.client.ui.panel;

import dev.kima.cogwheel.client.ui.ScaledUi;
import dev.kima.cogwheel.integration.jei.JeiBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * JEI-style searchable grid of items used by the Recipes / Sources / Sinks / Outputs categories.
 *
 * <p><b>Interactions</b>
 * <ul>
 *   <li>Click an item → open JEI recipes-producing page (same as JEI's R gesture).</li>
 *   <li>Drag an item off the panel onto the canvas → fires {@code onDropOnCanvas} to add a node.</li>
 *   <li>R while hovering → JEI recipes-producing.</li>
 *   <li>U while hovering → JEI recipes-using.</li>
 * </ul>
 *
 * <p>Search syntax mirrors JEI's basics: plain text matches the display name, {@code @modid}
 * restricts to items in that namespace. Combine like {@code @create iron}.
 */
public final class ItemListPage implements LeftPanelPage {

    private static final int SEARCH_HEIGHT = 18;
    private static final int CELL_SIZE = 18;
    private static final int PADDING = 4;
    private static final int FOOTER_HEIGHT = 14;
    /** Pixels of pointer movement required to count a click as a drag (not just a sloppy click). */
    private static final double DRAG_THRESHOLD = 4.0;

    private static final int CELL_HOVER = 0xFF3A4868;
    private static final int TEXT_DIM = 0xFFA8A8B8;
    private static final int SCROLLBAR = 0xFF54607A;
    private static final int BORDER = 0xFF2A3148;

    private final Component title;
    private final List<Item> allItems;
    /** Called when an item is dragged out of the panel and released over the canvas. */
    private final Consumer<Item> onDropOnCanvas;

    private EditBox searchBox;
    private List<Item> filtered = List.of();
    private double scrollOffset;
    private int x, y, width, height;
    /** Preserved across onHide/onShow so opening JEI and returning keeps the user's search filter
     *  + scroll position. Scroll is already a field, this covers the search text. */
    private String preservedSearchText = "";

    /** Drag tracking. {@code pendingDragItem} is the item under the cursor at mouseDown. */
    private Item pendingDragItem;
    private double pressX, pressY;
    private boolean dragStarted;

    public ItemListPage(Component title, List<Item> items, Consumer<Item> onDropOnCanvas) {
        this.title = title;
        this.allItems = List.copyOf(items);
        this.filtered = this.allItems;
        this.onDropOnCanvas = onDropOnCanvas;
    }

    @Override
    public Component title() {
        return title;
    }

    @Override
    public void onShow(WidgetHost host) {
        Font font = Minecraft.getInstance().font;
        searchBox = new EditBox(font, x + PADDING, y + PADDING, width - PADDING * 2 - 2, SEARCH_HEIGHT - 4,
                Component.translatable("screen.cogwheel.toolbox.search"));
        searchBox.setBordered(true);
        searchBox.setHint(Component.literal("Search… (@mod, R/U on hover)"));
        searchBox.setMaxLength(64);
        searchBox.setResponder(this::applyFilter);
        if (!preservedSearchText.isEmpty()) {
            searchBox.setValue(preservedSearchText); // re-runs responder → filtered list rebuilt
        }
        host.register(searchBox);
    }

    @Override
    public void onHide(WidgetHost host) {
        if (searchBox != null) {
            preservedSearchText = searchBox.getValue();
            host.unregister(searchBox);
            searchBox = null;
        }
        pendingDragItem = null;
        dragStarted = false;
    }

    @Override
    public void setLayout(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        if (searchBox != null) {
            searchBox.setX(x + PADDING);
            searchBox.setY(y + PADDING);
            searchBox.setWidth(width - PADDING * 2 - 2);
        }
    }

    private int columns() {
        return Math.max(1, (width - PADDING * 2) / CELL_SIZE);
    }

    private int rowsTotal() {
        int cols = columns();
        return (filtered.size() + cols - 1) / cols;
    }

    private int listY() {
        return y + SEARCH_HEIGHT + PADDING * 2;
    }

    private int listH() {
        return height - (listY() - y) - FOOTER_HEIGHT - PADDING;
    }

    private boolean withinPanel(double mx, double my) {
        return mx >= x && mx <= x + width && my >= y && my <= y + height;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        Font font = Minecraft.getInstance().font;
        int cols = columns();
        int listY = listY();
        int listH = listH();
        int contentH = rowsTotal() * CELL_SIZE;

        graphics.enableScissor(x, listY, x + width, listY + listH);

        int firstRow = (int) (scrollOffset / CELL_SIZE);
        int rowsVisible = listH / CELL_SIZE + 2;
        int lastRow = Math.min(rowsTotal(), firstRow + rowsVisible);

        for (int row = firstRow; row < lastRow; row++) {
            for (int col = 0; col < cols; col++) {
                int idx = row * cols + col;
                if (idx >= filtered.size()) break;
                Item item = filtered.get(idx);
                int cellX = x + PADDING + col * CELL_SIZE;
                int cellY = listY + row * CELL_SIZE - (int) scrollOffset;
                boolean hover = mouseX >= cellX && mouseX < cellX + CELL_SIZE
                        && mouseY >= cellY && mouseY < cellY + CELL_SIZE
                        && mouseY >= listY && mouseY < listY + listH;
                if (hover) {
                    graphics.fill(cellX, cellY, cellX + CELL_SIZE, cellY + CELL_SIZE, CELL_HOVER);
                }
                graphics.renderItem(new ItemStack(item), cellX + 1, cellY + 1);
            }
        }

        graphics.disableScissor();

        if (contentH > listH) {
            int barH = Math.max(20, (int) ((double) listH / contentH * listH));
            double frac = scrollOffset / (double) (contentH - listH);
            int barY = listY + (int) (frac * (listH - barH));
            graphics.fill(x + width - 4, barY, x + width - 1, barY + barH, SCROLLBAR);
        }

        int footerY = y + height - FOOTER_HEIGHT + 2;
        graphics.fill(x, footerY - 2, x + width, footerY - 1, BORDER);
        String counter = filtered.size() == allItems.size()
                ? allItems.size() + " items"
                : filtered.size() + " of " + allItems.size();
        ScaledUi.drawString(graphics, font, counter, x + PADDING, footerY + 2, TEXT_DIM);

        // Drag ghost — small icon following cursor while a drag is in progress, even outside the panel.
        if (dragStarted && pendingDragItem != null) {
            graphics.renderItem(new ItemStack(pendingDragItem), mouseX - 8, mouseY - 8);
        }

        // Tooltip — only when not actively dragging, since the ghost would overlap.
        if (!dragStarted) {
            Item hovered = itemAt(mouseX, mouseY);
            if (hovered != null) {
                ItemStack stack = new ItemStack(hovered);
                List<Component> tooltip = Screen.getTooltipFromItem(Minecraft.getInstance(), stack);
                graphics.renderTooltip(font, tooltip, Optional.empty(), mouseX, mouseY);
            }
        }
    }

    private Item itemAt(int mouseX, int mouseY) {
        int listY = listY();
        int listH = listH();
        if (mouseX < x + PADDING || mouseX >= x + width - PADDING
                || mouseY < listY || mouseY >= listY + listH) return null;
        int cols = columns();
        int col = (mouseX - x - PADDING) / CELL_SIZE;
        if (col < 0 || col >= cols) return null;
        int row = ((int) (mouseY - listY + scrollOffset)) / CELL_SIZE;
        int idx = row * cols + col;
        if (idx < 0 || idx >= filtered.size()) return null;
        return filtered.get(idx);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        if (searchBox != null && searchBox.isMouseOver(mouseX, mouseY)) return false; // vanilla focus
        Item hit = itemAt((int) mouseX, (int) mouseY);
        if (hit == null) return true;
        pendingDragItem = hit;
        pressX = mouseX;
        pressY = mouseY;
        dragStarted = false;
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button != 0 || pendingDragItem == null) return false;
        Item item = pendingDragItem;
        boolean wasDragged = dragStarted;
        pendingDragItem = null;
        dragStarted = false;
        if (wasDragged) {
            if (!withinPanel(mouseX, mouseY)) {
                onDropOnCanvas.accept(item);
            }
            // Dragged + released inside panel = drag cancelled, no-op.
            return true;
        }
        // Plain click → open JEI recipe page for the item (same as R).
        JeiBridge.showRecipesOfItem(new ItemStack(item));
        return true;
    }

    /**
     * Called by {@link LeftPanel#mouseDragged} so we can promote a pending click into a drag once
     * the cursor moves past {@link #DRAG_THRESHOLD}. The render method then draws the ghost.
     */
    public void mouseDragged(double mouseX, double mouseY) {
        if (pendingDragItem == null || dragStarted) return;
        double dx = mouseX - pressX;
        double dy = mouseY - pressY;
        if ((dx * dx + dy * dy) >= (DRAG_THRESHOLD * DRAG_THRESHOLD)) {
            dragStarted = true;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers, int mouseX, int mouseY) {
        Item hovered = itemAt(mouseX, mouseY);
        if (hovered == null) return false;
        if (keyCode == GLFW.GLFW_KEY_R) {
            return JeiBridge.showRecipesOfItem(new ItemStack(hovered));
        }
        if (keyCode == GLFW.GLFW_KEY_U) {
            return JeiBridge.showUsesOfItem(new ItemStack(hovered));
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        int listH = listH();
        int contentH = rowsTotal() * CELL_SIZE;
        if (contentH <= listH) return true;
        scrollOffset -= scrollY * CELL_SIZE * 2;
        scrollOffset = Math.max(0, Math.min(contentH - listH, scrollOffset));
        return true;
    }

    private void applyFilter(String query) {
        String raw = query == null ? "" : query.trim();
        if (raw.isEmpty()) {
            filtered = allItems;
            scrollOffset = 0;
            return;
        }
        List<String> modPrefixes = new ArrayList<>();
        StringBuilder name = new StringBuilder();
        for (String token : raw.split("\\s+")) {
            if (token.isEmpty()) continue;
            if (token.startsWith("@") && token.length() > 1) {
                modPrefixes.add(token.substring(1).toLowerCase(Locale.ROOT));
            } else {
                if (name.length() > 0) name.append(' ');
                name.append(token);
            }
        }
        String needle = name.toString().toLowerCase(Locale.ROOT);
        List<Item> result = new ArrayList<>();
        for (Item item : allItems) {
            if (!modPrefixes.isEmpty()) {
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
                if (id == null) continue;
                String ns = id.getNamespace().toLowerCase(Locale.ROOT);
                boolean matchedMod = false;
                for (String prefix : modPrefixes) {
                    if (ns.startsWith(prefix)) { matchedMod = true; break; }
                }
                if (!matchedMod) continue;
            }
            if (!needle.isEmpty()) {
                if (!displayName(item).toLowerCase(Locale.ROOT).contains(needle)) continue;
            }
            result.add(item);
        }
        filtered = result;
        scrollOffset = 0;
    }

    private static String displayName(Item item) {
        return new ItemStack(item).getHoverName().getString();
    }
}
