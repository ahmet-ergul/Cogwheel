package dev.kima.cogwheel.client.ui.panel;

import dev.kima.cogwheel.client.ui.modal.TextInputModal;
import dev.kima.cogwheel.client.ui.picker.ExternalOutputPickerScreen;
import dev.kima.cogwheel.model.Factory;
import dev.kima.cogwheel.model.FactoryStore;
import dev.kima.cogwheel.model.FilterNode;
import dev.kima.cogwheel.model.LimiterNode;
import dev.kima.cogwheel.model.MergerNode;
import dev.kima.cogwheel.model.Node;
import dev.kima.cogwheel.model.OutputNode;
import dev.kima.cogwheel.model.Port;
import dev.kima.cogwheel.model.RecipeNode;
import dev.kima.cogwheel.model.SinkNode;
import dev.kima.cogwheel.model.SourceNode;
import dev.kima.cogwheel.model.SplitterNode;
import dev.kima.cogwheel.recipe.IngredientStack;
import dev.kima.cogwheel.recipe.RecipeEntry;
import dev.kima.cogwheel.recipe.RecipeIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Right sidebar showing properties for the currently selected node.
 *
 * <ul>
 *   <li>{@link SourceNode}: a Kind cycle button (MANUAL / EXTERNAL_FACTORY / DROP / INFINITE).</li>
 *   <li>{@link RecipeNode}: parallelism slider (1–256, linear), swap-recipe button, inputs/outputs.</li>
 *   <li>{@link SinkNode}: just the item; nothing to configure yet.</li>
 *   <li>{@link SplitterNode}: output-count slider (2–8).</li>
 *   <li>{@link MergerNode}: input-count slider (2–8).</li>
 * </ul>
 *
 * <p>Renders manually (no vanilla widgets) so we don't have to wrestle the screen widget lifecycle
 * every time the selection changes. Mouse events come in via {@link #mouseClicked} /
 * {@link #mouseDragged} / {@link #mouseReleased}.
 */
public final class PropertiesPanel {
    public static final int WIDTH = 152;
    private static final int PADDING = 5;
    private static final int HEADER_HEIGHT = 18;
    private static final int ROW_HEIGHT = 12;
    private static final int SLIDER_HEIGHT = 8;
    private static final int BUTTON_HEIGHT = 14;
    private static final int CLOSE_SIZE = 10;

    private static final int BG_COLOR = 0xFF131726;
    private static final int BORDER = 0xFF2A3148;
    private static final int HEADER_BG = 0xFF2A3148;
    private static final int TEXT = 0xFFEAEAEA;
    private static final int TEXT_DIM = 0xFFA8A8B8;
    private static final int SLIDER_TRACK = 0xFF2A3148;
    private static final int SLIDER_FILL = 0xFFE8B86E;
    private static final int BUTTON_BG = 0xFF2A3148;
    private static final int BUTTON_HOVER_BG = 0xFF3A4868;

    private static final int PARALLELISM_MIN = 1;
    private static final int PARALLELISM_MAX = 256;

    private final Consumer<Node> onNodeUpdated;
    private final Runnable onSwapRecipe;
    private final Runnable onClose;
    private final Supplier<RecipeIndex> indexSupplier;

    private Node selected;
    private int panelX;
    private int panelY;
    private int panelH;
    private boolean draggingSlider;

    public PropertiesPanel(Consumer<Node> onNodeUpdated, Runnable onSwapRecipe, Runnable onClose,
                            Supplier<RecipeIndex> indexSupplier) {
        this.onNodeUpdated = onNodeUpdated;
        this.onSwapRecipe = onSwapRecipe;
        this.onClose = onClose;
        this.indexSupplier = indexSupplier;
    }

    public void setSelected(Node node) {
        this.selected = node;
    }

    public void setLayout(int x, int y, int h) {
        this.panelX = x;
        this.panelY = y;
        this.panelH = h;
    }

    public boolean contains(double sx, double sy) {
        return sx >= panelX && sx <= panelX + WIDTH && sy >= panelY && sy <= panelY + panelH;
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(panelX, panelY, panelX + WIDTH, panelY + panelH, BG_COLOR);
        graphics.fill(panelX - 1, panelY, panelX, panelY + panelH, BORDER);

        Font font = Minecraft.getInstance().font;
        if (selected == null) {
            graphics.drawString(font, "Select a node",
                    panelX + PADDING, panelY + PADDING + 4, TEXT_DIM, false);
            return;
        }

        // Header.
        int hx = panelX;
        int hy = panelY;
        graphics.fill(hx, hy, hx + WIDTH, hy + HEADER_HEIGHT, HEADER_BG);
        graphics.fill(hx, hy + HEADER_HEIGHT, hx + WIDTH, hy + HEADER_HEIGHT + 1, BORDER);
        if (!selected.icon().isEmpty()) {
            graphics.renderItem(selected.icon(), hx + PADDING, hy + 3);
        }
        graphics.drawString(font,
                truncate(font, selected.title().getString(), WIDTH - CLOSE_SIZE - PADDING * 4 - 20),
                hx + PADDING + 20, hy + 7, TEXT, false);

        // Close button in the header.
        int closeX = hx + WIDTH - CLOSE_SIZE - PADDING;
        int closeY = hy + (HEADER_HEIGHT - CLOSE_SIZE) / 2;
        boolean closeHover = mouseX >= closeX && mouseX <= closeX + CLOSE_SIZE
                && mouseY >= closeY && mouseY < closeY + CLOSE_SIZE;
        if (closeHover) graphics.fill(closeX, closeY, closeX + CLOSE_SIZE, closeY + CLOSE_SIZE, BUTTON_HOVER_BG);
        graphics.drawString(font, "×", closeX + 3, closeY + 2, TEXT_DIM, false);

        int contentY = panelY + HEADER_HEIGHT + PADDING;
        if (selected instanceof SourceNode src) {
            renderSource(graphics, font, src, contentY, mouseX, mouseY);
        } else if (selected instanceof RecipeNode rn) {
            renderRecipe(graphics, font, rn, contentY, mouseX, mouseY);
        } else if (selected instanceof SinkNode sn) {
            renderSink(graphics, font, sn, contentY);
        } else if (selected instanceof OutputNode out) {
            renderOutput(graphics, font, out, contentY, mouseX, mouseY);
        } else if (selected instanceof SplitterNode splitter) {
            renderCountSlider(graphics, font, contentY,
                    "Outputs: " + splitter.outputCount(),
                    splitter.outputCount(), SplitterNode.MIN_OUTPUTS, SplitterNode.MAX_OUTPUTS);
            int modeRowY = contentY + ROW_HEIGHT + SLIDER_HEIGHT + ROW_HEIGHT + PADDING * 2;
            graphics.drawString(font, "Mode", panelX + PADDING, modeRowY, TEXT_DIM, false);
            int modeBtnY = modeRowY + ROW_HEIGHT;
            drawButton(graphics, panelX + PADDING, modeBtnY, WIDTH - PADDING * 2, BUTTON_HEIGHT,
                    splitter.mode().name(), mouseX, mouseY);
        } else if (selected instanceof MergerNode merger) {
            renderCountSlider(graphics, font, contentY,
                    "Inputs: " + merger.inputCount(),
                    merger.inputCount(), MergerNode.MIN_INPUTS, MergerNode.MAX_INPUTS);
        } else if (selected instanceof LimiterNode limiter) {
            renderLimiter(graphics, font, limiter, contentY);
        } else if (selected instanceof FilterNode filter) {
            renderFilter(graphics, font, filter, contentY);
        }
    }

    private void renderLimiter(GuiGraphics graphics, Font font, LimiterNode limiter, int y) {
        String label = "Limit: " + formatRate(limiter.limitPerMin()) + "/min";
        graphics.drawString(font, label, panelX + PADDING, y, TEXT, false);
        int sliderY = y + ROW_HEIGHT;
        renderLogSlider(graphics, panelX + PADDING, sliderY, WIDTH - PADDING * 2,
                limiter.limitPerMin(), LimiterNode.MIN_LIMIT, LimiterNode.MAX_LIMIT);
        graphics.drawString(font, "Range: " + (int) LimiterNode.MIN_LIMIT + "–" + (int) LimiterNode.MAX_LIMIT,
                panelX + PADDING, sliderY + SLIDER_HEIGHT + PADDING, TEXT_DIM, false);
        graphics.drawString(font, "Excess is voided.",
                panelX + PADDING, sliderY + SLIDER_HEIGHT + PADDING + ROW_HEIGHT, TEXT_DIM, false);
    }

    private void renderFilter(GuiGraphics graphics, Font font, FilterNode filter, int y) {
        graphics.drawString(font, "Matches", panelX + PADDING, y, TEXT_DIM, false);
        int itemY = y + ROW_HEIGHT;
        if (!filter.matchItem().isEmpty()) {
            graphics.renderItem(filter.matchItem(), panelX + PADDING, itemY);
            graphics.drawString(font, filter.matchItem().getHoverName().getString(),
                    panelX + PADDING + 20, itemY + 4, TEXT, false);
        } else {
            graphics.drawString(font, "(none — set via right-click on canvas)",
                    panelX + PADDING, itemY + 4, TEXT_DIM, false);
        }
        int hintY = itemY + ROW_HEIGHT * 2;
        graphics.drawString(font, "Out 0: match → carries this item",
                panelX + PADDING, hintY, TEXT_DIM, false);
        graphics.drawString(font, "Out 1: reject → everything else",
                panelX + PADDING, hintY + ROW_HEIGHT, TEXT_DIM, false);
    }

    private String formatRate(double v) {
        if (v >= 1000) return String.format("%.1fk", v / 1000);
        if (v >= 100) return String.format("%.0f", v);
        return String.format("%.1f", v);
    }

    /** Log-scale slider for the limiter (we want fine control near 1/min, coarse near 10k/min). */
    private void renderLogSlider(GuiGraphics graphics, int x, int y, int w, double value, double min, double max) {
        graphics.fill(x, y, x + w, y + SLIDER_HEIGHT, SLIDER_TRACK);
        double frac = Math.log(value / min) / Math.log(max / min);
        frac = Math.max(0, Math.min(1, frac));
        int fillW = (int) Math.round(w * frac);
        graphics.fill(x, y, x + fillW, y + SLIDER_HEIGHT, SLIDER_FILL);
    }

    private void renderSource(GuiGraphics graphics, Font font, SourceNode src, int y, int mouseX, int mouseY) {
        graphics.drawString(font, "Kind", panelX + PADDING, y, TEXT_DIM, false);
        int btnY = y + ROW_HEIGHT;
        drawButton(graphics, panelX + PADDING, btnY, WIDTH - PADDING * 2, BUTTON_HEIGHT,
                src.kind().name(), mouseX, mouseY);

        int itemY = btnY + BUTTON_HEIGHT + PADDING * 2;
        graphics.drawString(font, "Item", panelX + PADDING, itemY, TEXT_DIM, false);
        graphics.renderItem(src.item(), panelX + PADDING, itemY + ROW_HEIGHT);
        graphics.drawString(font, src.item().getHoverName().getString(),
                panelX + PADDING + 20, itemY + ROW_HEIGHT + 4, TEXT, false);

        // EXTERNAL_FACTORY sources get a bind-to-output panel.
        if (src.kind() == SourceNode.Kind.EXTERNAL_FACTORY) {
            int refY = itemY + ROW_HEIGHT * 2 + PADDING * 2;
            graphics.drawString(font, "External Output", panelX + PADDING, refY, TEXT_DIM, false);
            String refText = src.externalRef()
                    .map(ref -> {
                        Factory f = FactoryStore.get().findById(ref.factoryId());
                        if (f == null) return "(missing factory)";
                        Optional<OutputNode> out = FactoryStore.get().resolveOutput(ref);
                        return f.name() + " · " + out.map(OutputNode::exportName).orElse("(missing)");
                    })
                    .orElse("Not bound");
            graphics.drawString(font, truncate(font, refText, WIDTH - PADDING * 2),
                    panelX + PADDING, refY + ROW_HEIGHT, TEXT, false);
            int refBtnY = refY + ROW_HEIGHT * 2;
            drawButton(graphics, panelX + PADDING, refBtnY, WIDTH - PADDING * 2, BUTTON_HEIGHT,
                    "Bind to output…", mouseX, mouseY);
        }
    }

    private void renderOutput(GuiGraphics graphics, Font font, OutputNode out, int y, int mouseX, int mouseY) {
        graphics.drawString(font, "Export name", panelX + PADDING, y, TEXT_DIM, false);
        int btnY = y + ROW_HEIGHT;
        String label = out.exportName().isEmpty() ? "(rename…)" : out.exportName();
        drawButton(graphics, panelX + PADDING, btnY, WIDTH - PADDING * 2, BUTTON_HEIGHT,
                label, mouseX, mouseY);

        int itemY = btnY + BUTTON_HEIGHT + PADDING * 2;
        graphics.drawString(font, "Item", panelX + PADDING, itemY, TEXT_DIM, false);
        graphics.renderItem(out.item(), panelX + PADDING, itemY + ROW_HEIGHT);
        graphics.drawString(font, out.item().getHoverName().getString(),
                panelX + PADDING + 20, itemY + ROW_HEIGHT + 4, TEXT, false);

        int hintY = itemY + ROW_HEIGHT * 2 + PADDING * 2;
        graphics.drawString(font, "Exposed to other factories",
                panelX + PADDING, hintY, TEXT_DIM, false);
    }

    private void renderRecipe(GuiGraphics graphics, Font font, RecipeNode rn, int y, int mouseX, int mouseY) {
        graphics.drawString(font, "Parallelism: " + rn.parallelism(),
                panelX + PADDING, y, TEXT, false);
        int sliderY = y + ROW_HEIGHT;
        renderSlider(graphics, panelX + PADDING, sliderY, WIDTH - PADDING * 2,
                rn.parallelism(), PARALLELISM_MIN, PARALLELISM_MAX);

        int btnY = sliderY + SLIDER_HEIGHT + PADDING * 2;
        drawButton(graphics, panelX + PADDING, btnY, WIDTH - PADDING * 2, BUTTON_HEIGHT,
                "Swap recipe…", mouseX, mouseY);

        int inY = btnY + BUTTON_HEIGHT + PADDING * 2;
        graphics.drawString(font, "Inputs", panelX + PADDING, inY, TEXT_DIM, false);
        int rowY = inY + ROW_HEIGHT;
        List<IngredientStack> origInputs = lookupOriginalInputs(rn);
        for (Port p : rn.inputs()) {
            List<Item> alternates = p.index() < origInputs.size()
                    ? origInputs.get(p.index()).matchingItems()
                    : List.of();
            renderInputRow(graphics, font, p, rowY, alternates, mouseX, mouseY);
            rowY += ROW_HEIGHT + 2;
        }

        rowY += PADDING;
        graphics.drawString(font, "Outputs", panelX + PADDING, rowY, TEXT_DIM, false);
        rowY += ROW_HEIGHT;
        for (Port p : rn.outputs()) {
            renderPortRow(graphics, font, p, rowY);
            rowY += ROW_HEIGHT + 2;
        }
    }

    /** Look up the recipe's original ingredient list (with full matchingItems) so the variant
     *  picker knows which alternates are available. Empty list if the recipe isn't in the index. */
    private List<IngredientStack> lookupOriginalInputs(RecipeNode rn) {
        if (indexSupplier == null) return List.of();
        RecipeIndex idx = indexSupplier.get();
        if (idx == null) return List.of();
        RecipeEntry e = idx.byId(rn.recipeId());
        return e != null ? e.inputs() : List.of();
    }

    private void renderInputRow(GuiGraphics graphics, Font font, Port port, int y,
                                List<Item> alternates, int mouseX, int mouseY) {
        boolean hasCycle = alternates.size() > 1;
        int cycleW = hasCycle ? 12 : 0;
        if (!port.display().isEmpty()) {
            graphics.renderItem(port.display(), panelX + PADDING, y);
        }
        int labelMax = WIDTH - 22 - PADDING * 2 - cycleW;
        graphics.drawString(font, truncate(font, port.label(), labelMax),
                panelX + PADDING + 20, y + 4, TEXT, false);
        if (hasCycle) {
            int bx = panelX + WIDTH - PADDING - cycleW;
            boolean hover = mouseX >= bx && mouseX < bx + cycleW && mouseY >= y && mouseY < y + ROW_HEIGHT;
            if (hover) graphics.fill(bx, y, bx + cycleW, y + ROW_HEIGHT, BUTTON_HOVER_BG);
            graphics.drawString(font, "›", bx + 4, y + 4, TEXT, false);
        }
    }

    private void renderSink(GuiGraphics graphics, Font font, SinkNode sn, int y) {
        graphics.drawString(font, "Item", panelX + PADDING, y, TEXT_DIM, false);
        graphics.renderItem(sn.item(), panelX + PADDING, y + ROW_HEIGHT);
        graphics.drawString(font, sn.item().getHoverName().getString(),
                panelX + PADDING + 20, y + ROW_HEIGHT + 4, TEXT, false);
    }

    /** Shared rendering for Splitter outputCount and Merger inputCount sliders. */
    private void renderCountSlider(GuiGraphics graphics, Font font, int y, String label, int value, int min, int max) {
        graphics.drawString(font, label, panelX + PADDING, y, TEXT, false);
        int sliderY = y + ROW_HEIGHT;
        renderSlider(graphics, panelX + PADDING, sliderY, WIDTH - PADDING * 2, value, min, max);
        graphics.drawString(font, "Range: " + min + "–" + max,
                panelX + PADDING, sliderY + SLIDER_HEIGHT + PADDING, TEXT_DIM, false);
    }

    private void renderPortRow(GuiGraphics graphics, Font font, Port port, int y) {
        if (!port.display().isEmpty()) {
            graphics.renderItem(port.display(), panelX + PADDING, y);
        }
        graphics.drawString(font, truncate(font, port.label(), WIDTH - 22 - PADDING * 2),
                panelX + PADDING + 20, y + 4, TEXT, false);
    }

    private void renderSlider(GuiGraphics graphics, int x, int y, int w, int value, int min, int max) {
        graphics.fill(x, y, x + w, y + SLIDER_HEIGHT, SLIDER_TRACK);
        double frac = max == min ? 0 : (double) (value - min) / (max - min);
        int fillW = (int) Math.round(w * frac);
        graphics.fill(x, y, x + fillW, y + SLIDER_HEIGHT, SLIDER_FILL);
    }

    private void drawButton(GuiGraphics graphics, int x, int y, int w, int h, String label,
                            int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY < y + h;
        graphics.fill(x, y, x + w, y + h, hover ? BUTTON_HOVER_BG : BUTTON_BG);
        Font font = Minecraft.getInstance().font;
        int textWidth = font.width(label);
        graphics.drawString(font, label, x + (w - textWidth) / 2, y + (h - 8) / 2, TEXT, false);
    }

    public boolean mouseClicked(double sx, double sy, int button) {
        if (!contains(sx, sy) || selected == null) return false;
        if (button != 0) return true;

        // Close button.
        int closeX = panelX + WIDTH - CLOSE_SIZE - PADDING;
        int closeY = panelY + (HEADER_HEIGHT - CLOSE_SIZE) / 2;
        if (hits(sx, sy, closeX, closeY, CLOSE_SIZE, CLOSE_SIZE)) {
            if (onClose != null) onClose.run();
            return true;
        }

        int contentY = panelY + HEADER_HEIGHT + PADDING;
        if (selected instanceof SourceNode src) {
            int btnY = contentY + ROW_HEIGHT;
            if (hits(sx, sy, panelX + PADDING, btnY, WIDTH - PADDING * 2, BUTTON_HEIGHT)) {
                SourceNode.Kind[] kinds = SourceNode.Kind.values();
                int next = (src.kind().ordinal() + 1) % kinds.length;
                onNodeUpdated.accept(src.withKind(kinds[next]));
                return true;
            }
            // Bind-to-output button (only when kind is EXTERNAL_FACTORY).
            if (src.kind() == SourceNode.Kind.EXTERNAL_FACTORY) {
                int itemY = btnY + BUTTON_HEIGHT + PADDING * 2;
                int refY = itemY + ROW_HEIGHT * 2 + PADDING * 2;
                int refBtnY = refY + ROW_HEIGHT * 2;
                if (hits(sx, sy, panelX + PADDING, refBtnY, WIDTH - PADDING * 2, BUTTON_HEIGHT)) {
                    Minecraft.getInstance().setScreen(new ExternalOutputPickerScreen(
                            Minecraft.getInstance().screen,
                            ref -> {
                                Optional<OutputNode> resolved = FactoryStore.get().resolveOutput(ref);
                                SourceNode updated = src.withExternalRef(ref);
                                if (resolved.isPresent()) {
                                    updated = updated.withItem(resolved.get().item());
                                }
                                onNodeUpdated.accept(updated);
                            }));
                    return true;
                }
            }
        } else if (selected instanceof OutputNode out) {
            int btnY = contentY + ROW_HEIGHT;
            if (hits(sx, sy, panelX + PADDING, btnY, WIDTH - PADDING * 2, BUTTON_HEIGHT)) {
                Minecraft.getInstance().setScreen(new TextInputModal(
                        Minecraft.getInstance().screen,
                        Component.literal("Rename output"),
                        out.exportName(),
                        newName -> onNodeUpdated.accept(out.withExportName(newName))));
                return true;
            }
        } else if (selected instanceof RecipeNode rn) {
            int sliderY = contentY + ROW_HEIGHT;
            if (hits(sx, sy, panelX + PADDING, sliderY, WIDTH - PADDING * 2, SLIDER_HEIGHT)) {
                draggingSlider = true;
                applySliderDrag(sx);
                return true;
            }
            int btnY = sliderY + SLIDER_HEIGHT + PADDING * 2;
            if (hits(sx, sy, panelX + PADDING, btnY, WIDTH - PADDING * 2, BUTTON_HEIGHT)) {
                onSwapRecipe.run();
                return true;
            }
            // Variant cycle buttons on input rows.
            int inY = btnY + BUTTON_HEIGHT + PADDING * 2;
            int rowY = inY + ROW_HEIGHT;
            List<IngredientStack> origInputs = lookupOriginalInputs(rn);
            int cycleX = panelX + WIDTH - PADDING - 12;
            for (Port p : rn.inputs()) {
                List<Item> alts = p.index() < origInputs.size() ? origInputs.get(p.index()).matchingItems() : List.of();
                if (alts.size() > 1 && hits(sx, sy, cycleX, rowY, 12, ROW_HEIGHT)) {
                    Item current = p.display().isEmpty() ? alts.get(0) : p.display().getItem();
                    int idx = alts.indexOf(current);
                    int next = (idx < 0 ? 1 : (idx + 1) % alts.size());
                    onNodeUpdated.accept(rn.withInputDisplay(p.index(), new ItemStack(alts.get(next))));
                    return true;
                }
                rowY += ROW_HEIGHT + 2;
            }
        } else if (selected instanceof SplitterNode splitter) {
            int sliderY = contentY + ROW_HEIGHT;
            if (hits(sx, sy, panelX + PADDING, sliderY, WIDTH - PADDING * 2, SLIDER_HEIGHT)) {
                draggingSlider = true;
                applySliderDrag(sx);
                return true;
            }
            int modeRowY = contentY + ROW_HEIGHT + SLIDER_HEIGHT + ROW_HEIGHT + PADDING * 2;
            int modeBtnY = modeRowY + ROW_HEIGHT;
            if (hits(sx, sy, panelX + PADDING, modeBtnY, WIDTH - PADDING * 2, BUTTON_HEIGHT)) {
                SplitterNode.Mode[] modes = SplitterNode.Mode.values();
                int next = (splitter.mode().ordinal() + 1) % modes.length;
                onNodeUpdated.accept(splitter.withMode(modes[next]));
                return true;
            }
        } else if (selected instanceof MergerNode || selected instanceof LimiterNode) {
            int sliderY = contentY + ROW_HEIGHT;
            if (hits(sx, sy, panelX + PADDING, sliderY, WIDTH - PADDING * 2, SLIDER_HEIGHT)) {
                draggingSlider = true;
                applySliderDrag(sx);
                return true;
            }
        }
        return true;
    }

    public boolean mouseDragged(double sx, double sy, int button, double dragX, double dragY) {
        if (!draggingSlider) return false;
        applySliderDrag(sx);
        return true;
    }

    public boolean mouseReleased(double sx, double sy, int button) {
        if (draggingSlider) {
            draggingSlider = false;
            return true;
        }
        return false;
    }

    private void applySliderDrag(double sx) {
        if (selected == null) return;
        int x = panelX + PADDING;
        int w = WIDTH - PADDING * 2;
        double frac = Math.max(0, Math.min(1, (sx - x) / w));

        if (selected instanceof RecipeNode rn) {
            int newValue = (int) Math.round(PARALLELISM_MIN + frac * (PARALLELISM_MAX - PARALLELISM_MIN));
            if (newValue != rn.parallelism()) {
                onNodeUpdated.accept(rn.withParallelism(newValue));
            }
        } else if (selected instanceof SplitterNode splitter) {
            int newValue = (int) Math.round(SplitterNode.MIN_OUTPUTS
                    + frac * (SplitterNode.MAX_OUTPUTS - SplitterNode.MIN_OUTPUTS));
            if (newValue != splitter.outputCount()) {
                onNodeUpdated.accept(splitter.withOutputCount(newValue));
            }
        } else if (selected instanceof MergerNode merger) {
            int newValue = (int) Math.round(MergerNode.MIN_INPUTS
                    + frac * (MergerNode.MAX_INPUTS - MergerNode.MIN_INPUTS));
            if (newValue != merger.inputCount()) {
                onNodeUpdated.accept(merger.withInputCount(newValue));
            }
        } else if (selected instanceof LimiterNode limiter) {
            // Log scale: value = min * (max/min)^frac
            double v = LimiterNode.MIN_LIMIT * Math.pow(LimiterNode.MAX_LIMIT / LimiterNode.MIN_LIMIT, frac);
            if (Math.abs(v - limiter.limitPerMin()) > 0.01) {
                onNodeUpdated.accept(limiter.withLimit(v));
            }
        }
    }

    private static boolean hits(double sx, double sy, int x, int y, int w, int h) {
        return sx >= x && sx <= x + w && sy >= y && sy < y + h;
    }

    private static String truncate(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        while (text.length() > 0 && font.width(text + "…") > maxWidth) {
            text = text.substring(0, text.length() - 1);
        }
        return text + "…";
    }
}
