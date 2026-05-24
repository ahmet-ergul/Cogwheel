package dev.kima.cogwheel.client.ui.modal;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.IntConsumer;

/**
 * HSV color picker with separate alpha slider.
 *
 * <ul>
 *   <li>Saturation × value square (2D) — click or drag to pick.</li>
 *   <li>Hue strip on the right — 6-stop rainbow gradient.</li>
 *   <li>Alpha strip below the SV square — checkerboard backing + current-color over it.</li>
 *   <li>Preview swatch + hex readout + OK / Cancel.</li>
 * </ul>
 *
 * <p>Returns the chosen ARGB int via {@code onPick} when OK is pressed; ESC / Cancel returns
 * to the parent screen unchanged.
 */
public final class ColorPickerModal extends Screen {
    private static final int PANEL_W = 220;
    private static final int PANEL_H = 200;
    private static final int PADDING = 8;
    private static final int SV_SIZE = 130;
    private static final int HUE_W = 12;
    private static final int ALPHA_H = 12;
    private static final int PREVIEW_SIZE = 22;

    private static final int BG = 0xFF1F2233;
    private static final int BORDER = 0xFF54607A;
    private static final int TEXT = 0xFFEAEAEA;
    private static final int TEXT_DIM = 0xFFA8A8B8;
    private static final int CB_LIGHT = 0xFF707070; // alpha checkerboard
    private static final int CB_DARK  = 0xFF404040;

    private final Screen parent;
    private final IntConsumer onPick;

    private float hue;        // 0..360
    private float saturation; // 0..1
    private float value;      // 0..1
    private int alpha;        // 0..255

    private int panelX, panelY;
    private int svX, svY;
    private int hueX, hueY;
    private int alphaX, alphaY;

    private enum Drag { NONE, SV, HUE, ALPHA }
    private Drag activeDrag = Drag.NONE;

    public ColorPickerModal(Screen parent, int initialArgb, IntConsumer onPick) {
        super(Component.literal("Pick a color"));
        this.parent = parent;
        this.onPick = onPick;
        argbToHsva(initialArgb);
    }

    private void argbToHsva(int argb) {
        alpha = (argb >>> 24) & 0xFF;
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float d = max - min;
        if (d == 0f) hue = 0f;
        else if (max == r) hue = ((g - b) / d) * 60f;
        else if (max == g) hue = ((b - r) / d + 2f) * 60f;
        else hue = ((r - g) / d + 4f) * 60f;
        if (hue < 0) hue += 360f;
        saturation = max == 0 ? 0 : d / max;
        value = max;
    }

    @Override
    protected void init() {
        panelX = (this.width - PANEL_W) / 2;
        panelY = (this.height - PANEL_H) / 2;
        svX = panelX + PADDING;
        svY = panelY + PADDING + 12; // leave room for title
        hueX = svX + SV_SIZE + 8;
        hueY = svY;
        alphaX = svX;
        alphaY = svY + SV_SIZE + 6;

        int buttonY = panelY + PANEL_H - PADDING - 20;
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> commit())
                .bounds(panelX + PANEL_W - PADDING - 80, buttonY, 80, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> cancel())
                .bounds(panelX + PADDING, buttonY, 80, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Dim backdrop.
        g.fill(0, 0, this.width, this.height, 0xEE000000);

        // Panel.
        g.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, BG);
        outline(g, panelX, panelY, PANEL_W, PANEL_H, BORDER);

        g.drawString(this.font, "Pick a color", panelX + PADDING, panelY + PADDING - 1, TEXT, false);

