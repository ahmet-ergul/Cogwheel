package dev.kima.cogwheel.recipe.adapter.create;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.stress.BlockStressValues;
import dev.kima.cogwheel.CogwheelConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-RPM stress impact for each Create recipe type. Primary source is a wiki-aligned hardcoded
 * table — the values match what the Create wiki documents per machine, scaled to "1 component
 * doing 1 recipe at 1 RPM". Effective SU at runtime is {@code impact × rpm × parallelism}.
 *
 * <p><b>Why not just use Create's {@code BlockStressValues} for everything?</b> Create's runtime
 * registry returns 0 for several components in 6.x (notably the encased fan, which only "actually"
 * pulls stress when it's blowing through a medium in-world). For a planner we want the conceptual
 * cost of the recipe regardless of the in-world activation state, so the table is the source of
 * truth and {@link BlockStressValues} is consulted only as a fallback for recipe types we haven't
 * curated.
 *
 * <p>This class touches Create types directly — only call from code paths gated on
 * {@code ModList.isLoaded("create")}.
 */
public final class CreateStressLookup {

    private CreateStressLookup() {}

    /** Wiki-aligned per-RPM stress impacts. Keys are recipe-type ids; values are SU drawn per
     *  unit RPM by a single physical component running the recipe. At factory RPM 128, a crushing
     *  wheel setup (8) draws 8 × 128 = 1024 SU. */
    private static final Map<String, Double> IMPACT_PER_RPM = new HashMap<>();
    static {
        // Crushing Wheel: 8 SU/RPM per pair (one recipe = one pair operating).
        IMPACT_PER_RPM.put("create:crushing",          8.0);
        // Mechanical Mixer: 4 SU/RPM (basin + mixer combo).
        IMPACT_PER_RPM.put("create:mixing",            4.0);
        // Mechanical Press: 8 SU/RPM (basin + press combo).
        IMPACT_PER_RPM.put("create:pressing",          8.0);
        IMPACT_PER_RPM.put("create:compacting",        8.0);
        // Millstone: 4 SU/RPM.
        IMPACT_PER_RPM.put("create:milling",           4.0);
        // Mechanical Saw: 4 SU/RPM.
        IMPACT_PER_RPM.put("create:cutting",           4.0);
        // Deployer: 4 SU/RPM. Both DEPLOYING (auto deploy with input) and ITEM_APPLICATION (manual
        // form that JEI renders as deploying) cost the same physical component.
        IMPACT_PER_RPM.put("create:deploying",         4.0);
        IMPACT_PER_RPM.put("create:item_application",  4.0);
        // Spout (filling) / Item Drain (emptying): fluid handlers.
        IMPACT_PER_RPM.put("create:filling",           4.0);
        IMPACT_PER_RPM.put("create:emptying",          4.0);
        // Encased Fan w/ medium below: 2 SU/RPM. Both splashing (water) and haunting (fire) use
        // the same fan — Create's runtime BlockStressValues returns 0 for the bare fan because
        // it tracks active-state stress separately, so the hardcoded value here is essential.
        IMPACT_PER_RPM.put("create:splashing",         2.0);
        IMPACT_PER_RPM.put("create:haunting",          2.0);
        // Sandpaper polishing: manual recipe with no Create-component cost.
        IMPACT_PER_RPM.put("create:sandpaper_polishing", 0.0);
    }

    /** Recipe-type → block fallback table for the {@link BlockStressValues} secondary lookup.
     *  Only used when {@link #IMPACT_PER_RPM} doesn't have a curated value for a recipe type
     *  (e.g. third-party Create addons adding new recipe categories). */
    private static volatile Map<String, java.util.function.Supplier<Block>> blockByRecipeType;

    private static Map<String, java.util.function.Supplier<Block>> registry() {
        if (blockByRecipeType == null) {
            synchronized (CreateStressLookup.class) {
                if (blockByRecipeType == null) {
                    Map<String, java.util.function.Supplier<Block>> m = new HashMap<>();
                    m.put("create:mixing",            () -> AllBlocks.MECHANICAL_MIXER.get());
                    m.put("create:pressing",          () -> AllBlocks.MECHANICAL_PRESS.get());
                    m.put("create:compacting",        () -> AllBlocks.MECHANICAL_PRESS.get());
                    m.put("create:milling",           () -> AllBlocks.MILLSTONE.get());
                    m.put("create:crushing",          () -> AllBlocks.CRUSHING_WHEEL.get());
                    m.put("create:cutting",           () -> AllBlocks.MECHANICAL_SAW.get());
                    m.put("create:deploying",         () -> AllBlocks.DEPLOYER.get());
                    m.put("create:item_application",  () -> AllBlocks.DEPLOYER.get());
                    m.put("create:filling",           () -> AllBlocks.SPOUT.get());
                    m.put("create:emptying",          () -> AllBlocks.ITEM_DRAIN.get());
                    m.put("create:splashing",         () -> AllBlocks.ENCASED_FAN.get());
                    m.put("create:haunting",          () -> AllBlocks.ENCASED_FAN.get());
                    blockByRecipeType = m;
                }
            }
        }
        return blockByRecipeType;
    }

    /** Stress impact PER UNIT OF RPM for the component that runs the given recipe type. Returns
     *  the curated wiki value when available; otherwise falls back to Create's registered
     *  {@link BlockStressValues}. Returns 0 for non-Create recipes or when both sources fail. */
    public static double impactPerRpm(ResourceLocation recipeTypeId) {
        if (recipeTypeId == null) return 0;
        String id = recipeTypeId.toString();
        Double curated = IMPACT_PER_RPM.get(id);
        if (curated != null) return curated;
        try {
            var supplier = registry().get(id);
            if (supplier == null) return 0;
            Block block = supplier.get();
            if (block == null) return 0;
            return BlockStressValues.getImpact(block);
        } catch (Throwable t) {
            CogwheelConstants.LOG.warn("CreateStressLookup.impactPerRpm({}) fallback failed: {}",
                    recipeTypeId, t.toString());
            return 0;
        }
    }

    /** Convenience: full SU for one component running this recipe at {@code rpm}. */
    public static double suFor(ResourceLocation recipeTypeId, int rpm) {
        return impactPerRpm(recipeTypeId) * Math.max(0, rpm);
    }
}
