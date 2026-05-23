package dev.kima.cogwheel.client.ui.picker;

import dev.kima.cogwheel.recipe.IngredientStack;
import dev.kima.cogwheel.recipe.RecipeEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Consumer;

/**
 * Modal Screen shown when the user clicks an item in the toolbox that has more than one recipe
 * producing it. Lists each recipe with its inputs/outputs/duration. Clicking a row pops the screen
 * and invokes the consumer with the chosen entry.
 *
 * <p>The picker stays on top of the EditorScreen — closing it (Esc or selection) returns to the
 * editor. Pretty bare-bones for v1; Phase 9 can add recipe-mod-source chips, search, and previews.
 */
public final class RecipePickerScreen extends Screen {
    private static final int PANEL_WIDTH = 360;
    private static final int ROW_HEIGHT = 22;
    private static final int HEADER_HEIGHT = 24;
    private static final int PADDING = 6;

    private static final int BG = 0xF01F2233;
    private static final int BORDER = 0xFF54607A;
    private static final int ROW_HOVER = 0xFF2A3148;
    private static final int TEXT = 0xFFEAEAEA;
    private static final int TEXT_DIM = 0xFFA8A8B8;

    private final Screen parent;
    private final List<RecipeEntry> entries;
    private final Consumer<RecipeEntry> onSelect;
    private int scroll;
    private int panelX;
    private int panelY;
    private int panelH;

    public RecipePickerScreen(Screen parent, Component titleSuffix, List<RecipeEntry> entries, Consumer<RecipeEntry> onSelect) {
        super(Component.translatable("screen.cogwheel.recipe_picker", titleSuffix));
        this.parent = parent;
        this.entries = entries;
        this.onSelect = onSelect;
    }

    @Override
    protected void init() {
        int desiredRows = Math.min(entries.size(), 10);
        panelH = HEADER_HEIGHT + desiredRows * ROW_HEIGHT + PADDING * 2;
        panelX = (this.width - PANEL_WIDTH) / 2;
        panelY = (this.height - panelH) / 2;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Solid backdrop — we deliberately do NOT render the parent here. ItemRenderer pushes
        // items to a higher Z than 2D fills, so a translucent dim over a rendered parent always
        // lets icons bleed through. The user knows where they came from; they don't need to see
        // the editor under us.
        graphics.fill(0, 0, this.width, this.height, 0xFF101522);

        Font font = Minecraft.getInstance().font;
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + panelH, BG);
        graphics.fill(panelX - 1, panelY - 1, panelX + PANEL_WIDTH + 1, panelY, BORDER);
        graphics.fill(panelX - 1, panelY + panelH, panelX + PANEL_WIDTH + 1, panelY + panelH + 1, BORDER);
        graphics.fill(panelX - 1, panelY, panelX, panelY + panelH, BORDER);
        graphics.fill(panelX + PANEL_WIDTH, panelY, panelX + PANEL_WIDTH + 1, panelY + panelH, BORDER);

        graphics.drawString(font, this.title, panelX + PADDING, panelY + PADDING, TEXT, false);
        graphics.drawString(font,
                Component.literal(entries.size() + " recipes"),
                panelX + PANEL_WIDTH - PADDING - font.width(entries.size() + " recipes"),
                panelY + PADDING, TEXT_DIM, false);

        int listY = panelY + HEADER_HEIGHT;
        int listH = panelH - HEADER_HEIGHT - PADDING;
        graphics.enableScissor(panelX, listY, panelX + PANEL_WIDTH, listY + listH);
        int firstVisible = scroll / ROW_HEIGHT;
        int last = Math.min(entries.size(), firstVisible + (listH / ROW_HEIGHT) + 1);
        for (int i = firstVisible; i < last; i++) {
            int rowY = listY + i * ROW_HEIGHT - scroll;
            boolean hover = mouseX >= panelX && mouseX <= panelX + PANEL_WIDTH
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            if (hover) {
                graphics.fill(panelX + 1, rowY, panelX + PANEL_WIDTH - 1, rowY + ROW_HEIGHT, ROW_HOVER);
            }
            renderRow(graphics, font, entries.get(i), panelX + PADDING, rowY);
        }
        graphics.disableScissor();
    }

    private void renderRow(GuiGraphics graphics, Font font, RecipeEntry entry, int x, int y) {
        int iconY = y + 3;
        int cursorX = x;

        // Inputs (up to 4).
        int inputsShown = 0;
        for (IngredientStack input : entry.inputs()) {
            if (inputsShown >= 4) break;
            if (input.matchingItems().isEmpty()) continue;
            ItemStack stack = new ItemStack(input.matchingItems().get(0));
            graphics.renderItem(stack, cursorX, iconY);
            cursorX += 18;
            inputsShown++;
        }

        // Arrow.
        graphics.drawString(font, "→", cursorX + 2, y + 7, TEXT, false);
        cursorX += 14;

        // Outputs (up to 3).
        int outputsShown = 0;
        for (ItemStack out : entry.outputs()) {
            if (outputsShown >= 3) break;
            if (out.isEmpty()) continue;
            graphics.renderItem(out, cursorX, iconY);
            cursorX += 18;
            outputsShown++;
        }

        // Type + duration tail.
        String tail = entry.typeId().toString();
        if (entry.processingTimeTicks() > 0) {
            tail += "  " + entry.processingTimeTicks() + "t";
        }
        int tailW = font.width(tail);
        graphics.drawString(font, tail, panelX + PANEL_WIDTH - PADDING - tailW, y + 7, TEXT_DIM, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        int listY = panelY + HEADER_HEIGHT;
        int listH = panelH - HEADER_HEIGHT - PADDING;
        if (mouseX >= panelX && mouseX <= panelX + PANEL_WIDTH && mouseY >= listY && mouseY < listY + listH) {
            int relY = (int) (mouseY - listY + scroll);
            int index = relY / ROW_HEIGHT;
            if (index >= 0 && index < entries.size()) {
                RecipeEntry chosen = entries.get(index);
                Minecraft.getInstance().setScreen(parent);
                onSelect.accept(chosen);
                return true;
            }
        }
        // Click outside panel cancels — return to editor without selection.
        if (mouseX < panelX || mouseX > panelX + PANEL_WIDTH || mouseY < panelY || mouseY > panelY + panelH) {
            Minecraft.getInstance().setScreen(parent);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double sx, double sy) {
        int listH = panelH - HEADER_HEIGHT - PADDING;
        int contentH = entries.size() * ROW_HEIGHT;
        if (contentH <= listH) return true;
        scroll -= (int) (sy * ROW_HEIGHT * 2);
        scroll = Math.max(0, Math.min(contentH - listH, scroll));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