        renderSvSquare(g);
        renderHueStrip(g);
        renderAlphaStrip(g);
        renderPreview(g);

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderSvSquare(GuiGraphics g) {
        int pureHue = hsvToArgb(hue, 1f, 1f) | 0xFF000000;
        // Horizontal gradient via column-by-column solid fills (fillGradient is vertical-only).
        for (int col = 0; col < SV_SIZE; col++) {
            float t = (float) col / (SV_SIZE - 1);
            int color = lerpArgb(0xFFFFFFFF, pureHue, t);
            g.fill(svX + col, svY, svX + col + 1, svY + SV_SIZE, color);
        }
        // Vertical overlay: transparent → opaque black.
        g.fillGradient(svX, svY, svX + SV_SIZE, svY + SV_SIZE, 0x00000000, 0xFF000000);

        outline(g, svX - 1, svY - 1, SV_SIZE + 2, SV_SIZE + 2, BORDER);

        // Crosshair at (saturation, 1-value).
        int cx = svX + Math.round(saturation * (SV_SIZE - 1));
        int cy = svY + Math.round((1f - value) * (SV_SIZE - 1));
        int crossColor = value > 0.5f ? 0xFF000000 : 0xFFFFFFFF;
        g.fill(cx - 4, cy, cx - 1, cy + 1, crossColor);
        g.fill(cx + 2, cy, cx + 5, cy + 1, crossColor);
        g.fill(cx, cy - 4, cx + 1, cy - 1, crossColor);
        g.fill(cx, cy + 2, cx + 1, cy + 5, crossColor);
    }

    private void renderHueStrip(GuiGraphics g) {
        // 6 stops × 1/6 of the height each.
        int[] stops = {
                0xFFFF0000, 0xFFFFFF00, 0xFF00FF00,
                0xFF00FFFF, 0xFF0000FF, 0xFFFF00FF, 0xFFFF0000
        };
        int segH = SV_SIZE / 6;
        for (int i = 0; i < 6; i++) {
            int y1 = hueY + i * segH;
            int y2 = (i == 5) ? hueY + SV_SIZE : hueY + (i + 1) * segH;
            g.fillGradient(hueX, y1, hueX + HUE_W, y2, stops[i], stops[i + 1]);
        }
        outline(g, hueX - 1, hueY - 1, HUE_W + 2, SV_SIZE + 2, BORDER);

        int marker = hueY + Math.round((hue / 360f) * (SV_SIZE - 1));
        g.fill(hueX - 2, marker, hueX + HUE_W + 2, marker + 1, 0xFFFFFFFF);
    }

    private void renderAlphaStrip(GuiGraphics g) {
        // Checkerboard background so partial alpha is visible.
        int cell = 4;
        for (int x = 0; x < SV_SIZE; x += cell) {
            int wCell = Math.min(cell, SV_SIZE - x);
            for (int row = 0; row < ALPHA_H; row += cell) {
                int hCell = Math.min(cell, ALPHA_H - row);
                boolean dark = ((x / cell) + (row / cell)) % 2 == 0;
                g.fill(alphaX + x, alphaY + row, alphaX + x + wCell, alphaY + row + hCell,
                        dark ? CB_DARK : CB_LIGHT);
            }
        }
        // Current color: horizontal transparent → opaque (alpha sweep)
        int colorAtFullAlpha = hsvToArgb(hue, saturation, value) | 0xFF000000;
        int colorAtZeroAlpha = colorAtFullAlpha & 0x00FFFFFF;
        // fillGradient is vertical, so column-by-column manual fills again.
        for (int col = 0; col < SV_SIZE; col++) {
            float t = (float) col / (SV_SIZE - 1);
            int color = lerpArgb(colorAtZeroAlpha, colorAtFullAlpha, t);
            g.fill(alphaX + col, alphaY, alphaX + col + 1, alphaY + ALPHA_H, color);
        }
        outline(g, alphaX - 1, alphaY - 1, SV_SIZE + 2, ALPHA_H + 2, BORDER);

        int marker = alphaX + Math.round((alpha / 255f) * (SV_SIZE - 1));
        g.fill(marker, alphaY - 2, marker + 1, alphaY + ALPHA_H + 2, 0xFFFFFFFF);
    }

    private void renderPreview(GuiGraphics g) {
        int px = hueX;
        int py = alphaY;
        // Checkerboard backing under the preview too.
        for (int dx = 0; dx < HUE_W; dx += 4) {
            for (int dy = 0; dy < ALPHA_H; dy += 4) {
                boolean dark = ((dx / 4) + (dy / 4)) % 2 == 0;
                g.fill(px + dx, py + dy,
                        Math.min(px + dx + 4, px + HUE_W),
                        Math.min(py + dy + 4, py + ALPHA_H),
                        dark ? CB_DARK : CB_LIGHT);
            }
        }
        int current = currentArgb();
        g.fill(px, py, px + HUE_W, py + ALPHA_H, current);
        outline(g, px - 1, py - 1, HUE_W + 2, ALPHA_H + 2, BORDER);

        // Hex readout next to the preview.
        String hex = String.format("#%08X", current);
        g.drawString(this.font, hex, panelX + PADDING, alphaY + ALPHA_H + 6, TEXT_DIM, false);
    }

