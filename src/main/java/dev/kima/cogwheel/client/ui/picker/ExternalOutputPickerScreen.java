package dev.kima.cogwheel.client.ui.picker;

import dev.kima.cogwheel.model.ExternalRef;
import dev.kima.cogwheel.model.Factory;
import dev.kima.cogwheel.model.FactoryStore;
import dev.kima.cogwheel.model.Node;
import dev.kima.cogwheel.model.OutputNode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Modal picker listing every {@link OutputNode} in every {@link Factory}. Used by
 * {@link dev.kima.cogwheel.client.ui.panel.PropertiesPanel} when the user binds a
 * SourceNode (kind EXTERNAL_FACTORY) to a specific external output.
 *
 * <p>Each row shows: {@code factoryName · exportName · item}. Click to select.
 */
public final class ExternalOutputPickerScreen extends Screen {
    private static final int PANEL_WIDTH = 380;
    private static final int ROW_HEIGHT = 22;
    private static final int HEADER_HEIGHT = 26;
    private static final int PADDING = 6;

    private static final int BG = 0xF01F2233;
    private static final int BORDER = 0xFF54607A;
    private static final int ROW_HOVER = 0xFF2A3148;
    private static final int TEXT = 0xFFEAEAEA;
    private static final int TEXT_DIM = 0xFFA8A8B8;

    private final Screen parent;
    private final Consumer<ExternalRef> onSelect;
    private final List<Row> rows = new ArrayList<>();

    private int panelX;
    private int panelY;
    private int panelH;
    private int scroll;

    /** A single selectable entry — a (factory, output node) pair. */
    private record Row(Factory factory, OutputNode output) {}

    public ExternalOutputPickerScreen(Screen parent, Consumer<ExternalRef> onSelect) {
        super(Component.translatable("screen.cogwheel.external_picker.title"));
        this.parent = parent;
        this.onSelect = onSelect;
        for (Factory f : FactoryStore.get().all()) {
            for (Node n : f.design().nodes()) {
                if (n instanceof OutputNode out) {
                    rows.add(new Row(f, out));
                }
            }
        }
    }

    @Override
    protected void init() {
        int desiredRows = Math.min(Math.max(rows.size(), 1), 10);
        panelH = HEADER_HEIGHT + desiredRows * ROW_HEIGHT + PADDING * 2;
        int maxH = this.height - 40;
        panelH = Math.min(maxH, panelH);
        panelX = (this.width - PANEL_WIDTH) / 2;
        panelY = (this.height - panelH) / 2;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xFF101522);

        Font font = Minecraft.getInstance().font;
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + panelH, BG);
        graphics.fill(panelX - 1, panelY - 1, panelX + PANEL_WIDTH + 1, panelY, BORDER);
        graphics.fill(panelX - 1, panelY + panelH, panelX + PANEL_WIDTH + 1, panelY + panelH + 1, BORDER);
        graphics.fill(panelX - 1, panelY, panelX, panelY + panelH, BORDER);
        graphics.fill(panelX + PANEL_WIDTH, panelY, panelX + PANEL_WIDTH + 1, panelY + panelH, BORDER);

        graphics.drawString(font, this.title, panelX + PADDING, panelY + PADDING + 2, TEXT, false);
        String count = rows.size() + " outputs";
        graphics.drawString(font, Component.literal(count),
                panelX + PANEL_WIDTH - PADDING - font.width(count),
                panelY + PADDING + 2, TEXT_DIM, false);
        graphics.fill(panelX + 1, panelY + HEADER_HEIGHT - 1, panelX + PANEL_WIDTH - 1, panelY + HEADER_HEIGHT, BORDER);

        int listY = panelY + HEADER_HEIGHT;
        int listH = panelH - HEADER_HEIGHT - PADDING;

        if (rows.isEmpty()) {
            graphics.drawString(font, "No output nodes in any factory.",
                    panelX + PADDING, listY + 10, TEXT_DIM, false);
            graphics.drawString(font, "Add an Output node from the Outputs category first.",
                    panelX + PADDING, listY + 22, TEXT_DIM, false);
            return;
        }

        graphics.enableScissor(panelX, listY, panelX + PANEL_WIDTH, listY + listH);
        int firstVisible = scroll / ROW_HEIGHT;
        int last = Math.min(rows.size(), firstVisible + (listH / ROW_HEIGHT) + 1);
        for (int i = firstVisible; i < last; i++) {
            int rowY = listY + i * ROW_HEIGHT - scroll;
            boolean hover = mouseX >= panelX && mouseX <= panelX + PANEL_WIDTH
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            if (hover) {
                graphics.fill(panelX + 1, rowY, panelX + PANEL_WIDTH - 1, rowY + ROW_HEIGHT, ROW_HOVER);
            }
            Row row = rows.get(i);
            ItemStack icon = row.output.item();
            if (!icon.isEmpty()) {
                graphics.renderItem(icon, panelX + PADDING, rowY + 3);
            }
            String name = row.factory.name() + " · " + row.output.exportName();
            graphics.drawString(font, name, panelX + PADDING + 22, rowY + 7, TEXT, false);
        }
        graphics.disableScissor();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        int listY = panelY + HEADER_HEIGHT;
        int listH = panelH - HEADER_HEIGHT - PADDING;
        if (mouseX >= panelX && mouseX <= panelX + PANEL_WIDTH && mouseY >= listY && mouseY < listY + listH) {
            int relY = (int) (mouseY - listY + scroll);
            int index = relY / ROW_HEIGHT;
            if (index >= 0 && index < rows.size()) {
                Row row = rows.get(index);
                Minecraft.getInstance().setScreen(parent);
                onSelect.accept(new ExternalRef(row.factory.id(), row.output.id()));
                return true;
            }
        }
        if (mouseX < panelX || mouseX > panelX + PANEL_WIDTH || mouseY < panelY || mouseY > panelY + panelH) {
            Minecraft.getInstance().setScreen(parent);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double sx, double sy) {
        int listH = panelH - HEADER_HEIGHT - PADDING;
        int contentH = rows.size() * ROW_HEIGHT;
        if (contentH <= listH) return true;
        scroll -= (int) (sy * ROW_HEIGHT * 2);
        scroll = Math.max(0, Math.min(contentH - listH, scroll));
        return true;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
