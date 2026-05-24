package dev.kima.cogwheel.client.ui.panel;

import dev.kima.cogwheel.client.ui.ScaledUi;
import dev.kima.cogwheel.model.Factory;
import dev.kima.cogwheel.model.PowerSource;
import dev.kima.cogwheel.model.PowerSourceStore;
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
    public static final int HEIGHT = 30;
    /** Extra strip for the SU readout when the factory has a power source assigned. */
    public static final int POWER_STRIP_HEIGHT = 14;
    private static final int PADDING = 5;
    private static final int BG = 0xEE131726;
    private static final int BORDER = 0xFF2A3148;
    private static final int LABEL = 0xFFA8A8B8;
    private static final int TEXT = 0xFFEAEAEA;
    private static final int ERROR = 0xFFE86E6E;
    private static final int WARN = 0xFFE8A040;
    private static final int OK = 0xFF6EE886;
    private static final int MAX_ITEMS_PER_COLUMN = 5;

    private SolverOverlay() {}

    /** Back-compat: render without power info. */
    public static void render(GuiGraphics graphics, SolverResult result, int x, int y, int w) {
        render(graphics, result, x, y, w, null);
    }

    public static void render(GuiGraphics graphics, SolverResult result, int x, int y, int w,
                               Factory factory) {
        Font font = Minecraft.getInstance().font;
        graphics.fill(x, y, x + w, y + HEIGHT, BG);
        graphics.fill(x, y - 1, x + w, y, BORDER);

        if (result.hasCycle()) {
            String msg = "⚠ Graph has a cycle — cannot solve.";
            ScaledUi.drawString(graphics, font,msg, x + PADDING, y + (HEIGHT - 8) / 2, ERROR, false);
            return;
        }

        int colW = (w - PADDING * 4) / 3;
        int col1 = x + PADDING;
        int col2 = col1 + colW + PADDING;
        int col3 = col2 + colW + PADDING;

        renderColumn(graphics, font, "NEEDS", result.unmetInputs(), col1, y, colW);
        renderColumn(graphics, font, "PRODUCES", result.finalOutputs(), col2, y, colW);
        renderColumn(graphics, font, "UNUSED", result.unusedOutputs(), col3, y, colW);

        // Power-info strip below the main row. Only shows when the factory or power source is
        // configured — keeps non-Create users from seeing irrelevant SU readouts.
        renderPowerStrip(graphics, font, factory, result, x, y + HEIGHT, w);
    }

    private static void renderPowerStrip(GuiGraphics graphics, Font font, Factory factory,
                                          SolverResult result, int x, int y, int w) {
        if (factory == null) return;
        double su = result.totalSu();
        PowerSource source = factory.powerSourceId().map(id -> PowerSourceStore.get().findById(id)).orElse(null);
        // Skip the strip entirely when there's nothing power-related to show.
        if (su <= 0 && source == null) return;

        graphics.fill(x, y, x + w, y + POWER_STRIP_HEIGHT, BG);
        graphics.fill(x, y, x + w, y + 1, BORDER);

        StringBuilder line = new StringBuilder();
        line.append("RPM ").append(factory.rpm()).append("  ·  ");
        line.append("SU ").append(formatSu(su));
        int color = TEXT;
        if (source != null) {
            // Sum SU across every factory pointing at this same power source.
            double budgetUsed = sumSuAgainstSource(source.id());
            double remaining = source.suCapacity() - budgetUsed;
            line.append(" / ").append(formatSu(source.suCapacity()))
                .append(" (").append(source.name()).append(")");
            if (remaining < 0) {
                line.append("  ⚠ over by ").append(formatSu(-remaining));
                color = ERROR;
            } else if (remaining < source.suCapacity() * 0.1) {
                color = WARN;
            } else {
                color = OK;
            }
        }
        ScaledUi.drawString(graphics, font, line.toString(), x + PADDING, y + 3, color);
    }

    /** Aggregate SU draw across every factory currently assigned to {@code sourceId}. Runs the
     *  solver for each, which is OK at v0.5 scale — a few factories means a few hundred-node solves
     *  per frame, well under any frame budget. Cache later if it becomes a bottleneck. */
    private static double sumSuAgainstSource(java.util.UUID sourceId) {
        double total = 0;
        for (Factory f : dev.kima.cogwheel.model.FactoryStore.get().all()) {
            if (f.powerSourceId().filter(sourceId::equals).isEmpty()) continue;
            SolverResult r = dev.kima.cogwheel.solver.Solver.solve(
                    f.design(), dev.kima.cogwheel.recipe.source.RebuildTrigger.index(), f.rpm());
            if (!r.hasCycle()) total += r.totalSu();
        }
        return total;
    }

    private static String formatSu(double su) {
        if (su >= 1_000_000) return String.format("%.2fM", su / 1_000_000);
        if (su >= 1_000) return String.format("%.1fk", su / 1_000);
        return String.format("%.0f", su);
    }

    private static void renderColumn(GuiGraphics graphics, Font font, String label,
                                     Map<Item, Double> data, int x, int y, int w) {
        ScaledUi.drawString(graphics, font,label, x, y + 2, LABEL, false);

        List<Map.Entry<Item, Double>> entries = data.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .sorted(Comparator.<Map.Entry<Item, Double>, Double>comparing(Map.Entry::getValue).reversed())
                .toList();
        if (entries.isEmpty()) {
            ScaledUi.drawString(graphics, font,"—", x, y + 14, TEXT, false);
            return;
        }

        int shown = 0;
        int cursorX = x;
        int rowY = y + 12;
        for (Map.Entry<Item, Double> entry : entries) {
            if (shown >= MAX_ITEMS_PER_COLUMN) {
                int remaining = entries.size() - shown;
                ScaledUi.drawString(graphics, font,"+" + remaining + " more", cursorX, rowY + 4, LABEL, false);
                break;
            }
            ItemStack stack = new ItemStack(entry.getKey());
            renderScaledItem(graphics, stack, cursorX, rowY, 0.75f);
            String num = formatRate(entry.getValue());
            int numW = ScaledUi.scaledWidth(font, num);
            int iconCellW = 14;
            if (cursorX + iconCellW + numW > x + w) break;
            ScaledUi.drawString(graphics, font,num, cursorX + iconCellW, rowY + 4, TEXT, false);
            cursorX += iconCellW + numW + 6;
            shown++;
            if (cursorX > x + w) break;
        }
    }

    private static void renderScaledItem(GuiGraphics graphics, ItemStack stack, int x, int y, float scale) {
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(scale, scale, 1f);
        graphics.renderItem(stack, 0, 0);
        pose.popPose();
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
