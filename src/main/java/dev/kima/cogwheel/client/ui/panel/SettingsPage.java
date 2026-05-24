package dev.kima.cogwheel.client.ui.panel;

import dev.kima.cogwheel.client.ui.modal.ColorPickerModal;
import dev.kima.cogwheel.settings.CogwheelSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Editor settings page — three top-level tabs (Colors / Texts / Cogwheel), each with a scrollable
 * list of rows. Color rows have a clickable swatch (opens {@link ColorPickerModal}) + label +
 * per-row reset. Numeric rows have stepper controls. Bool rows have a checkbox. A "Reset all to
 * defaults" footer wipes every override across every tab.
 */
public final class SettingsPage implements LeftPanelPage {

    private static final int PADDING = 5;
    private static final int TAB_HEIGHT = 16;
    private static final int SECTION_GAP = 6;
    private static final int SECTION_HEADER_HEIGHT = 12;
    private static final int ROW_HEIGHT = 20;
    private static final int SWATCH_SIZE = 14;
    private static final int CHECKBOX_SIZE = 12;
    private static final int RESET_SIZE = 12;
    private static final int FOOTER_HEIGHT = 16;
    private static final int STEPPER_SIZE = 12;

    private static final int TEXT_DIM = 0xFFA8A8B8;
    private static final int BORDER = 0xFF2A3148;
    private static final int SCROLLBAR = 0xFF54607A;
    private static final int HOVER_BG = 0xFF3A4868;
    private static final int TAB_BG_ACTIVE = 0xFF3A4868;
    private static final int TAB_BG_INACTIVE = 0xFF1A1E2A;

    private CogwheelSettings.Category activeTab = CogwheelSettings.Category.COLORS;
    /** Per-tab scroll offsets so switching back doesn't lose your place. */
    private final java.util.EnumMap<CogwheelSettings.Category, Double> scrollByTab =
            new java.util.EnumMap<>(CogwheelSettings.Category.class);
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

    private int tabsBottomY() { return y + TAB_HEIGHT; }
    private int listY() { return tabsBottomY() + PADDING; }
    private int listH() { return height - (listY() - y) - FOOTER_HEIGHT; }

    private double scroll() { return scrollByTab.getOrDefault(activeTab, 0.0); }
    private void setScroll(double v) { scrollByTab.put(activeTab, v); }

    /** Row kinds the layout iterator emits — drives both render and click dispatch. */
    private record RowLayout(int yWithinList, RowKind kind, Object key) {}
    private enum RowKind { COLOR, NUM, BOOL, HEADER }

    /** Build the list of rows for the currently active tab. */
    private java.util.List<RowLayout> layout() {
        java.util.List<RowLayout> out = new java.util.ArrayList<>();
        int cursor = 0;
        CogwheelSettings.Section curSection = null;

        // Group by Section within this tab (preserving section enum order). Per-section list of
        // entries holds Key + NumKey + BoolKey in declaration order.
        java.util.LinkedHashMap<CogwheelSettings.Section, java.util.List<Object>> grouped =
                new java.util.LinkedHashMap<>();
        for (CogwheelSettings.Key k : CogwheelSettings.Key.values()) {
            if (k.section.category != activeTab) continue;
            grouped.computeIfAbsent(k.section, s -> new java.util.ArrayList<>()).add(k);
        }
        for (CogwheelSettings.NumKey k : CogwheelSettings.NumKey.values()) {
            if (k.section.category != activeTab) continue;
            grouped.computeIfAbsent(k.section, s -> new java.util.ArrayList<>()).add(k);
        }
        for (CogwheelSettings.BoolKey k : CogwheelSettings.BoolKey.values()) {
            if (k.section.category != activeTab) continue;
            grouped.computeIfAbsent(k.section, s -> new java.util.ArrayList<>()).add(k);
        }
        for (var entry : grouped.entrySet()) {
            if (curSection != null) cursor += SECTION_GAP;
            curSection = entry.getKey();
            out.add(new RowLayout(cursor, RowKind.HEADER, curSection));
            cursor += SECTION_HEADER_HEIGHT;
            for (Object o : entry.getValue()) {
                RowKind kind;
                if (o instanceof CogwheelSettings.Key)         kind = RowKind.COLOR;
                else if (o instanceof CogwheelSettings.NumKey) kind = RowKind.NUM;
                else                                            kind = RowKind.BOOL;
                out.add(new RowLayout(cursor, kind, o));
                cursor += ROW_HEIGHT;
            }
        }
        return out;
    }

