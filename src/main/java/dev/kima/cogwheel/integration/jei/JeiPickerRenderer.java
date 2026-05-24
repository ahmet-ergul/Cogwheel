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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

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

    /** Drawables are surprisingly expensive to construct (each one allocates a fresh
     *  {@code CycleTicker} with a NEW random offset), so re-creating per frame makes JEI's tag
     *  ingredient cycling visibly stutter — the displayed item snaps to a new random alternate
     *  every frame instead of cycling once per second. We cache by entry id and rebuild only on
     *  JEI runtime change (handled via {@link #clearCache}). */
    private static final Map<ResourceLocation, IRecipeLayoutDrawable<?>> DRAWABLE_CACHE = new ConcurrentHashMap<>();

    /** JEI's {@code RecipeLayout.tick()} is the per-tick hook that advances {@code CycleTicker}.
     *  The interface doesn't expose it, so we reflect. ~20 Hz matches a vanilla game tick. */
    private static long lastTickMs = 0;
    private static java.lang.reflect.Method cachedTickMethod;

    static void clearCache() {
        DRAWABLE_CACHE.clear();
    }

    /**
     * Tick every cached drawable at ~20 Hz so its internal {@code CycleTicker} advances. Called
     * from {@link #tryRender} on every frame; the rate limiter keeps us honest.
     */
    private static void maybeTickAll() {
        long now = System.currentTimeMillis();
        if (now - lastTickMs < 50) return; // 20 Hz = once per game tick
        lastTickMs = now;
        for (IRecipeLayoutDrawable<?> drawable : DRAWABLE_CACHE.values()) {
            try {
                if (cachedTickMethod == null
                        || !cachedTickMethod.getDeclaringClass().isInstance(drawable)) {
                    cachedTickMethod = drawable.getClass().getMethod("tick");
                }
                cachedTickMethod.invoke(drawable);
            } catch (ReflectiveOperationException e) {
                // tick() isn't where we expect — give up on ticking but keep the cache so we don't
                // hammer reflection every frame.
                cachedTickMethod = null;
                return;
            }
        }
    }

    private JeiPickerRenderer() {}

    static boolean tryRender(GuiGraphics graphics, Object runtimeObj,
                             ResourceLocation entryId, ResourceLocation typeId,
                             int rowX, int rowY, int rowW, int rowH,
                             double mouseX, double mouseY) {
        try {
            IJeiRuntime runtime = (IJeiRuntime) runtimeObj;
            IRecipeManager rm = runtime.getRecipeManager();

            IRecipeLayoutDrawable<?> drawable = DRAWABLE_CACHE.get(entryId);
            if (drawable == null) {
                CategoryAndRecipe pair = resolveCategoryAndRecipe(rm, typeId, entryId);
                if (pair == null) return false;
                Optional<? extends IRecipeLayoutDrawable<?>> drawableOpt =
                        createDrawable(runtime, rm, pair.category(), pair.recipe());
                if (drawableOpt.isEmpty()) return false;
                drawable = drawableOpt.get();
                DRAWABLE_CACHE.put(entryId, drawable);
            }
            // Advance the cycle so tag-backed ingredients actually rotate at JEI's natural pace.
            maybeTickAll();

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

    /** Together: a JEI category whose recipe class accepts the recipe instance, ready to render. */
    record CategoryAndRecipe(IRecipeCategory<?> category, Object recipe) {}

    /**
     * Resolve the right (category, recipe) to render for this entry. Most entries: category from
     * the entry's typeId, recipe via {@link #findRecipeInstance}. Mirror entries override the
     * category to match the UNDERLYING recipe's actual MC RecipeType — JEI's drawable construction
     * casts the recipe to the category's expected class, so e.g. an item_application recipe under
     * the deploying category UID would crash. We render mirrors using the original recipe's actual
     * category (so brass-casing-via-deployer shows the item_application JEI card rather than
     * fallback art).
     */
    static CategoryAndRecipe resolveCategoryAndRecipe(IRecipeManager rm, ResourceLocation typeId, ResourceLocation entryId) {
        ResourceLocation aliasedOriginal =
                dev.kima.cogwheel.recipe.source.RebuildTrigger.getMirrorOriginal(entryId);
        if (aliasedOriginal != null) {
            var mc = Minecraft.getInstance();
            if (mc.level != null) {
                var holder = mc.level.getRecipeManager().byKey(aliasedOriginal);
                if (holder.isPresent()) {
                    RecipeHolder<?> rh = holder.get();
                    ResourceLocation actualType = BuiltInRegistries.RECIPE_TYPE.getKey(rh.value().getType());
                    if (actualType != null) {
                        IRecipeCategory<?> cat = findCategoryByUid(rm, actualType);
                        if (cat != null && cat.getRecipeType().getRecipeClass().isInstance(rh)) {
                            return new CategoryAndRecipe(cat, rh);
                        }
                    }
                }
            }
        }
        IRecipeCategory<?> category = findCategoryByUid(rm, typeId);
        if (category == null) return null;
        Object recipe = findRecipeInstance(rm, category, entryId);
        if (recipe == null) return null;
        return new CategoryAndRecipe(category, recipe);
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
    static Object findRecipeInstance(IRecipeManager rm, IRecipeCategory<?> category, ResourceLocation entryId) {
        // JEI-enumerated entries live in the bridge's map keyed by the synthetic cogwheel_jei id.
        Object jeiSourced = JeiBridge.lookupJeiSourcedRecipe(entryId);
        if (jeiSourced != null && category.getRecipeType().getRecipeClass().isInstance(jeiSourced)) {
            return jeiSourced;
        }
        // Cogwheel category-alias mirrors (e.g. create:item_application surfaced under create:deploying)
        // store the original recipe id in RebuildTrigger; resolve the underlying RecipeHolder so JEI
        // draws the recipe inside the aliased category's visual.
        ResourceLocation aliasedOriginal = dev.kima.cogwheel.recipe.source.RebuildTrigger.getMirrorOriginal(entryId);
        ResourceLocation lookupId = aliasedOriginal != null ? aliasedOriginal : entryId;
        var mc = Minecraft.getInstance();
        if (mc.level != null) {
            var holder = mc.level.getRecipeManager().byKey(lookupId);
            if (holder.isPresent()) {
                Object value = holder.get();
                if (category.getRecipeType().getRecipeClass().isInstance(value)) {
                    return value;
                }
            }
        }
        // Last resort for JEI virtual recipes that aren't in our bridge map: scan by getUid().
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
