package dev.kima.cogwheel.integration.jei;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kima.cogwheel.CogwheelConstants;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * Tries to render a single picker row using JEI's own {@link IRecipeCategory#draw} via
 * {@link IRecipeLayoutDrawable}. Imports JEI types — only class-loaded when called from
 * {@link JeiBridge#tryRenderPickerRow}, which guards on {@code jeiRuntime != null}.
 *
 * <p>Strategy: look up the JEI category whose UID matches our recipe's typeId, find the recipe
 * instance (vanilla via RecipeManager, virtual recipes by scanning JEI's lookup), build the
 * drawable, scale-to-fit, and draw inside the row bounds.
 *
 * <p>If anything fails — category not found, recipe instance not found, drawable creation fails,
 * size doesn't fit — returns false so the picker falls back to its generic layout.
 */
final class JeiPickerRenderer {
    /** Padding inside the row when drawing the JEI layout. */
    private static final int PADDING = 4;

    private JeiPickerRenderer() {}

    static boolean tryRender(GuiGraphics graphics, Object runtimeObj,
                             ResourceLocation entryId, ResourceLocation typeId,
                             int rowX, int rowY, int rowW, int rowH,
                             double mouseX, double mouseY) {
        try {
            IJeiRuntime runtime = (IJeiRuntime) runtimeObj;
            IRecipeManager rm = runtime.getRecipeManager();

            IRecipeCategory<?> category = findCategoryByUid(rm, typeId);
            if (category == null) return false;

            Object recipeInstance = findRecipeInstance(rm, category, entryId);
            if (recipeInstance == null) return false;

            Optional<? extends IRecipeLayoutDrawable<?>> drawableOpt =
                    createDrawable(runtime, rm, category, recipeInstance);
            if (drawableOpt.isEmpty()) return false;
            IRecipeLayoutDrawable<?> drawable = drawableOpt.get();

            Rect2i rect = drawable.getRect();
            int natW = Math.max(1, rect.getWidth());
            int natH = Math.max(1, rect.getHeight());

            int availW = rowW - PADDING * 2;
            int availH = rowH - PADDING * 2;
            float scale = Math.min((float) availW / natW, (float) availH / natH);
            if (scale > 1.0f) scale = 1.0f; // never blow up; only shrink to fit

            int drawnW = Math.round(natW * scale);
            int drawnH = Math.round(natH * scale);

            // Center vertically in the row; left-align horizontally with padding.
            int translateX = rowX + PADDING;
            int translateY = rowY + (rowH - drawnH) / 2;

            PoseStack pose = graphics.pose();
            pose.pushPose();
            pose.translate(translateX, translateY, 0);
            pose.scale(scale, scale, 1);
            // Drawable's setPosition uses INT screen coords (post-transform position 0,0 inside
            // our translated/scaled pose stack). Mouse coords are inverse-transformed so hovers /
            // tooltips land correctly inside the scaled drawable.
            drawable.setPosition(0, 0);
            double localMouseX = (mouseX - translateX) / scale;
            double localMouseY = (mouseY - translateY) / scale;
            drawable.drawRecipe(graphics, (int) localMouseX, (int) localMouseY);
            pose.popPose();

            return true;
        } catch (Throwable t) {
            CogwheelConstants.LOG.debug("JeiPickerRenderer: fallback for {} ({}): {}",
                    entryId, typeId, t.toString());
            return false;
        }
    }

    private static IRecipeCategory<?> findCategoryByUid(IRecipeManager rm, ResourceLocation typeUid) {
        return rm.createRecipeCategoryLookup().get()
                .filter(c -> c.getRecipeType().getUid().equals(typeUid))
                .findFirst()
                .orElse(null);
    }

    /**
     * Find the recipe instance JEI's category expects. Two paths:
     * <ul>
     *   <li>Vanilla recipe types (crafting, smelting, etc.): the recipe IS a {@code RecipeHolder<?>}
     *       which we look up by {@link net.minecraft.client.multiplayer.ClientPacketListener}'s
     *       recipe manager.</li>
     *   <li>JEI virtual types (brewing, anvil): scan the category's own recipe lookup for one whose
     *       getUid matches.</li>
     * </ul>
     */
    private static Object findRecipeInstance(IRecipeManager rm, IRecipeCategory<?> category, ResourceLocation entryId) {
        // Try vanilla RecipeManager (works for crafting/smelting/Create's ProcessingRecipe etc.)
        var mc = Minecraft.getInstance();
        if (mc.level != null) {
            var holder = mc.level.getRecipeManager().byKey(entryId);
            if (holder.isPresent()) {
                Object value = holder.get();
                // JEI's RecipeType for vanilla recipes uses RecipeHolder<X> as T, so passing the
                // holder itself is correct.
                if (category.getRecipeType().getRecipeClass().isInstance(value)) {
                    return value;
                }
            }
        }
        // Fallback for JEI virtual recipes (brewing, anvil): scan category's lookup for matching UID.
        return scanCategoryForUid(rm, category, entryId);
    }

    private static <T> Object scanCategoryForUid(IRecipeManager rm, IRecipeCategory<T> category, ResourceLocation entryId) {
        return rm.createRecipeLookup(category.getRecipeType()).get()
                .filter(r -> {
                    try {
                        var uidMethod = r.getClass().getMethod("getUid");
                        Object uid = uidMethod.invoke(r);
                        return uid instanceof ResourceLocation rl && rl.equals(entryId);
                    } catch (ReflectiveOperationException e) {
                        return false;
                    }
                })
                .findFirst()
                .orElse(null);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Optional<? extends IRecipeLayoutDrawable<?>> createDrawable(
            IJeiRuntime runtime, IRecipeManager rm, IRecipeCategory<?> category, Object recipeInstance) {
        var focusGroup = runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup();
        return rm.createRecipeLayoutDrawable((IRecipeCategory) category, recipeInstance, focusGroup);
    }
}