    private int contentHeight() {
        java.util.List<RowLayout> rows = layout();
        if (rows.isEmpty()) return 0;
        RowLayout last = rows.get(rows.size() - 1);
        int base = last.kind == RowKind.HEADER ? SECTION_HEADER_HEIGHT : ROW_HEIGHT;
        return last.yWithinList + base;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        Font font = Minecraft.getInstance().font;

        renderTabs(graphics, font, mouseX, mouseY);

        int listY = listY();
        int listH = listH();
        int contentH = contentHeight();

        graphics.enableScissor(x, listY, x + width, listY + listH);
        for (RowLayout row : layout()) {
            int rowY = listY + row.yWithinList - (int) scroll();
            if (rowY + ROW_HEIGHT < listY || rowY > listY + listH) continue;
            switch (row.kind) {
                case HEADER -> renderHeader(graphics, font, (CogwheelSettings.Section) row.key, rowY);
                case COLOR  -> renderColorRow(graphics, font, (CogwheelSettings.Key) row.key, rowY, mouseX, mouseY);
                case NUM    -> renderNumRow(graphics, font, (CogwheelSettings.NumKey) row.key, rowY, mouseX, mouseY);
                case BOOL   -> renderBoolRow(graphics, font, (CogwheelSettings.BoolKey) row.key, rowY, mouseX, mouseY);
            }
        }
        graphics.disableScissor();

        if (contentH > listH) {
            int barH = Math.max(20, (int) ((double) listH / contentH * listH));
            double frac = scroll() / (double) (contentH - listH);
            int barY = listY + (int) (frac * (listH - barH));
            graphics.fill(x + width - 4, barY, x + width - 1, barY + barH, SCROLLBAR);
        }

        // Footer: Reset all
        int footerY = y + height - FOOTER_HEIGHT;
        graphics.fill(x, footerY, x + width, footerY + 1, BORDER);
        boolean hoverReset = mouseY >= footerY + 2 && mouseY < footerY + FOOTER_HEIGHT - 2
                && mouseX >= x + PADDING && mouseX <= x + width - PADDING;
        if (hoverReset) graphics.fill(x + PADDING, footerY + 2, x + width - PADDING,
                footerY + FOOTER_HEIGHT - 2, HOVER_BG);
        String label = "Reset all to defaults";
        int tw = font.width(label);
        graphics.drawString(font, label, x + (width - tw) / 2, footerY + 4, TEXT_DIM, false);
    }

    private void renderTabs(GuiGraphics g, Font font, int mouseX, int mouseY) {
        CogwheelSettings.Category[] tabs = CogwheelSettings.Category.values();
        int tabW = width / tabs.length;
        for (int i = 0; i < tabs.length; i++) {
            int tx = x + i * tabW;
            int tw = (i == tabs.length - 1) ? (width - i * tabW) : tabW;
            boolean active = tabs[i] == activeTab;
            boolean hover = mouseX >= tx && mouseX < tx + tw && mouseY >= y && mouseY < y + TAB_HEIGHT;
            int bg = active ? TAB_BG_ACTIVE : (hover ? HOVER_BG : TAB_BG_INACTIVE);
            g.fill(tx, y, tx + tw, y + TAB_HEIGHT, bg);
            String label = tabLabel(tabs[i]);
            int textW = font.width(label);
            g.drawString(font, label, tx + (tw - textW) / 2, y + 4,
                    active ? 0xFFEAEAEA : TEXT_DIM, false);
        }
        g.fill(x, y + TAB_HEIGHT, x + width, y + TAB_HEIGHT + 1, BORDER);
    }

    private static String tabLabel(CogwheelSettings.Category c) {
        return switch (c) {
            case COLORS   -> "Colors";
            case TEXTS    -> "Texts";
            case COGWHEEL -> "Cogwheel";
        };
    }

