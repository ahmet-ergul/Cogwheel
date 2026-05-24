package dev.kima.cogwheel.client.ui.panel;

import dev.kima.cogwheel.client.ui.ScaledUi;
import dev.kima.cogwheel.client.ui.modal.TextInputModal;
import dev.kima.cogwheel.client.ui.picker.ExternalOutputPickerScreen;
import dev.kima.cogwheel.model.Factory;
import dev.kima.cogwheel.model.FactoryStore;
import dev.kima.cogwheel.model.ClusterNode;
import dev.kima.cogwheel.model.VoidNode;
import dev.kima.cogwheel.model.LimiterNode;
import dev.kima.cogwheel.model.LoopNode;
import dev.kima.cogwheel.model.MergerNode;
import dev.kima.cogwheel.model.Node;
import dev.kima.cogwheel.model.OutputNode;
import dev.kima.cogwheel.model.Port;
import dev.kima.cogwheel.model.RecipeNode;
import dev.kima.cogwheel.model.SinkNode;
import dev.kima.cogwheel.model.SourceNode;
import dev.kima.cogwheel.model.SplitterNode;
import dev.kima.cogwheel.integration.jei.JeiBridge;
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
    /** Returns true when the editor is currently viewing a cluster's inner design. The properties
     *  panel uses this to label the "Inherit RPM from <X>" checkbox correctly. */
    private final java.util.function.BooleanSupplier inClusterSupplier;
    /** Source of the effective ambient RPM (factory's RPM at root, cluster's effective RPM inside
     *  a cluster). Reused when displaying "(inheriting N RPM)" hints. */
    private final java.util.function.IntSupplier ambientRpmSupplier;

    private Node selected;
    /** Latched while inside a frame's render so click-handling sees the same value the render did. */
    private boolean inCluster;
    private int panelX;
    private int panelY;
    private int panelH;
    private boolean draggingSlider;
    /** Which slider the user grabbed — RecipeNode has two (parallelism + RPM override); all other
     *  nodes have one. Default PARALLELISM so existing call paths still work. */
    private SliderKind draggingSliderKind = SliderKind.PARALLELISM;
    private enum SliderKind { PARALLELISM, RECIPE_RPM, CLUSTER_RPM }

    /** Legacy constructor for callers that don't know about cluster context. */
    public PropertiesPanel(Consumer<Node> onNodeUpdated, Runnable onSwapRecipe, Runnable onClose,
                            Supplier<RecipeIndex> indexSupplier) {
        this(onNodeUpdated, onSwapRecipe, onClose, indexSupplier, () -> false,
                () -> Factory.DEFAULT_RPM);
    }

    public PropertiesPanel(Consumer<Node> onNodeUpdated, Runnable onSwapRecipe, Runnable onClose,
                            Supplier<RecipeIndex> indexSupplier,
                            java.util.function.BooleanSupplier inClusterSupplier,
                            java.util.function.IntSupplier ambientRpmSupplier) {
        this.onNodeUpdated = onNodeUpdated;
        this.onSwapRecipe = onSwapRecipe;
        this.onClose = onClose;
        this.indexSupplier = indexSupplier;
        this.inClusterSupplier = inClusterSupplier;
        this.ambientRpmSupplier = ambientRpmSupplier;
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
        // Latch cluster context for this frame so render() and mouseClicked() see the same value.
        this.inCluster = inClusterSupplier.getAsBoolean();
        graphics.fill(panelX, panelY, panelX + WIDTH, panelY + panelH, BG_COLOR);
        graphics.fill(panelX - 1, panelY, panelX, panelY + panelH, BORDER);

        Font font = Minecraft.getInstance().font;
        if (selected == null) {
            ScaledUi.drawString(graphics, font, "Select a node",
                    panelX + PADDING, panelY + PADDING + 4, TEXT_DIM);
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
        ScaledUi.drawString(graphics, font,
                ScaledUi.truncate(font, selected.title().getString(), WIDTH - CLOSE_SIZE - PADDING * 4 - 20),
                hx + PADDING + 20, hy + 7, TEXT);

        // Close button in the header.
        int closeX = hx + WIDTH - CLOSE_SIZE - PADDING;
        int closeY = hy + (HEADER_HEIGHT - CLOSE_SIZE) / 2;
        boolean closeHover = mouseX >= closeX && mouseX <= closeX + CLOSE_SIZE
                && mouseY >= closeY && mouseY < closeY + CLOSE_SIZE;
        if (closeHover) graphics.fill(closeX, closeY, closeX + CLOSE_SIZE, closeY + CLOSE_SIZE, BUTTON_HOVER_BG);
        ScaledUi.drawString(graphics, font,"×", closeX + 3, closeY + 2, TEXT_DIM, false);

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
            ScaledUi.drawString(graphics, font,"Mode", panelX + PADDING, modeRowY, TEXT_DIM, false);
            int modeBtnY = modeRowY + ROW_HEIGHT;
            drawButton(graphics, panelX + PADDING, modeBtnY, WIDTH - PADDING * 2, BUTTON_HEIGHT,
                    splitter.mode().name(), mouseX, mouseY);
        } else if (selected instanceof MergerNode merger) {
            renderCountSlider(graphics, font, contentY,
                    "Inputs: " + merger.inputCount(),
                    merger.inputCount(), MergerNode.MIN_INPUTS, MergerNode.MAX_INPUTS);
        } else if (selected instanceof LimiterNode limiter) {
            renderLimiter(graphics, font, limiter, contentY);
        } else if (selected instanceof VoidNode voidNode) {
            renderCountSlider(graphics, font, contentY,
                    "Inputs: " + voidNode.inputCount(),
                    voidNode.inputCount(), VoidNode.MIN_INPUTS, VoidNode.MAX_INPUTS);
        } else if (selected instanceof ClusterNode cluster) {
            renderCluster(graphics, font, cluster, contentY, mouseX, mouseY);
        } else if (selected instanceof LoopNode loop) {
            renderCountSlider(graphics, font, contentY,
                    "Loops: " + loop.loopCount(),
                    loop.loopCount(), LoopNode.MIN_LOOPS, LoopNode.MAX_LOOPS);
            int hintY = contentY + ROW_HEIGHT + SLIDER_HEIGHT + ROW_HEIGHT + PADDING * 2;
            ScaledUi.drawString(graphics, font, "Connect bottom port to gated", panelX + PADDING, hintY, TEXT_DIM);
            ScaledUi.drawString(graphics, font, "nodes' bottom ports.", panelX + PADDING, hintY + ROW_HEIGHT, TEXT_DIM);
        }
    }

    private void renderCluster(GuiGraphics graphics, Font font, ClusterNode cluster, int y,
                                int mouseX, int mouseY) {
        ScaledUi.drawString(graphics, font,"Name", panelX + PADDING, y, TEXT_DIM, false);
        int renameY = y + ROW_HEIGHT;
        drawButton(graphics, panelX + PADDING, renameY, WIDTH - PADDING * 2, BUTTON_HEIGHT,
                cluster.label().isEmpty() ? "Rename…" : cluster.label(), mouseX, mouseY);

        int parY = renameY + BUTTON_HEIGHT + PADDING * 2;
        ScaledUi.drawString(graphics, font,"Parallelism: " + cluster.parallelism(),
                panelX + PADDING, parY, TEXT, false);
        int sliderY = parY + ROW_HEIGHT;
        renderSlider(graphics, panelX + PADDING, sliderY, WIDTH - PADDING * 2,
                cluster.parallelism(), PARALLELISM_MIN, PARALLELISM_MAX);

        // RPM strip — every cluster has one regardless of kind. When set (override), the cluster's
        // RPM is used as the "factory RPM" for every inner Create node that inherits.
        int rpmY = sliderY + SLIDER_HEIGHT + PADDING * 2;
        int parentRpm = currentFactoryRpm();
        boolean inheriting = cluster.rpmOverride().isEmpty();
        int effectiveRpm = cluster.rpmOverride().orElse(parentRpm);
        String contextLabel = inCluster ? "outer cluster" : "factory";
        renderCheckbox(graphics, font, panelX + PADDING, rpmY, inheriting,
                "Inherit RPM from " + contextLabel, mouseX, mouseY);
        rpmY += ROW_HEIGHT;
        if (!inheriting) {
            ScaledUi.drawString(graphics, font, "RPM: " + effectiveRpm,
                    panelX + PADDING, rpmY, TEXT, false);
            int clRpmSliderY = rpmY + ROW_HEIGHT;
            renderSlider(graphics, panelX + PADDING, clRpmSliderY, WIDTH - PADDING * 2,
                    effectiveRpm, Factory.MIN_RPM, Factory.MAX_RPM);
            rpmY = clRpmSliderY + SLIDER_HEIGHT + PADDING;
        } else {
            ScaledUi.drawString(graphics, font, "(inheriting " + parentRpm + " RPM)",
                    panelX + PADDING, rpmY, TEXT_DIM, false);
            rpmY += ROW_HEIGHT;
        }

        int lockY = rpmY + PADDING;
        String lockLabel = cluster.kind() == ClusterNode.Kind.LOCKED ? "🔒 Locked — click to unlock" : "Unlocked — click to lock";
        drawButton(graphics, panelX + PADDING, lockY, WIDTH - PADDING * 2, BUTTON_HEIGHT,
                lockLabel, mouseX, mouseY);

        int inY = lockY + BUTTON_HEIGHT + PADDING * 2;
        ScaledUi.drawString(graphics, font,"Inputs", panelX + PADDING, inY, TEXT_DIM, false);
        int rowY = inY + ROW_HEIGHT;
        for (Port p : cluster.inputs()) {
            renderPortRow(graphics, font, p, rowY);
            rowY += ROW_HEIGHT + 2;
        }
        rowY += PADDING;
        ScaledUi.drawString(graphics, font,"Outputs", panelX + PADDING, rowY, TEXT_DIM, false);
        rowY += ROW_HEIGHT;
        for (Port p : cluster.outputs()) {
            renderPortRow(graphics, font, p, rowY);
            rowY += ROW_HEIGHT + 2;
        }
    }

    private void renderLimiter(GuiGraphics graphics, Font font, LimiterNode limiter, int y) {
        String label = "Limit: " + formatRate(limiter.limitPerMin()) + "/min";
        ScaledUi.drawString(graphics, font,label, panelX + PADDING, y, TEXT, false);
        int sliderY = y + ROW_HEIGHT;
        renderLogSlider(graphics, panelX + PADDING, sliderY, WIDTH - PADDING * 2,
                limiter.limitPerMin(), LimiterNode.MIN_LIMIT, LimiterNode.MAX_LIMIT);
        ScaledUi.drawString(graphics, font,"Range: " + (int) LimiterNode.MIN_LIMIT + "–" + (int) LimiterNode.MAX_LIMIT,
                panelX + PADDING, sliderY + SLIDER_HEIGHT + PADDING, TEXT_DIM, false);
        ScaledUi.drawString(graphics, font,"Excess is voided.",
                panelX + PADDING, sliderY + SLIDER_HEIGHT + PADDING + ROW_HEIGHT, TEXT_DIM, false);
    }

    /** Vertical space the JEI preview occupies above the rest of the RecipeNode props. */
    private int recipePreviewOffset(RecipeNode rn) {
        if (indexSupplier == null) return 0;
        RecipeIndex idx = indexSupplier.get();
        if (idx == null || idx.byId(rn.recipeId()) == null) return 0;
        return 72 + PADDING;
    }

    private String formatRate(double v) {
        if (v >= 1000) return String.format("%.1fk", v / 1000);
        if (v >= 100) return String.format("%.0f", v);
        return String.format("%.1f", v);
    }

    private String formatSu(double v) {
        if (v >= 1_000_000) return String.format("%.2fM", v / 1_000_000);
        if (v >= 1_000) return String.format("%.1fk", v / 1_000);
        return String.format("%.0f", v);
    }

    /** Ambient RPM the currently-displayed design inherits from — the factory's RPM at the root,
     *  or the cluster's effective RPM when viewing a cluster's inner design. */
    private int currentFactoryRpm() {
        return ambientRpmSupplier.getAsInt();
    }

    /** Small ☐/✔ + label checkbox row used by the RPM strips. Returns the column-width of the
     *  hit area so callers can position the label tooltip / overflow correctly. */
    private void renderCheckbox(GuiGraphics g, Font font, int x, int y, boolean checked,
                                 String label, int mouseX, int mouseY) {
        int box = 10;
        boolean hover = mouseX >= x && mouseX < x + WIDTH - PADDING * 2
                && mouseY >= y && mouseY < y + ROW_HEIGHT;
        // Box.
        int boxColor = checked ? SLIDER_FILL : BUTTON_BG;
        g.fill(x, y + 1, x + box, y + box + 1, boxColor);
        // Border.
        g.fill(x, y + 1, x + box, y + 2, BORDER);
        g.fill(x, y + box, x + box, y + box + 1, BORDER);
        g.fill(x, y + 1, x + 1, y + box + 1, BORDER);
        g.fill(x + box - 1, y + 1, x + box, y + box + 1, BORDER);
        if (checked) {
            // Tick mark — two diagonal pixel runs forming a check.
            g.fill(x + 3, y + 5, x + 4, y + 6, TEXT);
            g.fill(x + 4, y + 6, x + 5, y + 7, TEXT);
            g.fill(x + 5, y + 5, x + 6, y + 6, TEXT);
            g.fill(x + 6, y + 4, x + 7, y + 5, TEXT);
            g.fill(x + 7, y + 3, x + 8, y + 4, TEXT);
        }
        int textColor = hover ? 0xFFFFCC55 : TEXT;
        ScaledUi.drawString(g, font, label, x + box + 4, y + 2, textColor);
    }

    /** Hit-tests the checkbox row at {@code (x, y)} — full row width, so clicking the label
     *  toggles too. */
    private boolean hitsCheckbox(double sx, double sy, int x, int y) {
        return sx >= x && sx < x + WIDTH - PADDING * 2 && sy >= y && sy < y + ROW_HEIGHT;
    }

    /** Vertical pixels reserved by the RPM strip in the RecipeNode props pane. New layout:
     *  <ul>
     *    <li>1 ROW_HEIGHT: checkbox row</li>
     *    <li>If unchecked (override): ROW_HEIGHT (label) + SLIDER_HEIGHT + PADDING</li>
     *    <li>If checked (inheriting): ROW_HEIGHT (dim hint)</li>
     *    <li>+ ROW_HEIGHT (SU readout) + PADDING</li>
     *  </ul> */
    private int recipeRpmStripHeight(RecipeNode rn) {
        if (indexSupplier == null || indexSupplier.get() == null) return 0;
        RecipeEntry e = indexSupplier.get().byId(rn.recipeId());
        if (e == null) return 0;
        boolean isCreate = dev.kima.cogwheel.solver.PowerCalculator.impactPerRpm(e.typeId()) > 0;
        if (!isCreate) return 0;
        boolean inheriting = rn.rpmOverride().isEmpty();
        int h = ROW_HEIGHT; // checkbox
        if (!inheriting) {
            h += ROW_HEIGHT + SLIDER_HEIGHT + PADDING; // label + slider + spacing
        } else {
            h += ROW_HEIGHT; // dim "(inheriting N RPM)" hint
        }
        h += ROW_HEIGHT + PADDING; // SU readout + spacing
        return h;
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
        ScaledUi.drawString(graphics, font,"Kind", panelX + PADDING, y, TEXT_DIM, false);
        int btnY = y + ROW_HEIGHT;
        drawButton(graphics, panelX + PADDING, btnY, WIDTH - PADDING * 2, BUTTON_HEIGHT,
                src.kind().name(), mouseX, mouseY);

        int itemY = btnY + BUTTON_HEIGHT + PADDING * 2;
        ScaledUi.drawString(graphics, font,"Item", panelX + PADDING, itemY, TEXT_DIM, false);
        graphics.renderItem(src.item(), panelX + PADDING, itemY + ROW_HEIGHT);
        ScaledUi.drawString(graphics, font,src.item().getHoverName().getString(),
                panelX + PADDING + 20, itemY + ROW_HEIGHT + 4, TEXT, false);

        // EXTERNAL_FACTORY sources get a bind-to-output panel.
        if (src.kind() == SourceNode.Kind.EXTERNAL_FACTORY) {
            int refY = itemY + ROW_HEIGHT * 2 + PADDING * 2;
            ScaledUi.drawString(graphics, font,"External Output", panelX + PADDING, refY, TEXT_DIM, false);
            String refText = src.externalRef()
                    .map(ref -> {
                        Factory f = FactoryStore.get().findById(ref.factoryId());
                        if (f == null) return "(missing factory)";
                        Optional<OutputNode> out = FactoryStore.get().resolveOutput(ref);
                        return f.name() + " · " + out.map(OutputNode::exportName).orElse("(missing)");
                    })
                    .orElse("Not bound");
            ScaledUi.drawString(graphics, font, ScaledUi.truncate(font,refText, WIDTH - PADDING * 2),
                    panelX + PADDING, refY + ROW_HEIGHT, TEXT, false);
            int refBtnY = refY + ROW_HEIGHT * 2;
            drawButton(graphics, panelX + PADDING, refBtnY, WIDTH - PADDING * 2, BUTTON_HEIGHT,
                    "Bind to output…", mouseX, mouseY);
        }
    }

    private void renderOutput(GuiGraphics graphics, Font font, OutputNode out, int y, int mouseX, int mouseY) {
        ScaledUi.drawString(graphics, font,"Export name", panelX + PADDING, y, TEXT_DIM, false);
        int btnY = y + ROW_HEIGHT;
        String label = out.exportName().isEmpty() ? "(rename…)" : out.exportName();
        drawButton(graphics, panelX + PADDING, btnY, WIDTH - PADDING * 2, BUTTON_HEIGHT,
                label, mouseX, mouseY);

        int itemY = btnY + BUTTON_HEIGHT + PADDING * 2;
        ScaledUi.drawString(graphics, font,"Item", panelX + PADDING, itemY, TEXT_DIM, false);
        graphics.renderItem(out.item(), panelX + PADDING, itemY + ROW_HEIGHT);
        ScaledUi.drawString(graphics, font,out.item().getHoverName().getString(),
                panelX + PADDING + 20, itemY + ROW_HEIGHT + 4, TEXT, false);

        int hintY = itemY + ROW_HEIGHT * 2 + PADDING * 2;
        ScaledUi.drawString(graphics, font,"Exposed to other factories",
                panelX + PADDING, hintY, TEXT_DIM, false);
    }

    private void renderRecipe(GuiGraphics graphics, Font font, RecipeNode rn, int y, int mouseX, int mouseY) {
        // JEI card preview at the top: shows the recipe in JEI's native rendering for context.
        RecipeEntry entry = indexSupplier != null && indexSupplier.get() != null
                ? indexSupplier.get().byId(rn.recipeId()) : null;
        if (entry != null) {
            int previewH = 72;
            int previewX = panelX + PADDING;
            int previewY = y;
            int previewW = WIDTH - PADDING * 2;
            graphics.fill(previewX, previewY, previewX + previewW, previewY + previewH, 0xFF1A1E2A);
            graphics.fill(previewX, previewY, previewX + previewW, previewY + 1, BORDER);
            graphics.fill(previewX, previewY + previewH - 1, previewX + previewW, previewY + previewH, BORDER);
            graphics.fill(previewX, previewY, previewX + 1, previewY + previewH, BORDER);
            graphics.fill(previewX + previewW - 1, previewY, previewX + previewW, previewY + previewH, BORDER);
            JeiBridge.tryRenderPickerRow(graphics, entry.id(), entry.typeId(),
                    previewX, previewY, previewW, previewH, mouseX, mouseY);
            y += previewH + PADDING;
        }
        ScaledUi.drawString(graphics, font,"Parallelism: " + rn.parallelism(),
                panelX + PADDING, y, TEXT, false);
        int sliderY = y + ROW_HEIGHT;
        renderSlider(graphics, panelX + PADDING, sliderY, WIDTH - PADDING * 2,
                rn.parallelism(), PARALLELISM_MIN, PARALLELISM_MAX);

        // RPM strip — Create recipes only. Shows: a [✔/☐] checkbox "Inherit RPM from <context>",
        // and (when unchecked) the RPM slider + value + SU readout. Checked = use the inherited
        // RPM verbatim; unchecked = the node carries its own rpmOverride and the slider edits it.
        int btnY = sliderY + SLIDER_HEIGHT + PADDING * 2;
        boolean isCreate = entry != null
                && dev.kima.cogwheel.solver.PowerCalculator.impactPerRpm(entry.typeId()) > 0;
        if (isCreate) {
            int parentRpm = currentFactoryRpm();
            boolean inheriting = rn.rpmOverride().isEmpty();
            int effectiveRpm = rn.rpmOverride().orElse(parentRpm);
            String contextLabel = inCluster ? "cluster" : "factory";
            // Checkbox row.
            renderCheckbox(graphics, font, panelX + PADDING, btnY, inheriting,
                    "Inherit RPM from " + contextLabel, mouseX, mouseY);
            btnY += ROW_HEIGHT;
            if (!inheriting) {
                ScaledUi.drawString(graphics, font, "RPM: " + effectiveRpm,
                        panelX + PADDING, btnY, TEXT, false);
                int rpmSliderY = btnY + ROW_HEIGHT;
                renderSlider(graphics, panelX + PADDING, rpmSliderY, WIDTH - PADDING * 2,
                        effectiveRpm, dev.kima.cogwheel.model.Factory.MIN_RPM,
                        dev.kima.cogwheel.model.Factory.MAX_RPM);
                btnY = rpmSliderY + SLIDER_HEIGHT + PADDING;
            } else {
                // Even when inheriting, show the inherited value as a dim hint so the user can
                // verify what the node is actually running at without flipping the checkbox.
                ScaledUi.drawString(graphics, font, "(inheriting " + parentRpm + " RPM)",
                        panelX + PADDING, btnY, TEXT_DIM, false);
                btnY += ROW_HEIGHT;
            }
            // Per-recipe SU readout.
            double su = dev.kima.cogwheel.solver.PowerCalculator.suFor(entry.typeId(), effectiveRpm)
                    * rn.parallelism();
            ScaledUi.drawString(graphics, font, "SU: " + formatSu(su),
                    panelX + PADDING, btnY, TEXT_DIM, false);
            btnY += ROW_HEIGHT + PADDING;
        }
        drawButton(graphics, panelX + PADDING, btnY, WIDTH - PADDING * 2, BUTTON_HEIGHT,
                "Swap recipe…", mouseX, mouseY);

        int inY = btnY + BUTTON_HEIGHT + PADDING * 2;
        ScaledUi.drawString(graphics, font,"Inputs", panelX + PADDING, inY, TEXT_DIM, false);
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
        ScaledUi.drawString(graphics, font,"Outputs", panelX + PADDING, rowY, TEXT_DIM, false);
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
        ScaledUi.drawString(graphics, font, ScaledUi.truncate(font,port.label(), labelMax),
                panelX + PADDING + 20, y + 4, TEXT, false);
        if (hasCycle) {
            int bx = panelX + WIDTH - PADDING - cycleW;
            boolean hover = mouseX >= bx && mouseX < bx + cycleW && mouseY >= y && mouseY < y + ROW_HEIGHT;
            if (hover) graphics.fill(bx, y, bx + cycleW, y + ROW_HEIGHT, BUTTON_HOVER_BG);
            ScaledUi.drawString(graphics, font,"›", bx + 4, y + 4, TEXT, false);
        }
    }

    private void renderSink(GuiGraphics graphics, Font font, SinkNode sn, int y) {
        ScaledUi.drawString(graphics, font,"Item", panelX + PADDING, y, TEXT_DIM, false);
        graphics.renderItem(sn.item(), panelX + PADDING, y + ROW_HEIGHT);
        ScaledUi.drawString(graphics, font,sn.item().getHoverName().getString(),
                panelX + PADDING + 20, y + ROW_HEIGHT + 4, TEXT, false);
    }

    /** Shared rendering for Splitter outputCount and Merger inputCount sliders. */
    private void renderCountSlider(GuiGraphics graphics, Font font, int y, String label, int value, int min, int max) {
        ScaledUi.drawString(graphics, font,label, panelX + PADDING, y, TEXT, false);
        int sliderY = y + ROW_HEIGHT;
        renderSlider(graphics, panelX + PADDING, sliderY, WIDTH - PADDING * 2, value, min, max);
        ScaledUi.drawString(graphics, font,"Range: " + min + "–" + max,
                panelX + PADDING, sliderY + SLIDER_HEIGHT + PADDING, TEXT_DIM, false);
    }

    private void renderPortRow(GuiGraphics graphics, Font font, Port port, int y) {
        if (!port.display().isEmpty()) {
            graphics.renderItem(port.display(), panelX + PADDING, y);
        }
        ScaledUi.drawString(graphics, font, ScaledUi.truncate(font,port.label(), WIDTH - 22 - PADDING * 2),
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
        int textWidth = ScaledUi.scaledWidth(font, label);
        ScaledUi.drawString(graphics, font, label, x + (w - textWidth) / 2, y + (h - 8) / 2, TEXT);
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
            int previewOffset = recipePreviewOffset(rn);
            int rContentY = contentY + previewOffset;
            int sliderY = rContentY + ROW_HEIGHT;
            if (hits(sx, sy, panelX + PADDING, sliderY, WIDTH - PADDING * 2, SLIDER_HEIGHT)) {
                draggingSlider = true;
                draggingSliderKind = SliderKind.PARALLELISM;
                applySliderDrag(sx);
                return true;
            }
            // RPM strip — Create recipes only. Layout: checkbox, then either a slider row (when
            // unchecked) or a dim hint (when checked).
            int rpmStripH = recipeRpmStripHeight(rn);
            if (rpmStripH > 0) {
                int checkboxY = sliderY + SLIDER_HEIGHT + PADDING * 2;
                if (hitsCheckbox(sx, sy, panelX + PADDING, checkboxY)) {
                    // Toggle inherit/override. When flipping ON inheritance: clear override. When
                    // flipping OFF (gaining override): seed it with the currently-effective RPM so
                    // the slider doesn't snap to MIN.
                    if (rn.rpmOverride().isPresent()) {
                        onNodeUpdated.accept(rn.withRpmOverride(java.util.Optional.empty()));
                    } else {
                        onNodeUpdated.accept(rn.withRpmOverride(java.util.Optional.of(currentFactoryRpm())));
                    }
                    return true;
                }
                if (rn.rpmOverride().isPresent()) {
                    int rpmSliderY = checkboxY + ROW_HEIGHT + ROW_HEIGHT;
                    if (hits(sx, sy, panelX + PADDING, rpmSliderY, WIDTH - PADDING * 2, SLIDER_HEIGHT)) {
                        draggingSlider = true;
                        draggingSliderKind = SliderKind.RECIPE_RPM;
                        applySliderDrag(sx);
                        return true;
                    }
                }
            }
            int btnY = sliderY + SLIDER_HEIGHT + PADDING * 2 + rpmStripH;
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
        } else if (selected instanceof MergerNode || selected instanceof LimiterNode
                || selected instanceof VoidNode || selected instanceof LoopNode) {
            int sliderY = contentY + ROW_HEIGHT;
            if (hits(sx, sy, panelX + PADDING, sliderY, WIDTH - PADDING * 2, SLIDER_HEIGHT)) {
                draggingSlider = true;
                applySliderDrag(sx);
                return true;
            }
        } else if (selected instanceof ClusterNode cluster) {
            // Rename button
            int renameY = contentY + ROW_HEIGHT;
            if (hits(sx, sy, panelX + PADDING, renameY, WIDTH - PADDING * 2, BUTTON_HEIGHT)) {
                Minecraft.getInstance().setScreen(new TextInputModal(
                        Minecraft.getInstance().screen,
                        Component.literal("Rename cluster"),
                        cluster.label(),
                        newName -> {
                            String n = newName == null || newName.isEmpty() ? "Cluster" : newName;
                            onNodeUpdated.accept(cluster.withLabel(n));
                        }));
                return true;
            }
            // Parallelism slider
            int parY = renameY + BUTTON_HEIGHT + PADDING * 2;
            int sliderY = parY + ROW_HEIGHT;
            if (hits(sx, sy, panelX + PADDING, sliderY, WIDTH - PADDING * 2, SLIDER_HEIGHT)) {
                draggingSlider = true;
                draggingSliderKind = SliderKind.PARALLELISM;
                applySliderDrag(sx);
                return true;
            }
            // Cluster RPM strip — same checkbox + conditional slider pattern as RecipeNode.
            int rpmY = sliderY + SLIDER_HEIGHT + PADDING * 2;
            boolean clInheriting = cluster.rpmOverride().isEmpty();
            if (hitsCheckbox(sx, sy, panelX + PADDING, rpmY)) {
                if (cluster.rpmOverride().isPresent()) {
                    onNodeUpdated.accept(cluster.withRpmOverride(java.util.Optional.empty()));
                } else {
                    onNodeUpdated.accept(cluster.withRpmOverride(java.util.Optional.of(currentFactoryRpm())));
                }
                return true;
            }
            int rpmStripEnd;
            if (!clInheriting) {
                int clRpmSliderY = rpmY + ROW_HEIGHT + ROW_HEIGHT;
                if (hits(sx, sy, panelX + PADDING, clRpmSliderY, WIDTH - PADDING * 2, SLIDER_HEIGHT)) {
                    draggingSlider = true;
                    draggingSliderKind = SliderKind.CLUSTER_RPM;
                    applySliderDrag(sx);
                    return true;
                }
                rpmStripEnd = clRpmSliderY + SLIDER_HEIGHT + PADDING;
            } else {
                rpmStripEnd = rpmY + ROW_HEIGHT + ROW_HEIGHT;
            }
            // Lock toggle button
            int lockY = rpmStripEnd + PADDING;
            if (hits(sx, sy, panelX + PADDING, lockY, WIDTH - PADDING * 2, BUTTON_HEIGHT)) {
                ClusterNode.Kind newKind = cluster.kind() == ClusterNode.Kind.LOCKED
                        ? ClusterNode.Kind.USER : ClusterNode.Kind.LOCKED;
                onNodeUpdated.accept(cluster.withKind(newKind));
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
            draggingSliderKind = SliderKind.PARALLELISM; // reset so the next drag starts cleanly
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
            if (draggingSliderKind == SliderKind.RECIPE_RPM) {
                int newRpm = (int) Math.round(Factory.MIN_RPM + frac * (Factory.MAX_RPM - Factory.MIN_RPM));
                if (newRpm != rn.rpmOverride().orElse(-1)) {
                    onNodeUpdated.accept(rn.withRpmOverride(Optional.of(newRpm)));
                }
            } else {
                int newValue = (int) Math.round(PARALLELISM_MIN + frac * (PARALLELISM_MAX - PARALLELISM_MIN));
                if (newValue != rn.parallelism()) {
                    onNodeUpdated.accept(rn.withParallelism(newValue));
                }
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
        } else if (selected instanceof VoidNode voidNode) {
            int newValue = (int) Math.round(VoidNode.MIN_INPUTS
                    + frac * (VoidNode.MAX_INPUTS - VoidNode.MIN_INPUTS));
            if (newValue != voidNode.inputCount()) {
                onNodeUpdated.accept(voidNode.withInputCount(newValue));
            }
        } else if (selected instanceof LoopNode loop) {
            int newValue = (int) Math.round(LoopNode.MIN_LOOPS
                    + frac * (LoopNode.MAX_LOOPS - LoopNode.MIN_LOOPS));
            if (newValue != loop.loopCount()) {
                onNodeUpdated.accept(loop.withLoopCount(newValue));
            }
        } else if (selected instanceof ClusterNode cluster) {
            if (draggingSliderKind == SliderKind.CLUSTER_RPM) {
                int newRpm = (int) Math.round(Factory.MIN_RPM + frac * (Factory.MAX_RPM - Factory.MIN_RPM));
                if (newRpm != cluster.rpmOverride().orElse(-1)) {
                    onNodeUpdated.accept(cluster.withRpmOverride(java.util.Optional.of(newRpm)));
                }
            } else {
                int newValue = (int) Math.round(PARALLELISM_MIN + frac * (PARALLELISM_MAX - PARALLELISM_MIN));
                if (newValue != cluster.parallelism()) {
                    onNodeUpdated.accept(cluster.withParallelism(newValue));
                }
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
