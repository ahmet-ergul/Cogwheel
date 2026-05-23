package dev.kima.cogwheel.client.ui.picker;

import dev.kima.cogwheel.integration.jei.JeiBridge;
import dev.kima.cogwheel.recipe.IngredientStack;
import dev.kima.cogwheel.recipe.RecipeEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Map;
import java.util.List;
import java.util.function.Consumer;

/**
 * Modal Screen shown when the user clicks an item in the toolbox that has more than one recipe
 * producing it. Each row renders via JEI's own {@code IRecipeCategory.draw} when JEI is present
 * (crafting grids, Create compacting visuals, brewing setups, etc.) and falls back to a
 * type-icon + input/output icons + duration layout when JEI is absent or doesn't recognize the
 * recipe type.
 */
public final class RecipePickerScreen extends Screen {
    private static final int PANEL_WIDTH = 460;
    /** Tall enough to fit JEI's typical category drawables (crafting ~54, smelting ~34, brewing ~60). */
    private static final int ROW_HEIGHT = 56;
    private static final int HEADER_HEIGHT = 26;
    private static final int PADDING = 6;
    private static final int TAIL_WIDTH = 96;

    private static final int BG = 0xF01F2233;
    private static final int BORDER = 0xFF54607A;
    private static final int ROW_HOVER = 0xFF2A3148;
    private static final int ROW_SEPARATOR = 0xFF2A3148;
    private static final int TEXT = 0xFFEAEAEA;
    private static final int TEXT_DIM = 0xFFA8A8B8;
    private static final int SCROLLBAR = 0xFF54607A;

    /** Mapping from recipe type-id to a small icon that gives the user a "what tool makes this" cue
     *  when JEI is absent. Item icons stand in because they're already in the registry. */
    private static final Map<String, ItemStack> TYPE_ICONS = Map.ofEntries(
            Map.entry("minecraft:crafting", new ItemStack(Items.CRAFTING_TABLE)),
            Map.entry("minecraft:crafting_shaped", new ItemStack(Items.CRAFTING_TABLE)),
            Map.entry("minecraft:crafting_shapeless", new ItemStack(Items.CRAFTING_TABLE)),
            Map.entry("minecraft:smelting", new ItemStack(Items.FURNACE)),
            Map.entry("minecraft:blasting", new ItemStack(Items.BLAST_FURNACE)),
            Map.entry("minecraft:smoking", new ItemStack(Items.SMOKER)),
            Map.entry("minecraft:campfire_cooking", new ItemStack(Items.CAMPFIRE)),
            Map.entry("minecraft:stonecutting", new ItemStack(Items.STONECUTTER)),
            Map.entry("minecraft:smithing", new ItemStack(Items.SMITHING_TABLE)),
            Map.entry("minecraft:smithing_transform", new ItemStack(Items.SMITHING_TABLE)),
            Map.entry("minecraft:smithing_trim", new ItemStack(Items.SMITHING_TABLE)),
            Map.entry("minecraft:brewing", new ItemStack(Items.BREWING_STAND)),
            Map.entry("minecraft:anvil", new ItemStack(Items.ANVIL))
    );

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
        // Natural panel height grows with the recipe count, but never exceed the screen — leave 40px
        // top+bottom margin so the modal always has breathing room. Scrolling handles overflow.
        int desired = HEADER_HEIGHT + entries.size() * ROW_HEIGHT + PADDING * 2;
        int maxH = this.height - 40;
        panelH = Math.min(maxH, Math.max(HEADER_HEIGHT + ROW_HEIGHT + PADDING * 2, desired));
        panelX = (this.width - PANEL_WIDTH) / 2;
        panelY = (this.height - panelH) / 2;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Solid backdrop — ItemRenderer pushes items above 2D fills, so dimming a rendered parent
        // always lets icons poke through. Plain dark backdrop instead.
        graphics.fill(0, 0, this.width, this.height, 0xFF101522);

