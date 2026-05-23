package dev.kima.cogwheel.client.ui.panel;

import dev.kima.cogwheel.recipe.RecipeIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;

/**
 * Collapsible left sidebar with stacking-navigation pages. Hidden by default; toggled by the
 * {@code +} button at the bottom-right of the canvas (in EditorScreen).
 *
 * <p>Root page is {@link CategoriesPage} listing Recipes / Sources / Sinks / Logic. Drilling into
 * a category pushes an {@link ItemListPage} (or in the future, a different page type for Logic /
 * Notes / Factory switcher). The back button in the header pops to the previous page.
 */
public final class LeftPanel {
    public static final int WIDTH = 180;
    private static final int HEADER_HEIGHT = 22;
    private static final int PADDING = 6;
    private static final int CLOSE_SIZE = 12;
    private static final int BACK_SIZE = 12;

    private static final int BG_COLOR = 0xFF131726;
    private static final int BORDER = 0xFF2A3148;
    private static final int HEADER_BG = 0xFF1A1E2A;
    private static final int TEXT = 0xFFEAEAEA;
    private static final int TEXT_DIM = 0xFFA8A8B8;
    private static final int BUTTON_HOVER = 0xFF3A4868;

    public record Callbacks(
            Consumer<Item> onAddRecipe,
            Consumer<Item> onAddSource,
            Consumer<Item> onAddSink,
            Runnable onAddSplitter,
            Runnable onAddMerger
    ) {}

    private final LeftPanelPage.WidgetHost widgetHost;
    private final RecipeIndex index;
    private final Callbacks callbacks;
    private final Runnable onClose;

    private final Deque<LeftPanelPage> pageStack = new ArrayDeque<>();
    private boolean open;
    private int x, y, height;

    public LeftPanel(LeftPanelPage.WidgetHost widgetHost, RecipeIndex index, Callbacks callbacks, Runnable onClose) {
        this.widgetHost = widgetHost;
        this.index = index;
        this.callbacks = callbacks;
        this.onClose = onClose;
    }

    public boolean isOpen() {
        return open;
    }

    public void setLayout(int x, int y, int height) {
        this.x = x;
        this.y = y;
        this.height = height;
        relayoutCurrentPage();
    }

    public boolean contains(double sx, double sy) {
        return open && sx >= x && sx <= x + WIDTH && sy >= y && sy <= y + height;
    }

    public void open() {
        if (open) return;
        open = true;
        if (pageStack.isEmpty()) {
            pushPage(buildCategoriesPage());
        } else {
            relayoutCurrentPage();
            pageStack.peek().onShow(widgetHost);
        }
    }

    public void close() {
        if (!open) return;
        if (!pageStack.isEmpty()) pageStack.peek().onHide(widgetHost);
        open = false;
    }

    public void pushPage(LeftPanelPage page) {
        if (!pageStack.isEmpty()) pageStack.peek().onHide(widgetHost);
        pageStack.push(page);
        layoutPage(page);
        page.onShow(widgetHost);
    }

    public void popPage() {
        if (pageStack.size() <= 1) {
            // At root — close the panel instead.
            close();
            if (onClose != null) onClose.run();
            return;
        }
        LeftPanelPage leaving = pageStack.pop();
        leaving.onHide(widgetHost);
        LeftPanelPage now = pageStack.peek();
        layoutPage(now);
        now.onShow(widgetHost);
    }

    private void relayoutCurrentPage() {
        if (!pageStack.isEmpty()) layoutPage(pageStack.peek());
    }

    private void layoutPage(LeftPanelPage page) {
        page.setLayout(x, y + HEADER_HEIGHT, WIDTH, height - HEADER_HEIGHT);
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!open) return;
        Font font = Minecraft.getInstance().font;

        // Body background.
        graphics.fill(x, y, x + WIDTH, y + height, BG_COLOR);
        graphics.fill(x + WIDTH, y, x + WIDTH + 1, y + height, BORDER);

        // Header strip.
        graphics.fill(x, y, x + WIDTH, y + HEADER_HEIGHT, HEADER_BG);
        graphics.fill(x, y + HEADER_HEIGHT, x + WIDTH, y + HEADER_HEIGHT + 1, BORDER);

        // Back button (only if not on root).
        if (pageStack.size() > 1) {
            renderBackButton(graphics, font, mouseX, mouseY);
        }

        // Title — centered if no back button, left-shifted if there is one.
        if (!pageStack.isEmpty()) {
            String title = pageStack.peek().title().getString();
            int titleX = (pageStack.size() > 1) ? x + BACK_SIZE + PADDING * 2 : x + PADDING;
            graphics.drawString(font, truncate(font, title, WIDTH - CLOSE_SIZE - PADDING * 4 - (pageStack.size() > 1 ? BACK_SIZE + PADDING : 0)),
                    titleX, y + 7, TEXT, false);
        }

        // Close button (always).
        renderCloseButton(graphics, font, mouseX, mouseY);