    private void renderHeader(GuiGraphics g, Font font, CogwheelSettings.Section section, int rowY) {
        g.drawString(font, section.label, x + PADDING, rowY + 2, TEXT_DIM, false);
        g.fill(x + PADDING, rowY + SECTION_HEADER_HEIGHT - 2,
                x + width - PADDING, rowY + SECTION_HEADER_HEIGHT - 1, BORDER);
    }

    private void renderColorRow(GuiGraphics g, Font font, CogwheelSettings.Key key, int rowY,
                                int mouseX, int mouseY) {
        int currentColor = CogwheelSettings.get().color(key);
        int swX = x + PADDING;
        int swY = rowY + (ROW_HEIGHT - SWATCH_SIZE) / 2;
        // Checkerboard so partial alpha shows.
        for (int dx = 0; dx < SWATCH_SIZE; dx += 4) {
            for (int dy = 0; dy < SWATCH_SIZE; dy += 4) {
                boolean dark = ((dx / 4) + (dy / 4)) % 2 == 0;
                g.fill(swX + dx, swY + dy,
                        Math.min(swX + dx + 4, swX + SWATCH_SIZE),
                        Math.min(swY + dy + 4, swY + SWATCH_SIZE),
                        dark ? 0xFF707070 : 0xFF404040);
            }
        }
        g.fill(swX, swY, swX + SWATCH_SIZE, swY + SWATCH_SIZE, currentColor);
        boolean hoverSwatch = mouseX >= swX && mouseX < swX + SWATCH_SIZE
                && mouseY >= swY && mouseY < swY + SWATCH_SIZE;
        int outlineColor = hoverSwatch ? 0xFFFFCC55 : BORDER;
        outline(g, swX - 1, swY - 1, SWATCH_SIZE + 2, SWATCH_SIZE + 2, outlineColor);

        int labelX = swX + SWATCH_SIZE + 5;
        int labelMaxW = (x + width - PADDING - RESET_SIZE - 3) - labelX - 2;
        int textColor = CogwheelSettings.get().isOverridden(key)
                ? CogwheelSettings.c(CogwheelSettings.Key.TEXT)
                : TEXT_DIM;
        g.drawString(font, truncate(font, key.label, labelMaxW),
                labelX, rowY + (ROW_HEIGHT - 8) / 2, textColor, false);

        renderResetButton(g, font, x + width - PADDING - RESET_SIZE, rowY, mouseX, mouseY);
    }

    private void renderNumRow(GuiGraphics g, Font font, CogwheelSettings.NumKey key, int rowY,
                              int mouseX, int mouseY) {
        int labelX = x + PADDING;
        float value = CogwheelSettings.get().num(key);
        String valStr = String.format("%.2f", value);
        int valW = font.width(valStr);
        int resetX = x + width - PADDING - RESET_SIZE;
        int plusX = resetX - STEPPER_SIZE - 2;
        int valTextX = plusX - valW - 4;
        int minusX = valTextX - STEPPER_SIZE - 4;

        int labelMaxW = minusX - labelX - 4;
        int textColor = CogwheelSettings.get().isOverridden(key)
                ? CogwheelSettings.c(CogwheelSettings.Key.TEXT)
                : TEXT_DIM;
        g.drawString(font, truncate(font, key.label, labelMaxW),
                labelX, rowY + (ROW_HEIGHT - 8) / 2, textColor, false);

        renderStepper(g, font, minusX, rowY, "−", mouseX, mouseY);
        g.drawString(font, valStr, valTextX, rowY + (ROW_HEIGHT - 8) / 2,
                CogwheelSettings.c(CogwheelSettings.Key.TEXT), false);
        renderStepper(g, font, plusX, rowY, "+", mouseX, mouseY);
        renderResetButton(g, font, resetX, rowY, mouseX, mouseY);
    }

