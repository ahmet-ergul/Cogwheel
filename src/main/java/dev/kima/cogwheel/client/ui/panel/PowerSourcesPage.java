package dev.kima.cogwheel.client.ui.panel;

import dev.kima.cogwheel.client.ui.ScaledUi;
import dev.kima.cogwheel.client.ui.modal.TextInputModal;
import dev.kima.cogwheel.model.Factory;
import dev.kima.cogwheel.model.FactoryStore;
import dev.kima.cogwheel.model.PowerSource;
import dev.kima.cogwheel.model.PowerSourceStore;
import dev.kima.cogwheel.recipe.source.RebuildTrigger;
import dev.kima.cogwheel.solver.Solver;
import dev.kima.cogwheel.solver.SolverResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.UUID;

/**
 * Left-panel page: list every {@link PowerSource} in the store with its name, capacity, and
 * current consumption (summed across every {@link Factory} that points at it). Each row exposes:
 *
 * <ul>
 *   <li>Click the name → rename modal.</li>
 *   <li>{@code ⚙} → set capacity modal.</li>
 *   <li>{@code ×} → delete the source. Factories that referenced it are NOT modified; their
 *       {@code powerSourceId} simply fails to resolve afterwards.</li>
 * </ul>
 *
 * <p>A pinned {@code + New power source} row at the top creates a fresh one with a default name
 * ({@code "Power Source N"}) and capacity ({@code 1024 SU}).
 */
public final class PowerSourcesPage implements LeftPanelPage {

    private static final int ROW_HEIGHT = 30;
    private static final int PADDING = 6;
    private static final int FOOTER_HEIGHT = 14;
    private static final int BUTTON_SIZE = 14;

    private static final int BG_HOVER = 0xFF2A3148;
    private static final int TEXT = 0xFFEAEAEA;
    private static final int TEXT_DIM = 0xFFA8A8B8;
    private static final int TEXT_DANGER = 0xFFE86E6E;
    private static final int TEXT_OK = 0xFF6EE886;
    private static final int TEXT_WARN = 0xFFE8A040;
    private static final int BUTTON_HOVER = 0xFF54607A;
    private static final int BORDER = 0xFF2A3148;

    private int x, y, width, height;
    private double scroll;

    @Override
    public Component title() {
        return Component.translatable("screen.cogwheel.category.power");
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
        return height - ROW_HEIGHT - PADDING - FOOTER_HEIGHT;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        Font font = Minecraft.getInstance().font;
        List<PowerSource> all = PowerSourceStore.get().all();

        // Pinned "+ New" row.
        int newRowY = y + PADDING;
        boolean newHover = inRow(mouseX, mouseY, newRowY);
        if (newHover) graphics.fill(x + 2, newRowY, x + width - 2, newRowY + ROW_HEIGHT, BG_HOVER);
        ScaledUi.drawString(graphics, font, "+ New power source", x + PADDING, newRowY + 11, TEXT);

        int listY = newRowY + ROW_HEIGHT + 2;
        int listH = listH();
        graphics.fill(x + PADDING, listY - 1, x + width - PADDING, listY, BORDER);
        graphics.enableScissor(x, listY, x + width, listY + listH);

        int firstVisible = (int) (scroll / ROW_HEIGHT);
        int rowsVisible = listH / ROW_HEIGHT + 1;
        int last = Math.min(all.size(), firstVisible + rowsVisible + 1);
        for (int i = firstVisible; i < last; i++) {
            int rowY = listY + i * ROW_HEIGHT - (int) scroll;
            PowerSource ps = all.get(i);
            boolean hover = mouseY >= rowY && mouseY < rowY + ROW_HEIGHT && mouseX >= x && mouseX <= x + width;
            if (hover) graphics.fill(x + 2, rowY, x + width - 2, rowY + ROW_HEIGHT, BG_HOVER);

            String name = ps.name().isEmpty() ? "(unnamed)" : ps.name();
            ScaledUi.drawString(graphics, font,
                    ScaledUi.truncate(font, name, width - PADDING * 2 - BUTTON_SIZE * 2 - 8),
                    x + PADDING, rowY + 4, TEXT);

            // Capacity + usage line below name.
            double used = sumSuAgainstSource(ps.id());
            double cap = ps.suCapacity();
            double remaining = cap - used;
            String budget = formatSu(used) + " / " + formatSu(cap);
            int budgetColor = remaining < 0 ? TEXT_DANGER : (remaining < cap * 0.1 ? TEXT_WARN : TEXT_OK);
            ScaledUi.drawString(graphics, font, budget, x + PADDING, rowY + 16, budgetColor);

            // Buttons: ⚙ capacity, × delete.
            int capX = x + width - PADDING - BUTTON_SIZE * 2 - 2;
            int delX = x + width - PADDING - BUTTON_SIZE;
            int btnY = rowY + (ROW_HEIGHT - BUTTON_SIZE) / 2;
            drawButton(graphics, font, capX, btnY, "⚙", mouseX, mouseY, TEXT_DIM);
            drawButton(graphics, font, delX, btnY, "×", mouseX, mouseY, TEXT_DANGER);
        }
        graphics.disableScissor();

        // Scrollbar.
        int contentH = all.size() * ROW_HEIGHT;
        if (contentH > listH) {
            int barH = Math.max(20, (int) ((double) listH / contentH * listH));
            double frac = (contentH == listH) ? 0 : scroll / (double) (contentH - listH);
            int barY = listY + (int) (frac * (listH - barH));
            graphics.fill(x + width - 4, barY, x + width - 1, barY + barH, BUTTON_HOVER);
        }

        // Footer count.
        int footerY = y + height - FOOTER_HEIGHT + 2;
        graphics.fill(x, footerY - 2, x + width, footerY - 1, BORDER);
        ScaledUi.drawString(graphics, font, all.size() + " sources",
                x + PADDING, footerY + 2, TEXT_DIM);
    }