    private int currentArgb() {
        int rgb = hsvToArgb(hue, saturation, value) & 0x00FFFFFF;
        return (alpha << 24) | rgb;
    }

    private static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    private static int hsvToArgb(float h, float s, float v) {
        float c = v * s;
        float x = c * (1f - Math.abs(((h / 60f) % 2f) - 1f));
        float m = v - c;
        float r, g, b;
        if (h < 60)      { r = c; g = x; b = 0; }
        else if (h < 120){ r = x; g = c; b = 0; }
        else if (h < 180){ r = 0; g = c; b = x; }
        else if (h < 240){ r = 0; g = x; b = c; }
        else if (h < 300){ r = x; g = 0; b = c; }
        else             { r = c; g = 0; b = x; }
        int ri = Math.round((r + m) * 255f);
        int gi = Math.round((g + m) * 255f);
        int bi = Math.round((b + m) * 255f);
        return 0xFF000000 | (ri << 16) | (gi << 8) | bi;
    }

    private static int lerpArgb(int a, int b, float t) {
        int aA = (a >>> 24) & 0xFF, aR = (a >> 16) & 0xFF, aG = (a >> 8) & 0xFF, aB = a & 0xFF;
        int bA = (b >>> 24) & 0xFF, bR = (b >> 16) & 0xFF, bG = (b >> 8) & 0xFF, bB = b & 0xFF;
        int rA = Math.round(aA + (bA - aA) * t);
        int rR = Math.round(aR + (bR - aR) * t);
        int rG = Math.round(aG + (bG - aG) * t);
        int rB = Math.round(aB + (bB - aB) * t);
        return (rA << 24) | (rR << 16) | (rG << 8) | rB;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (inside(mouseX, mouseY, svX, svY, SV_SIZE, SV_SIZE)) {
                activeDrag = Drag.SV;
                applySv(mouseX, mouseY);
                return true;
            }
            if (inside(mouseX, mouseY, hueX, hueY, HUE_W, SV_SIZE)) {
                activeDrag = Drag.HUE;
                applyHue(mouseY);
                return true;
            }
            if (inside(mouseX, mouseY, alphaX, alphaY, SV_SIZE, ALPHA_H)) {
                activeDrag = Drag.ALPHA;
                applyAlpha(mouseX);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        switch (activeDrag) {
            case SV    -> { applySv(mouseX, mouseY); return true; }
            case HUE   -> { applyHue(mouseY); return true; }
            case ALPHA -> { applyAlpha(mouseX); return true; }
            default    -> {}
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        activeDrag = Drag.NONE;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void applySv(double mouseX, double mouseY) {
        saturation = clamp01((mouseX - svX) / (double) (SV_SIZE - 1));
        value = 1f - clamp01((mouseY - svY) / (double) (SV_SIZE - 1));
    }

    private void applyHue(double mouseY) {
        hue = clamp01((mouseY - hueY) / (double) (SV_SIZE - 1)) * 360f;
    }

    private void applyAlpha(double mouseX) {
        alpha = Math.round(clamp01((mouseX - alphaX) / (double) (SV_SIZE - 1)) * 255f);
    }

    private static float clamp01(double v) {
        return (float) Math.max(0.0, Math.min(1.0, v));
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER) { commit(); return true; }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) { cancel(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void commit() {
        int color = currentArgb();
        Minecraft.getInstance().setScreen(parent);
        onPick.accept(color);
    }

    private void cancel() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() { Minecraft.getInstance().setScreen(parent); }

    // Suppress vanilla's panorama / blur backdrop — we draw our own dim layer in render(). Without
    // these overrides, the picker comes up over a softly-blurred world that bleeds through our own
    // backdrop and reads as "selector is blurred".
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // intentionally empty — render() paints the dim layer
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {
        // intentionally empty
    }

    @Override
    protected void renderMenuBackground(GuiGraphics g) {
        // intentionally empty
    }
}