    private void renderBoolRow(GuiGraphics g, Font font, CogwheelSettings.BoolKey key, int rowY,
                                int mouseX, int mouseY) {
        boolean value = CogwheelSettings.get().bool(key);
        int cbX = x + PADDING;
        int cbY = rowY + (ROW_HEIGHT - CHECKBOX_SIZE) / 2;
        boolean hoverCb = mouseX >= cbX && mouseX < cbX + CHECKBOX_SIZE
                && mouseY >= cbY && mouseY < cbY + CHECKBOX_SIZE;
        // Box fill — gold when checked, neutral when unchecked.
        int boxColor = value ? 0xFFE8B86E : 0xFF2A3148;
        g.fill(cbX, cbY, cbX + CHECKBOX_SIZE, cbY + CHECKBOX_SIZE, boxColor);
        outline(g, cbX, cbY, CHECKBOX_SIZE, CHECKBOX_SIZE,
                hoverCb ? 0xFFFFCC55 : BORDER);
        if (value) {
            // Tick mark.
            int cc = 0xFF000000;
            g.fill(cbX + 3, cbY + 5, cbX + 4, cbY + 6, cc);
            g.fill(cbX + 4, cbY + 6, cbX + 5, cbY + 7, cc);
            g.fill(cbX + 5, cbY + 5, cbX + 6, cbY + 6, cc);
            g.fill(cbX + 6, cbY + 4, cbX + 7, cbY + 5, cc);
            g.fill(cbX + 7, cbY + 3, cbX + 8, cbY + 4, cc);
        }

        int labelX = cbX + CHECKBOX_SIZE + 5;
        int labelMaxW = (x + width - PADDING - RESET_SIZE - 3) - labelX - 2;
        int textColor = CogwheelSettings.get().isOverridden(key)
                ? CogwheelSettings.c(CogwheelSettings.Key.TEXT)
                : TEXT_DIM;
        g.drawString(font, truncate(font, key.label, labelMaxW),
                labelX, rowY + (ROW_HEIGHT - 8) / 2, textColor, false);

        renderResetButton(g, font, x + width - PADDING - RESET_SIZE, rowY, mouseX, mouseY);
    }

    private void renderStepper(GuiGraphics g, Font font, int sx, int rowY, String sym,
                                int mouseX, int mouseY) {
        int sy = rowY + (ROW_HEIGHT - STEPPER_SIZE) / 2;
        boolean hover = mouseX >= sx && mouseX < sx + STEPPER_SIZE
                && mouseY >= sy && mouseY < sy + STEPPER_SIZE;
        g.fill(sx, sy, sx + STEPPER_SIZE, sy + STEPPER_SIZE, hover ? HOVER_BG : 0xFF2A3148);
        outline(g, sx, sy, STEPPER_SIZE, STEPPER_SIZE, BORDER);
        int tw = font.width(sym);
        g.drawString(font, sym, sx + (STEPPER_SIZE - tw) / 2, sy + 2,
                CogwheelSettings.c(CogwheelSettings.Key.TEXT), false);
    }