    private static double sumSuAgainstSource(UUID sourceId) {
        double total = 0;
        for (Factory f : FactoryStore.get().all()) {
            if (f.powerSourceId().filter(sourceId::equals).isEmpty()) continue;
            SolverResult r = Solver.solve(f.design(), RebuildTrigger.index(), f.rpm());
            if (!r.hasCycle()) total += r.totalSu();
        }
        return total;
    }

    private static String formatSu(double su) {
        if (su >= 1_000_000) return String.format("%.2fM", su / 1_000_000);
        if (su >= 1_000) return String.format("%.1fk", su / 1_000);
        return String.format("%.0f", su);
    }

    private boolean inRow(double mouseX, double mouseY, int rowY) {
        return mouseY >= rowY && mouseY < rowY + ROW_HEIGHT && mouseX >= x && mouseX <= x + width;
    }

    private void drawButton(GuiGraphics graphics, Font font, int bx, int by, String label,
                             int mouseX, int mouseY, int color) {
        boolean hover = mouseX >= bx && mouseX <= bx + BUTTON_SIZE && mouseY >= by && mouseY < by + BUTTON_SIZE;
        if (hover) graphics.fill(bx, by, bx + BUTTON_SIZE, by + BUTTON_SIZE, BUTTON_HOVER);
        ScaledUi.drawString(graphics, font, label, bx + 4, by + 3, color);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        // "+ New" row.
        int newRowY = y + PADDING;
        if (inRow(mouseX, mouseY, newRowY)) {
            int n = PowerSourceStore.get().all().size() + 1;
            PowerSourceStore.get().create("Power Source " + n, 1024.0);
            return true;
        }

        int listY = newRowY + ROW_HEIGHT + 2;
        int listH = listH();
        if (mouseY < listY || mouseY >= listY + listH) return true;

        List<PowerSource> all = PowerSourceStore.get().all();
        int relY = (int) (mouseY - listY + scroll);
        int index = relY / ROW_HEIGHT;
        if (index < 0 || index >= all.size()) return true;

        PowerSource clicked = all.get(index);
        int rowY = listY + index * ROW_HEIGHT - (int) scroll;
        int capX = x + width - PADDING - BUTTON_SIZE * 2 - 2;
        int delX = x + width - PADDING - BUTTON_SIZE;
        int btnY = rowY + (ROW_HEIGHT - BUTTON_SIZE) / 2;

        // Capacity (⚙) button.
        if (mouseX >= capX && mouseX < capX + BUTTON_SIZE && mouseY >= btnY && mouseY < btnY + BUTTON_SIZE) {
            Minecraft.getInstance().setScreen(new TextInputModal(
                    Minecraft.getInstance().screen,
                    Component.literal("Capacity (SU)"),
                    Long.toString(Math.round(clicked.suCapacity())),
                    raw -> {
                        try {
                            double v = Double.parseDouble(raw.trim());
                            PowerSourceStore.get().setCapacity(clicked.id(), v);
                        } catch (NumberFormatException ignored) {}
                    }));
            return true;
        }
        // Delete (×) button.
        if (mouseX >= delX && mouseX < delX + BUTTON_SIZE && mouseY >= btnY && mouseY < btnY + BUTTON_SIZE) {
            PowerSourceStore.get().delete(clicked.id());
            return true;
        }
        // Row body → rename.
        Minecraft.getInstance().setScreen(new TextInputModal(
                Minecraft.getInstance().screen,
                Component.literal("Rename power source"),
                clicked.name(),
                newName -> {
                    if (!newName.isEmpty()) PowerSourceStore.get().rename(clicked.id(), newName);
                }));
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        int listH = listH();
        int contentH = PowerSourceStore.get().all().size() * ROW_HEIGHT;
        if (contentH <= listH) return true;
        scroll -= scrollY * ROW_HEIGHT * 2;
        scroll = Math.max(0, Math.min(contentH - listH, scroll));
        return true;
    }
}
