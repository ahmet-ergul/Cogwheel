package dev.kima.cogwheel.client.ui;

import dev.kima.cogwheel.settings.CogwheelSettings;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Sidepanel text rendering helpers. Reads the {@code SIDEPANEL_SCALE} setting and applies it via
 * {@link com.mojang.blaze3d.vertex.PoseStack#scale(float, float, float) pose.scale}, so the layout
 * math in the calling panel can stay in "unscaled" pixels — the panel decides where the text
 * <em>starts</em>, and this helper renders it at the chosen scale.
 *
 * <p>Layout helpers like {@link #scaledWidth(Font, String)} let callers compute how wide a
 * scale-1.0 text will actually be once the scale is applied — useful for truncation and right-edge
 * alignment math.
 */
public final class ScaledUi {
    private ScaledUi() {}

    public static float scale() {
        return CogwheelSettings.get().num(CogwheelSettings.NumKey.SIDEPANEL_SCALE);
    }

    /** Render text at {@link #scale()} anchored at (x, y). Same semantics as
     *  {@link GuiGraphics#drawString(Font, String, int, int, int, boolean)}. */
    public static void drawString(GuiGraphics g, Font font, String text, int x, int y, int color) {
        drawString(g, font, text, x, y, color, false);
    }

    public static void drawString(GuiGraphics g, Font font, String text, int x, int y, int color, boolean shadow) {
        float s = scale();
        if (Math.abs(s - 1.0f) < 0.001f) {
            g.drawString(font, text, x, y, color, shadow);
            return;
        }
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(s, s, 1f);
        g.drawString(font, text, 0, 0, color, shadow);
        pose.popPose();
    }

    /** Width of {@code text} once rendered at the current sidepanel scale. */
    public static int scaledWidth(Font font, String text) {
        return Math.round(font.width(text) * scale());
    }

    /** Truncate {@code text} (appending '…') so its scaled width fits within {@code maxWidthPx}. */
    public static String truncate(Font font, String text, int maxWidthPx) {
        if (scaledWidth(font, text) <= maxWidthPx) return text;
        // Work in the un-scaled width space so it's one width() call per shrink iteration.
        int unscaledBudget = Math.round(maxWidthPx / Math.max(0.001f, scale()));
        while (text.length() > 0 && font.width(text + "…") > unscaledBudget) {
            text = text.substring(0, text.length() - 1);
        }
        return text + "…";
    }

    /** Vertical line height at the current scale (font's native 9 px × scale, rounded). */
    public static int lineHeight() {
        return Math.max(1, Math.round(9 * scale()));
    }
}
