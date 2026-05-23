package dev.kima.cogwheel.client.ui.panel;

import dev.kima.cogwheel.recipe.RecipeIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Left sidebar with a search box on top and a scrollable list of items below. Each item shown is
 * one that appears as an input or output of at least one indexed recipe.
 *
 * <p>Clicking an item invokes the {@code onItemClicked} callback supplied by {@link
 * dev.kima.cogwheel.client.ui.EditorScreen}; that callback runs the add-node-with-recipe-picker
 * flow (Phase 4e).
 *
 * <p>The {@link EditBox} is a vanilla widget — the parent screen must register it via
 * {@code addRenderableWidget} so vanilla handles its render / focus / key events. The item list
 * below the search box is drawn manually here.
 */
public final class ToolboxPanel {
    public static final int WIDTH = 168;
    private static final int SEARCH_HEIGHT = 18;
    private static final int ROW_HEIGHT = 18;
    private static final int PADDING = 4;

    private static final int BG_COLOR = 0xFF131726;
    private static final int BORDER = 0xFF2A3148;
    private static final int ROW_HOVER = 0xFF2A3148;
    private static final int TEXT = 0xFFEAEAEA;
    private static final int TEXT_DIM = 0xFFA8A8B8;
    private static final int SCROLLBAR = 0xFF54607A;

    private final Consumer<Item> onItemClicked;
    private final List<Item> allItems = new ArrayList<>();
    private List<Item> filteredItems = List.of();
    private EditBox searchBox;
    private double scrollOffset = 0;
    private int panelX;
    private int panelY;
    private int panelH;

    public ToolboxPanel(Consumer<Item> onItemClicked) {
        this.onItemClicked = onItemClicked;
    }

    /**
     * Build the catalog from the current {@link RecipeIndex}. Items are sorted alphabetically by
     * display name. Called once per screen-open — cheap given a few-thousand-item ceiling.
     */
    public void buildCatalog(RecipeIndex index) {
        allItems.clear();
        for (Item item : index.indexedItems()) {
            if (item == null) continue;
            allItems.add(item);
        }
        Comparator<Item> byName = Comparator.comparing(item -> displayName(item).toLowerCase(Locale.ROOT));
        allItems.sort(byName);
        applyFilter("");
    }

    /** Creates the search EditBox. Caller registers it with the parent screen. */
    public EditBox createSearchBox(Font font, int x, int y) {
        this.searchBox = new EditBox(font, x + PADDING, y + PADDING, WIDTH - PADDING * 2 - 2, SEARCH_HEIGHT - 4,
                Component.translatable("screen.cogwheel.toolbox.search"));
        searchBox.setBordered(true);
        searchBox.setHint(Component.literal("Search…"));
        searchBox.setMaxLength(64);
        searchBox.setResponder(this::applyFilter);
        return searchBox;
    }

    public void setLayout(int x, int y, int h) {
        this.panelX = x;
        this.panelY = y;
        this.panelH = h;
        if (searchBox != null) {
            searchBox.setX(x + PADDING);
            searchBox.setY(y + PADDING);
        }
    }

    public boolean contains(double sx, double sy) {
        return sx >= panelX && sx <= panelX + WIDTH && sy >= panelY && sy <= panelY + panelH;
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(panelX, panelY, panelX + WIDTH, panelY + panelH, BG_COLOR);
        graphics.fill(panelX + WIDTH, panelY, panelX + WIDTH + 1, panelY + panelH, BORDER);

        // Item list area starts below the search box.
        int listY = panelY + SEARCH_HEIGHT + PADDING * 2;
        int listH = panelH - (listY - panelY) - PADDING;

        graphics.enableScissor(panelX, listY, panelX + WIDTH, listY + listH);
        Font font = Minecraft.getInstance().font;
        int firstVisible = (int) (scrollOffset / ROW_HEIGHT);
        int rowsVisible = listH / ROW_HEIGHT + 1;
        int last = Math.min(filteredItems.size(), firstVisible + rowsVisible + 1);

        for (int i = firstVisible; i < last; i++) {
            int rowY = listY + i * ROW_HEIGHT - (int) scrollOffset;
            boolean hover = mouseX >= panelX && mouseX <= panelX + WIDTH
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            if (hover) {
                graphics.fill(panelX + 1, rowY, panelX + WIDTH - 1, rowY + ROW_HEIGHT, ROW_HOVER);
            }
            Item item = filteredItems.get(i);
            ItemStack stack = new ItemStack(item);
            graphics.renderItem(stack, panelX + PADDING, rowY + 1);
            String name = displayName(item);
            int textWidth = WIDTH - 20 - PADDING * 2;
            graphics.drawString(font, truncate(font, name, textWidth),
                    panelX + 20 + PADDING, rowY + 5, TEXT, false);
        }
        graphics.disableScissor();

        // Scrollbar (only if list overflows).
        int contentH = filteredItems.size() * ROW_HEIGHT;
        if (contentH > listH) {
            int barH = Math.max(20, (int) ((double) listH / contentH * listH));
            int barY = listY + (int) (scrollOffset / contentH * listH);
            graphics.fill(panelX + WIDTH - 4, barY, panelX + WIDTH - 1, barY + barH, SCROLLBAR);
        }

        // "{count} items" subtitle at the bottom.
        graphics.drawString(font,
                Component.literal(filteredItems.size() + " / " + allItems.size()),
                panelX + PADDING, panelY + panelH - 11, TEXT_DIM, false);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!contains(mouseX, mouseY)) return false;
        // Clicks in the search box are handled by the EditBox itself (vanilla focus path).
        if (searchBox != null && searchBox.isMouseOver(mouseX, mouseY)) {
            return false; // let vanilla handle
        }
        int listY = panelY + SEARCH_HEIGHT + PADDING * 2;
        if (mouseY < listY) return true; // clicked in header strip but not in EditBox
        int relY = (int) (mouseY - listY + scrollOffset);
        int index = relY / ROW_HEIGHT;
        if (index < 0 || index >= filteredItems.size()) return true;
        onItemClicked.accept(filteredItems.get(index));
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (!contains(mouseX, mouseY)) return false;
        int listY = panelY + SEARCH_HEIGHT + PADDING * 2;
        int listH = panelH - (listY - panelY) - PADDING;
        int contentH = filteredItems.size() * ROW_HEIGHT;
        if (contentH <= listH) return true;
        scrollOffset -= scrollY * ROW_HEIGHT * 2;
        scrollOffset = Math.max(0, Math.min(contentH - listH, scrollOffset));
        return true;
    }

    /** Listener delegate compatible with {@link GuiEventListener} for vanilla mouse routing if needed. */
    public GuiEventListener listener() {
        return searchBox;
    }

    private void applyFilter(String query) {
        String needle = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
        if (needle.isEmpty()) {
            filteredItems = List.copyOf(allItems);
        } else {
            List<Item> result = new ArrayList<>();
            for (Item item : allItems) {
                if (displayName(item).toLowerCase(Locale.ROOT).contains(needle)) {
                    result.add(item);
                }
            }
            filteredItems = result;
        }
        scrollOffset = 0;
    }

    private static String displayName(Item item) {
        return new ItemStack(item).getHoverName().getString();
    }

    private static String truncate(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        while (text.length() > 0 && font.width(text + "…") > maxWidth) {
            text = text.substring(0, text.length() - 1);
        }
        return text + "…";
    }
}