    private void renderResetButton(GuiGraphics g, Font font, int rX, int rowY,
                                    int mouseX, int mouseY) {
        int rY = rowY + (ROW_HEIGHT - RESET_SIZE) / 2;
        boolean hover = mouseX >= rX && mouseX < rX + RESET_SIZE
                && mouseY >= rY && mouseY < rY + RESET_SIZE;
        if (hover) g.fill(rX, rY, rX + RESET_SIZE, rY + RESET_SIZE, HOVER_BG);
        g.drawString(font, "↻", rX + 2, rY + 2, TEXT_DIM, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        // Tab strip.
        if (mouseY >= y && mouseY < y + TAB_HEIGHT) {
            CogwheelSettings.Category[] tabs = CogwheelSettings.Category.values();
            int tabW = width / tabs.length;
            int idx = (int) ((mouseX - x) / tabW);
            if (idx >= 0 && idx < tabs.length) {
                activeTab = tabs[idx];
                return true;
            }
        }

        // Footer "Reset all"
        int footerY = y + height - FOOTER_HEIGHT;
        if (mouseY >= footerY + 2 && mouseY < footerY + FOOTER_HEIGHT - 2
                && mouseX >= x + PADDING && mouseX <= x + width - PADDING) {
            CogwheelSettings.get().resetAll();
            return true;
        }

        int listY = listY();
        for (RowLayout row : layout()) {
            int rowY = listY + row.yWithinList - (int) scroll();
            switch (row.kind) {
                case HEADER -> {}
                case COLOR -> {
                    if (handleColorRowClick((CogwheelSettings.Key) row.key, rowY, mouseX, mouseY)) return true;
                }
                case NUM -> {
                    if (handleNumRowClick((CogwheelSettings.NumKey) row.key, rowY, mouseX, mouseY)) return true;
                }
                case BOOL -> {
                    if (handleBoolRowClick((CogwheelSettings.BoolKey) row.key, rowY, mouseX, mouseY)) return true;
                }
            }
        }
        return false;
    }

    private boolean handleColorRowClick(CogwheelSettings.Key key, int rowY, double mx, double my) {
        int swX = x + PADDING;
        int swY = rowY + (ROW_HEIGHT - SWATCH_SIZE) / 2;
        if (mx >= swX && mx < swX + SWATCH_SIZE && my >= swY && my < swY + SWATCH_SIZE) {
            int current = CogwheelSettings.get().color(key);
            Minecraft.getInstance().setScreen(new ColorPickerModal(
                    Minecraft.getInstance().screen, current,
                    picked -> CogwheelSettings.get().set(key, picked)));
            return true;
        }
        int rX = x + width - PADDING - RESET_SIZE;
        int rY = rowY + (ROW_HEIGHT - RESET_SIZE) / 2;
        if (mx >= rX && mx < rX + RESET_SIZE && my >= rY && my < rY + RESET_SIZE) {
            CogwheelSettings.get().reset(key);
            return true;
        }
        return false;
    }

    private boolean handleNumRowClick(CogwheelSettings.NumKey key, int rowY, double mx, double my) {
        float value = CogwheelSettings.get().num(key);
        String valStr = String.format("%.2f", value);
        int valW = Minecraft.getInstance().font.width(valStr);
        int resetX = x + width - PADDING - RESET_SIZE;
        int plusX = resetX - STEPPER_SIZE - 2;
        int valTextX = plusX - valW - 4;
        int minusX = valTextX - STEPPER_SIZE - 4;
        int sy = rowY + (ROW_HEIGHT - STEPPER_SIZE) / 2;
        if (my >= sy && my < sy + STEPPER_SIZE) {
            if (mx >= minusX && mx < minusX + STEPPER_SIZE) {
                CogwheelSettings.get().setNum(key, value - key.step);
                return true;
            }
            if (mx >= plusX && mx < plusX + STEPPER_SIZE) {
                CogwheelSettings.get().setNum(key, value + key.step);
                return true;
            }
        }
        int rY = rowY + (ROW_HEIGHT - RESET_SIZE) / 2;
        if (mx >= resetX && mx < resetX + RESET_SIZE && my >= rY && my < rY + RESET_SIZE) {
            CogwheelSettings.get().resetNum(key);
            return true;
        }
        return false;
    }

    private boolean handleBoolRowClick(CogwheelSettings.BoolKey key, int rowY, double mx, double my) {
        // Whole-row hit toggles — easier target than a 12-px checkbox alone.
        int cbX = x + PADDING;
        int cbY = rowY + (ROW_HEIGHT - CHECKBOX_SIZE) / 2;
        int resetX = x + width - PADDING - RESET_SIZE;
        int rY = rowY + (ROW_HEIGHT - RESET_SIZE) / 2;
        if (mx >= resetX && mx < resetX + RESET_SIZE && my >= rY && my < rY + RESET_SIZE) {
            CogwheelSettings.get().resetBool(key);
            return true;
        }
        // Anywhere else on the row toggles the bool.
        if (my >= rowY && my < rowY + ROW_HEIGHT && mx >= x && mx <= x + width - PADDING - RESET_SIZE) {
            boolean current = CogwheelSettings.get().bool(key);
            CogwheelSettings.get().setBool(key, !current);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        int contentH = contentHeight();
        int listH = listH();
        if (contentH <= listH) return true;
        double s = scroll();
        s -= scrollY * ROW_HEIGHT;
        s = Math.max(0, Math.min(contentH - listH, s));
        setScroll(s);
        return true;
    }

    private static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    private static String truncate(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        while (text.length() > 0 && font.width(text + "…") > maxWidth) {
            text = text.substring(0, text.length() - 1);
        }
        return text + "…";
    }
}
