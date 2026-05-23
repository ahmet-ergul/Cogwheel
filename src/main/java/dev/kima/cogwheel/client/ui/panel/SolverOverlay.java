package dev.kima.cogwheel.client.ui.panel;

import dev.kima.cogwheel.solver.SolverResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Bottom-of-canvas status bar showing the solver's verdict: unmet inputs, final outputs,
 * unused outputs, or a cycle warning. Renders three columns left-to-right, each a small list of
 * item-icon + rate.
 */
public final class SolverOverlay {
    public static final int HEIGHT = 28;
    private static final int PADDING = 6;
    private static final int BG = 0xEE131726;
    private static final int BORDER = 0xFF2A3148;
    private static final int LABEL = 0xFFA8A8B8;
    private static final int TEXT = 0xFFEAEAEA;
    private static final int ERROR = 0xFFE86E6E;
    private static final int MAX_ITEMS_PER_COLUMN = 5;

    private SolverOverlay() {}

    public static void render(GuiGraphics graphics, SolverResult result, int x, int y, int w) {
        Font font = Minecraft.getInstance().font;
        graphics.fill(x, y, x + w, y + HEIGHT, BG);
        graphics.fill(x, y - 1, x + w, y, BORDER);

        if (result.hasCycle()) {
            String msg = "⚠ Graph has a cycle — cannot solve.";
            graphics.drawString(font, msg, x + PADDING, y + (HEIGHT - 8) / 2, ERROR, false);
            return;
        }

        int colW = (w - PADDING * 4) / 3;
        int col1 = x + PADDING;
        int col2 = col1 + colW + PADDING;
        int col3 = col2 + colW + PADDING;

        renderColumn(graphics, font, "NEEDS", result.unmetInputs(), col1, y, colW);
        renderColumn(graphics, font, "PRODUCES", result.finalOutputs(), col2, y, colW);
        renderColumn(graphics, font, "UNUSED", result.unusedOutputs(), col3, y, colW);
    }

    private static void renderColumn(GuiGraphics graphics, Font font, String label,
                                     Map<Item, Double> data, int x, int y, int w) {
        graphics.drawString(font, label, x, y + 2, LABEL, false);

        List<Map.Entry<Item, Double>> entries = data.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .sorted(Comparator.<Map.Entry<Item, Double>, Double>comparing(Map.Entry::getValue).reversed())
                .toList();
        if (entries.isEmpty()) {
            graphics.drawString(font, "—", x, y + 14, TEXT, false);
            return;
        }

        int shown = 0;
        int cursorX = x;
        int rowY = y + 12;
        for (Map.Entry<Item, Double> entry : entries) {
            if (shown >= MAX_ITEMS_PER_COLUMN) {
                int remaining = entries.size() - shown;
                graphics.drawString(font, "+" + remaining + " more", cursorX, rowY + 4, LABEL, false);
                break;
            }
            ItemStack stack = new ItemStack(entry.getKey());
            graphics.renderItem(stack, cursorX, rowY);
            String num = formatRate(entry.getValue());
            int numW = font.width(num);
            if (cursorX + 18 + numW > x + w) break;
            graphics.drawString(font, num, cursorX + 18, rowY + 4, TEXT, false);
            cursorX += 18 + numW + 8;
            shown++;
            if (cursorX > x + w) break;
        }
    }

    private static String formatRate(double itemsPerMin) {
        if (itemsPerMin >= 1000) {
            return String.format("%.1fk/m", itemsPerMin / 1000);
        }
        if (itemsPerMin >= 10) {
            return String.format("%.0f/m", itemsPerMin);
        }
        return String.format("%.1f/m", itemsPerMin);
    }
}
