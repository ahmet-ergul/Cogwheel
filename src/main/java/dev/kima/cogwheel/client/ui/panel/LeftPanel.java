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
    public static final int WIDTH = 152;
    private static final int HEADER_HEIGHT = 18;
    private static final int PADDING = 5;
    private static final int CLOSE_SIZE = 10;
    private static final int BACK_SIZE = 10;

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
            Runnable onAddMerger,
            Consumer<Item> onAddOutput,
            java.util.function.Consumer<java.util.UUID> onSwitchFactory,
            Consumer<String> onCreateFactory,
            java.util.function.Consumer<java.util.UUID> onDeleteFactory,
            java.util.function.BiConsumer<java.util.UUID, String> onRenameFactory,
            Runnable onRefreshFactories,
            Runnable onOpenFactoriesFolder,
            Runnable onAddLimiter,
            Runnable onAddFilter,
            Runnable onAddCluster
    ) {}

    /** Mutable so the panel survives a parent-screen re-init: the WidgetHost is bound to a Screen
     *  instance, and clearing/re-adding widgets needs an up-to-date reference. */
    private LeftPanelPage.WidgetHost widgetHost;
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

    /** Called when the parent screen re-inits (which clears all child widgets). Re-binds the
     *  WidgetHost and re-registers the current page's widgets so search boxes etc. survive
     *  navigating into JEI and back. */
    public void reattachWidgetHost(LeftPanelPage.WidgetHost host) {
        this.widgetHost = host;
        if (open && !pageStack.isEmpty()) {
            pageStack.peek().onShow(host);
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

    /** Forward to the current page. Returning false here means the parent screen will keep
     *  processing the event (so node drags etc. still work outside the panel). */
    public boolean mouseReleased(double sx, double sy, int button) {
        if (!open || pageStack.isEmpty()) return false;
        return pageStack.peek().mouseReleased(sx, sy, button);
    }

    /** Notify the current page of mouse movement during a drag — used by ItemListPage to promote
     *  a click into a drag once the cursor crosses the drag threshold. */
    public void mouseDragged(double sx, double sy) {
        if (!open || pageStack.isEmpty()) return;
        if (pageStack.peek() instanceof ItemListPage ilp) {
            ilp.mouseDragged(sx, sy);
        }
    }

    /** R/U on hover come through here; the page decides what to do based on the hovered item. */
    public boolean keyPressed(int keyCode, int scanCode, int modifiers, int mouseX, int mouseY) {
        if (!open || pageStack.isEmpty()) return false;
        return pageStack.peek().keyPressed(keyCode, scanCode, modifiers, mouseX, mouseY);
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
                                new ItemStack(Items.ENDER_CHEST),
                                Component.translatable("screen.cogwheel.category.outputs"),
                                Component.translatable("screen.cogwheel.category.outputs.subtitle"),
                                true,
                                () -> pushPage(makeOutputsListPage())),
                        new CategoriesPage.Category(
                                new ItemStack(Items.COMPARATOR),
                                Component.translatable("screen.cogwheel.category.logic"),
                                Component.translatable("screen.cogwheel.category.logic.subtitle"),
                                true,
                                () -> pushPage(buildLogicPage())),
                        new CategoriesPage.Category(
                                new ItemStack(Items.BOOK),
                                Component.translatable("screen.cogwheel.category.factories"),
                                Component.translatable("screen.cogwheel.category.factories.subtitle"),
                                true,
                                () -> pushPage(new FactoriesPage(callbacks))),
                        new CategoriesPage.Category(
                                new ItemStack(Items.REDSTONE),
                                Component.translatable("screen.cogwheel.category.settings"),
                                Component.translatable("screen.cogwheel.category.settings.subtitle"),
                                true,
                                true, // separatorAbove: visually peel off from feature categories
                                () -> pushPage(new SettingsPage()))
                ));
    }

    private LeftPanelPage makeOutputsListPage() {
        List<Item> items = new ArrayList<>(index.indexedItems());
        items.sort(Comparator.comparing(LeftPanel::displayName, String.CASE_INSENSITIVE_ORDER));
        return new ItemListPage(
                Component.translatable("screen.cogwheel.category.outputs"),
                items,
                callbacks.onAddOutput());
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
                                () -> callbacks.onAddMerger().run()),
                        new CategoriesPage.Category(
                                new ItemStack(Items.REDSTONE_TORCH),
                                Component.translatable("screen.cogwheel.logic.limiter"),
                                Component.translatable("screen.cogwheel.logic.limiter.subtitle"),
                                true,
                                () -> callbacks.onAddLimiter().run()),
                        new CategoriesPage.Category(
                                new ItemStack(Items.COMPARATOR),
                                Component.translatable("screen.cogwheel.logic.filter"),
                                Component.translatable("screen.cogwheel.logic.filter.subtitle"),
                                true,
                                () -> callbacks.onAddFilter().run()),
                        new CategoriesPage.Category(
                                new ItemStack(Items.BUNDLE),
                                Component.translatable("screen.cogwheel.logic.cluster"),
                                Component.translatable("screen.cogwheel.logic.cluster.subtitle"),
                                true,
                                () -> callbacks.onAddCluster().run())
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
