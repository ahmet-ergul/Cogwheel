package dev.kima.cogwheel.client.ui.panel;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * One page of the stacking left-panel navigation. Pages are pushed/popped on a {@link LeftPanel}
 * stack. The root page is {@link CategoriesPage}; drilling into a category pushes an
 * {@link ItemListPage} (or future logic / settings page).
 *
 * <p>Pages own their per-page vanilla widgets (e.g. search EditBox) and are responsible for
 * registering with the host on {@link #onShow} and removing themselves on {@link #onHide}.
 */
public sealed interface LeftPanelPage permits CategoriesPage, ItemListPage, FactoriesPage, SettingsPage, PowerSourcesPage {

    /**
     * Bridge to the parent {@code Screen}'s protected widget-management methods. EditorScreen
     * provides an implementation that delegates to its own {@code addRenderableWidget} /
     * {@code removeWidget}; pages can't access those directly from outside the Screen subclass.
     */
    interface WidgetHost {
        void register(EditBox widget);
        void unregister(EditBox widget);
    }

    /** Displayed in the panel header. */
    Component title();

    /** Called when this page becomes the active (top) page on the stack. */
    void onShow(WidgetHost host);

    /** Called when this page is being popped or another page is being pushed over it. */
    void onHide(WidgetHost host);

    /** Configure layout — called once per show and whenever the panel resizes. */
    void setLayout(int x, int y, int width, int height);

    void render(GuiGraphics graphics, int mouseX, int mouseY);

    boolean mouseClicked(double mouseX, double mouseY, int button);

    boolean mouseScrolled(double mouseX, double mouseY, double scrollY);

    /** Optional hooks — most pages don't care. ItemListPage uses these for drag-to-add and R/U. */
    default boolean mouseReleased(double mouseX, double mouseY, int button) { return false; }
    default boolean keyPressed(int keyCode, int scanCode, int modifiers, int mouseX, int mouseY) { return false; }
}
