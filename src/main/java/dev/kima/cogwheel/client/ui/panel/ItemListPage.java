package dev.kima.cogwheel.client.ui.panel;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Searchable scrolling list of items used by the Recipes / Sources / Sinks categories. The click
 * behavior is parameterized via {@code onItemClicked} — the caller decides whether a click adds a
 * recipe node, source node, or sink node.
 *
 * <p>Owns the search {@link EditBox} widget directly; registers it with the parent screen on
 * {@link #onShow} and removes it on {@link #onHide}. That lifecycle is what makes the stacking nav
 * clean — yesterday's search box doesn't linger when you back out.
 */
public final class ItemListPage implements LeftPanelPage {

    private static final int SEARCH_HEIGHT = 18;
    private static final int ROW_HEIGHT = 18;
    private static final int PADDING = 4;
    private static final int FOOTER_HEIGHT = 14;

    private static final int ROW_HOVER = 0xFF2A3148;
    private static final int TEXT = 0xFFEAEAEA;
    private static final int TEXT_DIM = 0xFFA8A8B8;
    private static final int SCROLLBAR = 0xFF54607A;
    private static final int BORDER = 0xFF2A3148;

    private final Component title;
    private final List<Item> allItems;
    private final Consumer<Item> onItemClicked;

    private EditBox searchBox;
    private List<Item> filtered = List.of();
    private double scrollOffset;
    private int x, y, width, height;

    public ItemListPage(Component title, List<Item> items, Consumer<Item> onItemClicked) {
        this.title = title;
        this.allItems = List.copyOf(items);
        this.filtered = this.allItems;
        this.onItemClicked = onItemClicked;
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
        searchBox.setHint(Component.literal("Search…"));
        searchBox.setMaxLength(64);
        searchBox.setResponder(this::applyFilter);
        host.register(searchBox);
    }

    @Override
    public void onHide(WidgetHost host) {
        if (searchBox != null) {
            host.unregister(searchBox);
            searchBox = null;
        }
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

    private int listY() {
        return y + SEARCH_HEIGHT + PADDING * 2;
    }

    private int listH() {
        return height - (listY() - y) - FOOTER_HEIGHT - PADDING;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        Font font = Minecraft.getInstance().font;
        int listY = listY();
        int listH = listH();

        // List rows under scissor.
        graphics.enableScissor(x, listY, x + width, listY + listH);
        int firstVisible = (int) (scrollOffset / ROW_HEIGHT);
        int rowsVisible = listH / ROW_HEIGHT + 1;
        int last = Math.min(filtered.size(), firstVisible + rowsVisible + 1);
        for (int i = firstVisible; i < last; i++) {
            int rowY = listY + i * ROW_HEIGHT - (int) scrollOffset;
            boolean hover = mouseX >= x && mouseX <= x + width
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            if (hover) {
                graphics.fill(x + 1, rowY, x + width - 1, rowY + ROW_HEIGHT, ROW_HOVER);
            }
            Item item = filtered.get(i);
            graphics.renderItem(new ItemStack(item), x + PADDING, rowY + 1);
            String name = displayName(item);
            int textWidth = width - 20 - PADDING * 2;
            graphics.drawString(font, truncate(font, name, textWidth),
                    x + 20 + PADDING, rowY + 5, TEXT, false);
        }
        graphics.disableScissor();

        // Scrollbar — strictly within the list track.
        int contentH = filtered.size() * ROW_HEIGHT;
        if (contentH > listH) {
            int barH = Math.max(20, (int) ((double) listH / contentH * listH));
            double frac = (contentH == listH) ? 0 : scrollOffset / (double) (contentH - listH);
            int barY = listY + (int) (frac * (listH - barH));
            graphics.fill(x + width - 4, barY, x + width - 1, barY + barH, SCROLLBAR);
        }

        // Footer counter, separated by a thin border line.
        int footerY = y + height - FOOTER_HEIGHT + 2;
        graphics.fill(x, footerY - 2, x + width, footerY - 1, BORDER);
        String counter = filtered.size() == allItems.size()
                ? allItems.size() + " items"
                : filtered.size() + " of " + allItems.size();
        graphics.drawString(font, Component.literal(counter), x + PADDING, footerY + 2, TEXT_DIM, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        if (searchBox != null && searchBox.isMouseOver(mouseX, mouseY)) return false; // vanilla focus
        int listY = listY();
        int listH = listH();
        if (mouseY < listY || mouseY >= listY + listH) return true;
        int relY = (int) (mouseY - listY + scrollOffset);
        int index = relY / ROW_HEIGHT;
        if (index < 0 || index >= filtered.size()) return true;
        onItemClicked.accept(filtered.get(index));
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        int listH = listH();
        int contentH = filtered.size() * ROW_HEIGHT;
        if (contentH <= listH) return true;
        scrollOffset -= scrollY * ROW_HEIGHT * 2;
        scrollOffset = Math.max(0, Math.min(contentH - listH, scrollOffset));
        return true;
    }

    private void applyFilter(String query) {
        String needle = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
        if (needle.isEmpty()) {
            filtered = allItems;
        } else {
            List<Item> result = new ArrayList<>();
            for (Item item : allItems) {
                if (displayName(item).toLowerCase(Locale.ROOT).contains(needle)) {
                    result.add(item);
                }
            }
            filtered = result;
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
