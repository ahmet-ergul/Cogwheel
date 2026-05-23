package dev.kima.cogwheel.client.ui.panel;

import dev.kima.cogwheel.model.Factory;
import dev.kima.cogwheel.model.FactoryStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.UUID;

/**
 * Left-panel page that lists all factories in the {@link FactoryStore}. Each row offers:
 * <ul>
 *   <li>Click the name → switch to that factory (becomes the current one in the editor).</li>
 *   <li>{@code ✎} (pencil) → rename via a small text-input modal.</li>
 *   <li>{@code ×} → delete the factory (no confirmation in this pre-alpha; we will add one later).</li>
 * </ul>
 *
 * <p>A {@code + New factory} row at the top creates a fresh factory with an auto-generated name
 * that the user can rename right away.
 */
public final class FactoriesPage implements LeftPanelPage {

    private static final int ROW_HEIGHT = 22;
    private static final int PADDING = 6;
    private static final int FOOTER_HEIGHT = 14;
    private static final int BUTTON_SIZE = 14;
    /** Height of the Refresh + Open Folder strip at the bottom of the page. */
    private static final int ACTION_BAR_HEIGHT = 22;

    private static final int BG_HOVER = 0xFF2A3148;
    private static final int BG_CURRENT = 0xFF3A4868;
    private static final int TEXT = 0xFFEAEAEA;
    private static final int TEXT_DIM = 0xFFA8A8B8;
    private static final int TEXT_DANGER = 0xFFE86E6E;
    private static final int BUTTON_HOVER = 0xFF54607A;
    private static final int BORDER = 0xFF2A3148;

    private final LeftPanel.Callbacks callbacks;
    private int x, y, width, height;
    private double scroll;

    public FactoriesPage(LeftPanel.Callbacks callbacks) {
        this.callbacks = callbacks;
    }