        Font font = Minecraft.getInstance().font;
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + panelH, BG);
        graphics.fill(panelX - 1, panelY - 1, panelX + PANEL_WIDTH + 1, panelY, BORDER);
        graphics.fill(panelX - 1, panelY + panelH, panelX + PANEL_WIDTH + 1, panelY + panelH + 1, BORDER);
        graphics.fill(panelX - 1, panelY, panelX, panelY + panelH, BORDER);
        graphics.fill(panelX + PANEL_WIDTH, panelY, panelX + PANEL_WIDTH + 1, panelY + panelH, BORDER);

        graphics.drawString(font, this.title, panelX + PADDING, panelY + PADDING + 2, TEXT, false);
        String count = entries.size() + " recipes";
        graphics.drawString(font, Component.literal(count),
                panelX + PANEL_WIDTH - PADDING - font.width(count),
                panelY + PADDING + 2, TEXT_DIM, false);
        // Header separator.
        graphics.fill(panelX + 1, panelY + HEADER_HEIGHT - 1, panelX + PANEL_WIDTH - 1, panelY + HEADER_HEIGHT, BORDER);

        int listY = panelY + HEADER_HEIGHT;
        int listH = panelH - HEADER_HEIGHT - PADDING;
        graphics.enableScissor(panelX, listY, panelX + PANEL_WIDTH, listY + listH);
        int firstVisible = scroll / ROW_HEIGHT;
        int last = Math.min(entries.size(), firstVisible + (listH / ROW_HEIGHT) + 2);
        for (int i = firstVisible; i < last; i++) {
            int rowY = listY + i * ROW_HEIGHT - scroll;
            boolean hover = mouseX >= panelX && mouseX <= panelX + PANEL_WIDTH
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            if (hover) {
                graphics.fill(panelX + 1, rowY, panelX + PANEL_WIDTH - 1, rowY + ROW_HEIGHT, ROW_HOVER);
            }
            // Light separator between rows.
            if (i > firstVisible) {
                graphics.fill(panelX + PADDING, rowY, panelX + PANEL_WIDTH - PADDING, rowY + 1, ROW_SEPARATOR);
            }
            renderRow(graphics, font, entries.get(i), panelX, rowY, PANEL_WIDTH, mouseX, mouseY);
        }
        graphics.disableScissor();

        // Scrollbar — stays in the list track.
        int contentH = entries.size() * ROW_HEIGHT;
        if (contentH > listH) {
            int barH = Math.max(24, (int) ((double) listH / contentH * listH));
            double frac = scroll / (double) (contentH - listH);
            int barY = listY + (int) (frac * (listH - barH));
            graphics.fill(panelX + PANEL_WIDTH - 4, barY, panelX + PANEL_WIDTH - 1, barY + barH, SCROLLBAR);
        }
    }

    private void renderRow(GuiGraphics graphics, Font font, RecipeEntry entry,
                           int rowX, int rowY, int rowW, int mouseX, int mouseY) {
        // The "tail" area on the right shows type id + duration in text. JEI / fallback art draws
        // in the area on the LEFT of the tail.
        int artW = rowW - TAIL_WIDTH - PADDING * 2;
        int artX = rowX + PADDING;
        int artH = ROW_HEIGHT - 4;

        boolean drawnByJei = JeiBridge.tryRenderPickerRow(
                graphics, entry.id(), entry.typeId(), artX, rowY + 2, artW, artH, mouseX, mouseY);

        if (!drawnByJei) {
            renderFallbackArt(graphics, font, entry, artX, rowY, artW);
        }

        renderTail(graphics, font, entry, rowX + rowW - TAIL_WIDTH, rowY, TAIL_WIDTH);
    }

    private void renderFallbackArt(GuiGraphics graphics, Font font, RecipeEntry entry,
                                    int x, int y, int w) {
        int iconY = y + (ROW_HEIGHT - 16) / 2;
        int cursor = x;

        // Recipe-type icon at the very left.
        ItemStack typeIcon = TYPE_ICONS.get(entry.typeId().toString());
        if (typeIcon == null) typeIcon = new ItemStack(Items.CRAFTING_TABLE); // generic fallback
        graphics.renderItem(typeIcon, cursor, iconY);
        cursor += 22;

        // Inputs — up to 5 icons with × counts.
        int inputsShown = 0;
        for (IngredientStack input : entry.inputs()) {
            if (inputsShown >= 5) break;
            if (input.matchingItems().isEmpty()) continue;
            ItemStack stack = new ItemStack(input.matchingItems().get(0));
            graphics.renderItem(stack, cursor, iconY);
            if (input.count() > 1) {
                String countStr = "×" + input.count();
                int cw = font.width(countStr);
                graphics.drawString(font, countStr, cursor + 16 - cw, iconY + 8, 0xFFFFFFFF, true);
            }
            cursor += 18;
            inputsShown++;
        }

        // Arrow.
        graphics.drawString(font, "→", cursor + 2, iconY + 4, TEXT, false);
        cursor += 14;

        // Outputs — up to 3 icons with counts.
        int outputsShown = 0;
        for (ItemStack out : entry.outputs()) {
            if (outputsShown >= 3) break;
            if (out.isEmpty()) continue;
            graphics.renderItem(out, cursor, iconY);
            if (out.getCount() > 1) {
                String countStr = "×" + out.getCount();
                int cw = font.width(countStr);
                graphics.drawString(font, countStr, cursor + 16 - cw, iconY + 8, 0xFFFFFFFF, true);
            }
            cursor += 18;
            outputsShown++;
        }
    }

    private void renderTail(GuiGraphics graphics, Font font, RecipeEntry entry, int x, int y, int w) {
        // Type id top-aligned, duration below.
        ResourceLocation type = entry.typeId();
        String typeLabel = type.getPath();
        String typeNs = type.getNamespace();
        int top = y + 6;
        graphics.drawString(font, typeLabel, x + w - PADDING - font.width(typeLabel), top, TEXT, false);
        graphics.drawString(font, typeNs, x + w - PADDING - font.width(typeNs), top + 12, TEXT_DIM, false);
        if (entry.processingTimeTicks() > 0) {
            String dur = entry.processingTimeTicks() + "t";
            graphics.drawString(font, dur, x + w - PADDING - font.width(dur), top + 26, 0xFFE8B86E, false);
        }
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
