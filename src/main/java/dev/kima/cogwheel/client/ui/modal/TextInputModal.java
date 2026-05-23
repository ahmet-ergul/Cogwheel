package dev.kima.cogwheel.client.ui.modal;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/**
 * Tiny modal: prompt + text input + OK / Cancel buttons. Used by the Factories page for creating
 * and renaming factories. Submits the entered string via {@code onSubmit}; Esc / Cancel returns to
 * the parent screen without firing.
 */
public final class TextInputModal extends Screen {
    private static final int PANEL_WIDTH = 240;
    private static final int PANEL_HEIGHT = 92;
    private static final int PADDING = 8;
    private static final int BG_COLOR = 0xFF1F2233;
    private static final int BORDER = 0xFF54607A;
    private static final int TEXT = 0xFFEAEAEA;

    private final Screen parent;
    private final Component prompt;
    private final String initialValue;
    private final Consumer<String> onSubmit;

    private EditBox input;
    private int panelX;
    private int panelY;

    public TextInputModal(Screen parent, Component prompt, String initialValue, Consumer<String> onSubmit) {
        super(prompt);
        this.parent = parent;
        this.prompt = prompt;
        this.initialValue = initialValue == null ? "" : initialValue;
        this.onSubmit = onSubmit;
    }

    @Override
    protected void init() {
        panelX = (this.width - PANEL_WIDTH) / 2;
        panelY = (this.height - PANEL_HEIGHT) / 2;
        Font font = Minecraft.getInstance().font;

        input = new EditBox(font, panelX + PADDING, panelY + PADDING + 14,
                PANEL_WIDTH - PADDING * 2, 18, prompt);
        input.setMaxLength(64);
        input.setValue(initialValue);
        input.setFocused(true);
        this.setInitialFocus(input);
        addRenderableWidget(input);

        // OK + Cancel buttons.
        int buttonY = panelY + PANEL_HEIGHT - PADDING - 20;
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> submit())
                .bounds(panelX + PANEL_WIDTH - PADDING - 80, buttonY, 80, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> cancel())
                .bounds(panelX + PADDING, buttonY, 80, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xEE000000);

        // Panel.
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, BG_COLOR);
        graphics.fill(panelX - 1, panelY - 1, panelX + PANEL_WIDTH + 1, panelY, BORDER);
        graphics.fill(panelX - 1, panelY + PANEL_HEIGHT, panelX + PANEL_WIDTH + 1, panelY + PANEL_HEIGHT + 1, BORDER);
        graphics.fill(panelX - 1, panelY, panelX, panelY + PANEL_HEIGHT, BORDER);
        graphics.fill(panelX + PANEL_WIDTH, panelY, panelX + PANEL_WIDTH + 1, panelY + PANEL_HEIGHT, BORDER);

        // Prompt label.
        graphics.drawString(this.font, prompt, panelX + PADDING, panelY + PADDING, TEXT, false);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER) {
            submit();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            cancel();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void submit() {
        String value = input == null ? initialValue : input.getValue();
        Minecraft.getInstance().setScreen(parent);
        onSubmit.accept(value.trim());
    }

    private void cancel() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