    @Override
    public Component title() {
        return Component.translatable("screen.cogwheel.category.factories");
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

    private int listH() {
        return height - ROW_HEIGHT - PADDING - ACTION_BAR_HEIGHT - FOOTER_HEIGHT;
    }

    private int actionBarY() {
        return y + height - FOOTER_HEIGHT - ACTION_BAR_HEIGHT;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        Font font = Minecraft.getInstance().font;
        List<Factory> all = FactoryStore.get().all();
        UUID currentId = FactoryStore.get().currentId();

        // "+ New factory" pinned row at the top.
        int newRowY = y + PADDING;
        boolean newHover = inRow(mouseX, mouseY, newRowY);
        if (newHover) {
            graphics.fill(x + 2, newRowY, x + width - 2, newRowY + ROW_HEIGHT, BG_HOVER);
        }
        graphics.drawString(font, "+ New factory", x + PADDING, newRowY + 7, TEXT, false);

        // List of factories below the "+ New" row.
        int listY = newRowY + ROW_HEIGHT + 2;
        int listH = listH();
        graphics.fill(x + PADDING, listY - 1, x + width - PADDING, listY, BORDER);
        graphics.enableScissor(x, listY, x + width, listY + listH);

        int firstVisible = (int) (scroll / ROW_HEIGHT);
        int rowsVisible = listH / ROW_HEIGHT + 1;
        int last = Math.min(all.size(), firstVisible + rowsVisible + 1);
        for (int i = firstVisible; i < last; i++) {
            int rowY = listY + i * ROW_HEIGHT - (int) scroll;
            Factory f = all.get(i);
            boolean isCurrent = f.id().equals(currentId);
            boolean rowHover = mouseY >= rowY && mouseY < rowY + ROW_HEIGHT && mouseX >= x && mouseX <= x + width;
            int bg = isCurrent ? BG_CURRENT : (rowHover ? BG_HOVER : 0);
            if (bg != 0) {
                graphics.fill(x + 2, rowY, x + width - 2, rowY + ROW_HEIGHT, bg);
            }

            String name = f.name().isEmpty() ? "(unnamed)" : f.name();
            graphics.drawString(font, truncate(font, name, width - PADDING * 2 - BUTTON_SIZE * 2 - 8),
                    x + PADDING, rowY + 7, isCurrent ? TEXT : TEXT_DIM, false);

            // Buttons on the right: ✎ rename, × delete.
            int renameX = x + width - PADDING - BUTTON_SIZE * 2 - 2;
            int deleteX = x + width - PADDING - BUTTON_SIZE;
            int btnY = rowY + (ROW_HEIGHT - BUTTON_SIZE) / 2;
            drawButton(graphics, font, renameX, btnY, "✎", mouseX, mouseY, TEXT_DIM);
            drawButton(graphics, font, deleteX, btnY, "×", mouseX, mouseY, TEXT_DANGER);
        }
        graphics.disableScissor();

        // Scrollbar if list overflows.
        int contentH = all.size() * ROW_HEIGHT;
        if (contentH > listH) {
            int barH = Math.max(20, (int) ((double) listH / contentH * listH));
            double frac = (contentH == listH) ? 0 : scroll / (double) (contentH - listH);
            int barY = listY + (int) (frac * (listH - barH));
            graphics.fill(x + width - 4, barY, x + width - 1, barY + barH, BUTTON_HOVER);
        }

        // Action bar: Refresh + Open folder.
        int abY = actionBarY();
        graphics.fill(x, abY - 1, x + width, abY, BORDER);
        int btnH = ACTION_BAR_HEIGHT - 6;
        int halfW = (width - PADDING * 3) / 2;
        int refreshX = x + PADDING;
        int openX = refreshX + halfW + PADDING;
        int btnY = abY + 3;
        drawActionButton(graphics, font, refreshX, btnY, halfW, btnH, "Refresh", mouseX, mouseY);
        drawActionButton(graphics, font, openX, btnY, halfW, btnH, "Open folder", mouseX, mouseY);

        // Footer count.
        int footerY = y + height - FOOTER_HEIGHT + 2;
        graphics.fill(x, footerY - 2, x + width, footerY - 1, BORDER);
        graphics.drawString(font, Component.literal(all.size() + " factories"),
                x + PADDING, footerY + 2, TEXT_DIM, false);
    }

    private void drawActionButton(GuiGraphics graphics, Font font, int bx, int by, int bw, int bh,
                                  String label, int mouseX, int mouseY) {
        boolean hover = mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY < by + bh;
        graphics.fill(bx, by, bx + bw, by + bh, hover ? BUTTON_HOVER : BG_HOVER);
        int tw = font.width(label);
        graphics.drawString(font, label, bx + (bw - tw) / 2, by + (bh - 8) / 2, TEXT, false);
    }

    private boolean inRow(double mouseX, double mouseY, int rowY) {
        return mouseY >= rowY && mouseY < rowY + ROW_HEIGHT && mouseX >= x && mouseX <= x + width;
    }

    private void drawButton(GuiGraphics graphics, Font font, int x, int y, String label,
                            int mouseX, int mouseY, int color) {
        boolean hover = mouseX >= x && mouseX <= x + BUTTON_SIZE && mouseY >= y && mouseY < y + BUTTON_SIZE;
        if (hover) graphics.fill(x, y, x + BUTTON_SIZE, y + BUTTON_SIZE, BUTTON_HOVER);
        graphics.drawString(font, label, x + 4, y + 3, color, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        // Action bar buttons.
        int abY = actionBarY();
        if (mouseY >= abY && mouseY < abY + ACTION_BAR_HEIGHT) {
            int btnH = ACTION_BAR_HEIGHT - 6;
            int halfW = (width - PADDING * 3) / 2;
            int refreshX = x + PADDING;
            int openX = refreshX + halfW + PADDING;
            int btnY = abY + 3;
            if (mouseX >= refreshX && mouseX < refreshX + halfW && mouseY >= btnY && mouseY < btnY + btnH) {
                callbacks.onRefreshFactories().run();
                return true;
            }
            if (mouseX >= openX && mouseX < openX + halfW && mouseY >= btnY && mouseY < btnY + btnH) {
                callbacks.onOpenFactoriesFolder().run();
                return true;
            }
            return true;
        }

        // "+ New factory" pinned row.
        int newRowY = y + PADDING;
        if (inRow(mouseX, mouseY, newRowY)) {
            Minecraft.getInstance().setScreen(new dev.kima.cogwheel.client.ui.modal.TextInputModal(
                    Minecraft.getInstance().screen,
                    Component.literal("New factory name"),
                    "Factory " + (FactoryStore.get().all().size() + 1),
                    name -> {
                        if (!name.isEmpty()) callbacks.onCreateFactory().accept(name);
                    }));
            return true;
        }

        int listY = newRowY + ROW_HEIGHT + 2;
        int listH = listH();
        if (mouseY < listY || mouseY >= listY + listH) return true;

        List<Factory> all = FactoryStore.get().all();
        int relY = (int) (mouseY - listY + scroll);
        int index = relY / ROW_HEIGHT;
        if (index < 0 || index >= all.size()) return true;

        Factory clicked = all.get(index);
        int rowY = listY + index * ROW_HEIGHT - (int) scroll;
        int renameX = x + width - PADDING - BUTTON_SIZE * 2 - 2;
        int deleteX = x + width - PADDING - BUTTON_SIZE;
        int btnY = rowY + (ROW_HEIGHT - BUTTON_SIZE) / 2;

        // Rename button hit?
        if (mouseX >= renameX && mouseX < renameX + BUTTON_SIZE && mouseY >= btnY && mouseY < btnY + BUTTON_SIZE) {
            Minecraft.getInstance().setScreen(new dev.kima.cogwheel.client.ui.modal.TextInputModal(
                    Minecraft.getInstance().screen,
                    Component.literal("Rename factory"),
                    clicked.name(),
                    newName -> {
                        if (!newName.isEmpty()) callbacks.onRenameFactory().accept(clicked.id(), newName);
                    }));
            return true;
        }
        // Delete button hit?
        if (mouseX >= deleteX && mouseX < deleteX + BUTTON_SIZE && mouseY >= btnY && mouseY < btnY + BUTTON_SIZE) {
            callbacks.onDeleteFactory().accept(clicked.id());
            return true;
        }
        // Row body click → switch.
        callbacks.onSwitchFactory().accept(clicked.id());
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        int listH = listH();
        int contentH = FactoryStore.get().all().size() * ROW_HEIGHT;
        if (contentH <= listH) return true;
        scroll -= scrollY * ROW_HEIGHT * 2;
        scroll = Math.max(0, Math.min(contentH - listH, scroll));
        return true;
    }

    private static String truncate(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        while (text.length() > 0 && font.width(text + "…") > maxWidth) {
            text = text.substring(0, text.length() - 1);
        }
        return text + "…";
    }
}
