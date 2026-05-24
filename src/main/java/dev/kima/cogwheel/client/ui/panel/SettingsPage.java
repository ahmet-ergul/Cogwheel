package dev.kima.cogwheel.client.ui.panel;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Placeholder Settings page. Populated in a future version with editor preferences (canvas grid
 * density, auto-save behavior, JEI integration toggles, etc.).
 */
public final class SettingsPage implements LeftPanelPage {

    private static final int TEXT_DIM = 0xFFA8A8B8;
    private static final int PADDING = 8;

    private int x, y, width, height;

    @Override
    public Component title() {
        return Component.translatable("screen.cogwheel.category.settings");
    }

    @Override public void onShow(WidgetHost host) {}
    @Override public void onHide(WidgetHost host) {}

    @Override
    public void setLayout(int x, int y, int width, int height) {
        this.x = x; this.y = y; this.width = width; this.height = height;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(Minecraft.getInstance().font,
                "No settings yet.", x + PADDING, y + PADDING, TEXT_DIM, false);
        graphics.drawString(Minecraft.getInstance().font,
                "Coming in a future version.", x + PADDING, y + PADDING + 12, TEXT_DIM, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) { return false; }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) { return false; }
}
