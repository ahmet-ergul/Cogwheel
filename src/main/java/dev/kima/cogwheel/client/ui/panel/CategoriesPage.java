package dev.kima.cogwheel.client.ui.panel;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Root page of {@link LeftPanel}: a vertical list of category cards. Clicking one fires its
 * {@link Category#onClick}, which typically pushes an {@link ItemListPage} onto the panel stack.
 *
 * <p>A disabled category (e.g. "Logic" before Phase 9e ships) renders grayed-out and ignores
 * clicks.
 */
public final class CategoriesPage implements LeftPanelPage {

    public record Category(ItemStack icon, Component label, Component subtitle, boolean enabled, Runnable onClick) {}

    private static final int ROW_HEIGHT = 28;
    private static final int PADDING = 6;
    private static final int BG_HOVER = 0xFF2A3148;
    private static final int TEXT = 0xFFEAEAEA;
    private static final int TEXT_DIM = 0xFFA8A8B8;
    private static final int TEXT_DISABLED = 0xFF5A6075;

    private final Component title;
    private final List<Category> categories;
    private int x, y, width, height;

    public CategoriesPage(Component title, List<Category> categories) {
        this.title = title;
        this.categories = categories;
    }

    @Override
    public Component title() {
        return title;
    }

    @Override public void onShow(WidgetHost host) {}
    @Override public void onHide(WidgetHost host) {}

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
            boolean hover = cat.enabled() && mouseX >= x && mouseX <= x + width
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            if (hover) {
                graphics.fill(x + 2, rowY, x + width - 2, rowY + ROW_HEIGHT, BG_HOVER);
            }
            if (!cat.icon().isEmpty()) {
                graphics.renderItem(cat.icon(), x + PADDING, rowY + 6);
            }
            int labelColor = cat.enabled() ? TEXT : TEXT_DISABLED;
            graphics.drawString(font, cat.label(), x + PADDING + 22, rowY + 5, labelColor, false);
            graphics.drawString(font, cat.subtitle(), x + PADDING + 22, rowY + 16,
                    cat.enabled() ? TEXT_DIM : TEXT_DISABLED, false);
            // Right-side chevron for enabled rows.
            if (cat.enabled()) {
                graphics.drawString(font, "›", x + width - 14, rowY + 10, TEXT_DIM, false);
            }
            rowY += ROW_HEIGHT;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        int rowY = y + PADDING;
        for (Category cat : categories) {
            if (mouseY >= rowY && mouseY < rowY + ROW_HEIGHT
                    && mouseX >= x && mouseX <= x + width) {
                if (cat.enabled()) {
                    cat.onClick().run();
                }
                return true;
            }
            rowY += ROW_HEIGHT;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        return false;
    }
}
