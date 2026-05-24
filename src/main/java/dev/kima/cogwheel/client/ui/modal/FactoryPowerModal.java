package dev.kima.cogwheel.client.ui.modal;

import dev.kima.cogwheel.model.Factory;
import dev.kima.cogwheel.model.PowerSource;
import dev.kima.cogwheel.model.PowerSourceStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Per-factory power/speed settings modal. Lets the user pick which {@link PowerSource} a factory
 * draws from (or "None" to leave it unassigned) and set the factory's operating RPM.
 *
 * <p>Returns the chosen {@code (powerSourceId, rpm)} pair through {@code onApply} when OK is
 * pressed. Cancel / ESC restores the previous values.
 */
public final class FactoryPowerModal extends Screen {
    private static final int PANEL_W = 240;
    private static final int PANEL_H = 160;
    private static final int PADDING = 10;

    private static final int BG = 0xFF1F2233;
    private static final int BORDER = 0xFF54607A;
    private static final int TEXT = 0xFFEAEAEA;
    private static final int TEXT_DIM = 0xFFA8A8B8;
    private static final int SLIDER_TRACK = 0xFF2A3148;
    private static final int SLIDER_FILL = 0xFFE8B86E;
    private static final int BUTTON_BG = 0xFF2A3148;
    private static final int BUTTON_HOVER = 0xFF3A4868;

    private final Screen parent;
    private final Factory factory;
    private final BiConsumer<Optional<UUID>, Integer> onApply;

    private Optional<UUID> selectedSourceId;
    private int rpm;

    private int panelX, panelY;
    private boolean draggingSlider;

    public FactoryPowerModal(Screen parent, Factory factory,
                              BiConsumer<Optional<UUID>, Integer> onApply) {
        super(Component.literal("Factory power"));
        this.parent = parent;
        this.factory = factory;
        this.onApply = onApply;
        this.selectedSourceId = factory.powerSourceId();
        this.rpm = factory.rpm();
    }

    @Override
    protected void init() {
        panelX = (this.width - PANEL_W) / 2;
        panelY = (this.height - PANEL_H) / 2;

        int btnY = panelY + PANEL_H - PADDING - 20;
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> commit())
                .bounds(panelX + PANEL_W - PADDING - 80, btnY, 80, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> cancel())
                .bounds(panelX + PADDING, btnY, 80, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0xEE000000);
        g.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, BG);
        outline(g, panelX, panelY, PANEL_W, PANEL_H, BORDER);

        int y = panelY + PADDING;
        g.drawString(this.font, "Factory: " + factory.name(),
                panelX + PADDING, y, TEXT, false);
        y += 14;

        // Power source cycle button.
        g.drawString(this.font, "Power source", panelX + PADDING, y, TEXT_DIM, false);
        y += 12;
        String sourceLabel = selectedSourceId
                .map(id -> {
                    PowerSource ps = PowerSourceStore.get().findById(id);
                    return ps != null ? ps.name() : "(missing)";
                })
                .orElse("None");
        boolean btnHover = mouseX >= panelX + PADDING && mouseX <= panelX + PANEL_W - PADDING
                && mouseY >= y && mouseY < y + 18;
        g.fill(panelX + PADDING, y, panelX + PANEL_W - PADDING, y + 18,
                btnHover ? BUTTON_HOVER : BUTTON_BG);
        int tw = this.font.width(sourceLabel);
        g.drawString(this.font, sourceLabel,
                panelX + (PANEL_W - tw) / 2, y + 5, TEXT, false);
        y += 22;

        // RPM slider.
        g.drawString(this.font, "RPM: " + rpm, panelX + PADDING, y, TEXT, false);
        y += 12;
        int sliderW = PANEL_W - PADDING * 2;
        g.fill(panelX + PADDING, y, panelX + PADDING + sliderW, y + 8, SLIDER_TRACK);
        double frac = (double) (rpm - Factory.MIN_RPM) / (Factory.MAX_RPM - Factory.MIN_RPM);
        int fillW = (int) Math.round(sliderW * frac);
        g.fill(panelX + PADDING, y, panelX + PADDING + fillW, y + 8, SLIDER_FILL);
        y += 12;
        g.drawString(this.font, "Range: " + Factory.MIN_RPM + "-" + Factory.MAX_RPM,
                panelX + PADDING, y, TEXT_DIM, false);

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        int y = panelY + PADDING + 14 + 12;
        // Source cycle button at y..y+18.
        if (mouseY >= y && mouseY < y + 18
                && mouseX >= panelX + PADDING && mouseX <= panelX + PANEL_W - PADDING) {
            cycleSource();
            return true;
        }
        // Slider hit?
        int sliderY = y + 22 + 12;
        int sliderW = PANEL_W - PADDING * 2;
        if (mouseY >= sliderY && mouseY < sliderY + 8
                && mouseX >= panelX + PADDING && mouseX <= panelX + PADDING + sliderW) {
            draggingSlider = true;
            applySliderDrag(mouseX);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (draggingSlider) {
            applySliderDrag(mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingSlider = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void applySliderDrag(double mouseX) {
        int sliderW = PANEL_W - PADDING * 2;
        double frac = (mouseX - (panelX + PADDING)) / sliderW;
        frac = Math.max(0, Math.min(1, frac));
        rpm = (int) Math.round(Factory.MIN_RPM + frac * (Factory.MAX_RPM - Factory.MIN_RPM));
    }

    /** Cycle through: None → source 0 → source 1 → ... → None. */
    private void cycleSource() {
        var all = PowerSourceStore.get().all();
        if (all.isEmpty()) {
            selectedSourceId = Optional.empty();
            return;
        }
        if (selectedSourceId.isEmpty()) {
            selectedSourceId = Optional.of(all.get(0).id());
            return;
        }
        UUID current = selectedSourceId.get();
        int idx = -1;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id().equals(current)) { idx = i; break; }
        }
        if (idx < 0 || idx == all.size() - 1) {
            selectedSourceId = Optional.empty();
        } else {
            selectedSourceId = Optional.of(all.get(idx + 1).id());
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER) { commit(); return true; }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) { cancel(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void commit() {
        onApply.accept(selectedSourceId, rpm);
        Minecraft.getInstance().setScreen(parent);
    }

    private void cancel() {
        Minecraft.getInstance().setScreen(parent);
    }

    private static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { Minecraft.getInstance().setScreen(parent); }
    @Override public void renderBackground(GuiGraphics g, int mx, int my, float pt) {}
    @Override protected void renderBlurredBackground(float pt) {}
    @Override protected void renderMenuBackground(GuiGraphics g) {}
}