        // Page content.
        if (!pageStack.isEmpty()) {
            pageStack.peek().render(graphics, mouseX, mouseY);
        }
    }

    private void renderBackButton(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        int bx = x + PADDING;
        int by = y + (HEADER_HEIGHT - BACK_SIZE) / 2;
        boolean hover = hits(mouseX, mouseY, bx, by, BACK_SIZE, BACK_SIZE);
        if (hover) graphics.fill(bx, by, bx + BACK_SIZE, by + BACK_SIZE, BUTTON_HOVER);
        graphics.drawString(font, "‹", bx + 3, by + 2, TEXT, false);
    }

    private void renderCloseButton(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        int cx = x + WIDTH - CLOSE_SIZE - PADDING;
        int cy = y + (HEADER_HEIGHT - CLOSE_SIZE) / 2;
        boolean hover = hits(mouseX, mouseY, cx, cy, CLOSE_SIZE, CLOSE_SIZE);
        if (hover) graphics.fill(cx, cy, cx + CLOSE_SIZE, cy + CLOSE_SIZE, BUTTON_HOVER);
        graphics.drawString(font, "×", cx + 3, cy + 2, TEXT_DIM, false);
    }

    public boolean mouseClicked(double sx, double sy, int button) {
        if (!open || !contains(sx, sy)) return false;
        if (button != 0) return true;

        // Header buttons.
        if (sy < y + HEADER_HEIGHT) {
            // Back button.
            int backX = x + PADDING;
            int backY = y + (HEADER_HEIGHT - BACK_SIZE) / 2;
            if (pageStack.size() > 1 && hits(sx, sy, backX, backY, BACK_SIZE, BACK_SIZE)) {
                popPage();
                return true;
            }
            // Close button.
            int closeX = x + WIDTH - CLOSE_SIZE - PADDING;
            int closeY = y + (HEADER_HEIGHT - CLOSE_SIZE) / 2;
            if (hits(sx, sy, closeX, closeY, CLOSE_SIZE, CLOSE_SIZE)) {
                close();
                if (onClose != null) onClose.run();
                return true;
            }
            return true;
        }

        // Page body.
        if (!pageStack.isEmpty()) {
            return pageStack.peek().mouseClicked(sx, sy, button);
        }
        return true;
    }

    public boolean mouseScrolled(double sx, double sy, double scrollY) {
        if (!open || !contains(sx, sy)) return false;
        if (!pageStack.isEmpty()) {
            return pageStack.peek().mouseScrolled(sx, sy, scrollY);
        }
        return true;
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

    // ─── Category page construction ────────────────────────────────────────────

    private CategoriesPage buildCategoriesPage() {
        return new CategoriesPage(
                Component.translatable("screen.cogwheel.left.categories"),
                List.of(
                        new CategoriesPage.Category(
                                new ItemStack(Items.CRAFTING_TABLE),
                                Component.translatable("screen.cogwheel.category.recipes"),
                                Component.translatable("screen.cogwheel.category.recipes.subtitle"),
                                true,
                                () -> pushPage(makeRecipesListPage())),
                        new CategoriesPage.Category(
                                new ItemStack(Items.CHEST),
                                Component.translatable("screen.cogwheel.category.sources"),
                                Component.translatable("screen.cogwheel.category.sources.subtitle"),
                                true,
                                () -> pushPage(makeSourcesListPage())),
                        new CategoriesPage.Category(
                                new ItemStack(Items.BARREL),
                                Component.translatable("screen.cogwheel.category.sinks"),
                                Component.translatable("screen.cogwheel.category.sinks.subtitle"),
                                true,
                                () -> pushPage(makeSinksListPage())),
                        new CategoriesPage.Category(
                                new ItemStack(Items.COMPARATOR),
                                Component.translatable("screen.cogwheel.category.logic"),
                                Component.translatable("screen.cogwheel.category.logic.subtitle"),
                                true,
                                () -> pushPage(buildLogicPage()))
                ));
    }

    private CategoriesPage buildLogicPage() {
        return new CategoriesPage(
                Component.translatable("screen.cogwheel.category.logic"),
                List.of(
                        new CategoriesPage.Category(
                                new ItemStack(Items.HOPPER),
                                Component.translatable("screen.cogwheel.logic.splitter"),
                                Component.translatable("screen.cogwheel.logic.splitter.subtitle"),
                                true,
                                () -> callbacks.onAddSplitter().run()),
                        new CategoriesPage.Category(
                                new ItemStack(Items.DROPPER),
                                Component.translatable("screen.cogwheel.logic.merger"),
                                Component.translatable("screen.cogwheel.logic.merger.subtitle"),
                                true,
                                () -> callbacks.onAddMerger().run())
                ));
    }

    private LeftPanelPage makeRecipesListPage() {
        // Items that at least one recipe in the index PRODUCES.
        List<Item> items = new ArrayList<>();
        for (Item item : index.indexedItems()) {
            if (!index.waysToProduce(item).isEmpty()) items.add(item);
        }
        items.sort(Comparator.comparing(LeftPanel::displayName, String.CASE_INSENSITIVE_ORDER));
        return new ItemListPage(
                Component.translatable("screen.cogwheel.category.recipes"),
                items,
                callbacks.onAddRecipe());
    }

    private LeftPanelPage makeSourcesListPage() {
        // Sources are arbitrary items — the user picks anything and declares it as available.
        List<Item> items = new ArrayList<>(index.indexedItems());
        items.sort(Comparator.comparing(LeftPanel::displayName, String.CASE_INSENSITIVE_ORDER));
        return new ItemListPage(
                Component.translatable("screen.cogwheel.category.sources"),
                items,
                callbacks.onAddSource());
    }

    private LeftPanelPage makeSinksListPage() {
        List<Item> items = new ArrayList<>(index.indexedItems());
        items.sort(Comparator.comparing(LeftPanel::displayName, String.CASE_INSENSITIVE_ORDER));
        return new ItemListPage(
                Component.translatable("screen.cogwheel.category.sinks"),
                items,
                callbacks.onAddSink());
    }

    private static String displayName(Item item) {
        return new ItemStack(item).getHoverName().getString();
    }
}
