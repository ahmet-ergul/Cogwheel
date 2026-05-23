package dev.kima.cogwheel.integration.jei;

import dev.kima.cogwheel.CogwheelConstants;
import dev.kima.cogwheel.recipe.IngredientStack;
import dev.kima.cogwheel.recipe.IngredientStacks;
import dev.kima.cogwheel.recipe.RecipeEntry;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Walks every JEI category and extracts each recipe into a Cogwheel {@link RecipeEntry}. This is
 * what gives the planner visibility into JEI's synthetic categories — Bulk Washing, Automated
 * Packing, Mechanical Crafter, brewing, anvil, etc. — that don't have a 1:1 Minecraft RecipeType.
 *
 * <p>To avoid double-indexing recipes that {@link dev.kima.cogwheel.recipe.source.VanillaRecipeSource}
 * already picked up, this skips {@link RecipeHolder} recipes whose underlying Minecraft RecipeType
 * UID equals the JEI category UID — the 1:1 case. Categories whose UID differs from the underlying
 * RecipeType (JEI virtuals like {@code create:automatic_packing} that re-skin {@code minecraft:crafting}
 * recipes) keep producing entries here, giving the user the JEI-flavored view as additional
 * picker rows.
 *
 * <p>Imports JEI types — only loaded by JEI's annotation scan or from
 * {@link CogwheelJeiPlugin#onRuntimeAvailable}.
 */
final class JeiRecipeEnumerator {
    /** Categories whose entries are not meaningful as Cogwheel recipe nodes. */
    private static final Set<String> SKIP_CATEGORY_UIDS = Set.of(
            "minecraft:fuel",            // burn time, not a transformation
            "minecraft:compostable",     // output is composter fill, not an item
            "minecraft:tag_recipes/item",
            "minecraft:tag_recipes/fluid",
            "minecraft:tag_recipes/block",
            "cogwheel:jei_bridge"        // our own plugin marker, shouldn't appear but be safe
    );

    private JeiRecipeEnumerator() {}

    /** Result bundle: the entries to add to the index + a map of entry-id → recipe instance for
     *  the picker to look up when rendering rows in JEI's own UI. */
    record Result(List<RecipeEntry> entries, Map<ResourceLocation, Object> recipeByEntryId) {}

    static Result extractAll(IJeiRuntime runtime) {
        IRecipeManager rm = runtime.getRecipeManager();
        IFocusGroup empty = runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup();

        List<RecipeEntry> entries = new ArrayList<>();
        Map<ResourceLocation, Object> recipeMap = new HashMap<>();
        Map<String, CategoryStats> perCategory = new LinkedHashMap<>();

        rm.createRecipeCategoryLookup().get().forEach(category -> {
            ResourceLocation catUid = category.getRecipeType().getUid();
            if (SKIP_CATEGORY_UIDS.contains(catUid.toString())) {
                perCategory.put(catUid.toString(), new CategoryStats(0, 0, 0, true));
                return;
            }
            CategoryStats stats = extractCategory(rm, category, empty, entries, recipeMap);
            perCategory.put(catUid.toString(), stats);
        });

        long kept = perCategory.values().stream().mapToLong(s -> s.kept).sum();
        long deduped = perCategory.values().stream().mapToLong(s -> s.deduped).sum();
        long failed = perCategory.values().stream().mapToLong(s -> s.failed).sum();
        CogwheelConstants.LOG.info("JeiRecipeEnumerator: kept={}, dedup-skipped={}, layout-failed={}, across {} categories",
                kept, deduped, failed, perCategory.size());
        // Categories with kept > 0 — what we surface as picker rows.
        perCategory.entrySet().stream()
                .filter(e -> e.getValue().kept > 0)
                .forEach(e -> CogwheelConstants.LOG.info("  kept   {} = {}", e.getKey(), e.getValue().kept));
        // Categories with deduped > 0 — assumed already in vanilla source.
        perCategory.entrySet().stream()
                .filter(e -> e.getValue().deduped > 0 && e.getValue().kept == 0)
                .forEach(e -> CogwheelConstants.LOG.info("  dedup  {} = {}", e.getKey(), e.getValue().deduped));
        // Categories where all attempts failed — possible JEI-state issues.
        perCategory.entrySet().stream()
                .filter(e -> e.getValue().failed > 0)
                .forEach(e -> CogwheelConstants.LOG.info("  failed {} = {} (likely JEI Internal not ready or unsupported recipe)", e.getKey(), e.getValue().failed));
        return new Result(entries, recipeMap);
    }

    private record CategoryStats(int kept, int deduped, int failed, boolean explicitlySkipped) {}

    private static <T> CategoryStats extractCategory(IRecipeManager rm, IRecipeCategory<T> category,
                                                     IFocusGroup empty, List<RecipeEntry> out,
                                                     Map<ResourceLocation, Object> recipeMap) {
        RecipeType<T> type = category.getRecipeType();
        ResourceLocation catUid = type.getUid();
        AtomicInteger fallbackCounter = new AtomicInteger();
        int[] kept = {0}, deduped = {0}, failed = {0};

        rm.createRecipeLookup(type).get().forEach(recipe -> {
            // Dedup against vanilla source: if it's a RecipeHolder under the same MC RecipeType UID
            // as the JEI category UID, vanilla source already indexed it.
            if (recipe instanceof RecipeHolder<?> holder) {
                ResourceLocation mcTypeId = BuiltInRegistries.RECIPE_TYPE.getKey(holder.value().getType());
                if (catUid.equals(mcTypeId)) {
                    deduped[0]++;
                    return;
                }
            }

            try {
                Optional<IRecipeLayoutDrawable<T>> opt = rm.createRecipeLayoutDrawable(category, recipe, empty);
                if (opt.isEmpty()) {
                    failed[0]++;
                    return;
                }
                IRecipeLayoutDrawable<T> drawable = opt.get();
                RecipeEntry entry = buildEntry(catUid, recipe, drawable, fallbackCounter);
                if (entry == null) {
                    failed[0]++;
                    return;
                }
                out.add(entry);
                recipeMap.put(entry.id(), recipe);
                kept[0]++;
            } catch (Throwable t) {
                failed[0]++;
                CogwheelConstants.LOG.debug("JeiRecipeEnumerator: skipping {} in {}: {}",
                        describe(recipe), catUid, t.toString());
            }
        });
        return new CategoryStats(kept[0], deduped[0], failed[0], false);
    }

    private static RecipeEntry buildEntry(ResourceLocation catUid, Object recipe,
                                          IRecipeLayoutDrawable<?> drawable,
                                          AtomicInteger fallbackCounter) {
        IRecipeSlotsView slots = drawable.getRecipeSlotsView();

        List<IngredientStack> rawInputs = new ArrayList<>();
        List<ItemStack> outputs = new ArrayList<>();
        for (IRecipeSlotView slot : slots.getSlotViews()) {
            RecipeIngredientRole role = slot.getRole();
            if (role == RecipeIngredientRole.INPUT) {
                IngredientStack stack = slotToIngredientStack(slot);
                if (!stack.isEmpty()) rawInputs.add(stack);
            } else if (role == RecipeIngredientRole.OUTPUT) {
                slot.getAllIngredients()
                        .map(ITypedIngredient::getItemStack)
                        .flatMap(Optional::stream)
                        .filter(s -> !s.isEmpty())
                        .findFirst()
                        .ifPresent(outputs::add);
            }
            // Catalysts (tools/machines) ignored — they're context, not recipe flow.
        }

        if (rawInputs.isEmpty() && outputs.isEmpty()) return null;

        List<IngredientStack> inputs = IngredientStacks.groupEquivalents(rawInputs);
        ResourceLocation entryId = makeEntryId(catUid, recipe, fallbackCounter);

        return new RecipeEntry(
                entryId, catUid,
                inputs, outputs,
                Optional.empty(), Optional.empty(),
                0,
                Optional.empty());
    }

    private static IngredientStack slotToIngredientStack(IRecipeSlotView slot) {
        List<ItemStack> alternates = slot.getAllIngredients()
                .map(ITypedIngredient::getItemStack)
                .flatMap(Optional::stream)
                .filter(s -> !s.isEmpty())
                .toList();
        if (alternates.isEmpty()) return IngredientStack.EMPTY;
        // Slot count comes from the first concrete alternate (JEI sizes all alternates the same).
        int count = Math.max(1, alternates.get(0).getCount());
        List<Item> matching = alternates.stream().map(ItemStack::getItem).distinct().toList();
        return new IngredientStack(matching, Optional.empty(), count);
    }

    private static ResourceLocation makeEntryId(ResourceLocation catUid, Object recipe,
                                                AtomicInteger fallbackCounter) {
        String recipePart;
        if (recipe instanceof RecipeHolder<?> holder) {
            recipePart = sanitizePath(holder.id().toString());
        } else {
            ResourceLocation uid = tryGetUid(recipe);
            recipePart = uid != null
                    ? sanitizePath(uid.toString())
                    : "r_" + fallbackCounter.getAndIncrement();
        }
        String path = sanitizePath(catUid.toString()) + "/" + recipePart;
        return ResourceLocation.fromNamespaceAndPath("cogwheel_jei", path);
    }

    private static ResourceLocation tryGetUid(Object recipe) {
        try {
            var m = recipe.getClass().getMethod("getUid");
            Object uid = m.invoke(recipe);
            return uid instanceof ResourceLocation rl ? rl : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    /** ResourceLocation paths allow [a-z0-9/._-]. Everything else becomes underscore. */
    private static String sanitizePath(String s) {
        return s.toLowerCase().replaceAll("[^a-z0-9/._-]", "_");
    }

    private static String describe(Object recipe) {
        if (recipe instanceof RecipeHolder<?> h) return h.id().toString();
        return recipe.getClass().getSimpleName() + "@" + Integer.toHexString(System.identityHashCode(recipe));
    }
}
