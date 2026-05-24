package dev.kima.cogwheel.solver;

import dev.kima.cogwheel.CogwheelConstants;
import net.minecraft.resources.ResourceLocation;

/**
 * Soft-dep facade for Create's stress lookup. Calling {@link #suFor(ResourceLocation, int)} on a
 * system without Create installed silently returns 0; with Create installed, delegates to
 * {@link dev.kima.cogwheel.recipe.adapter.create.CreateStressLookup}.
 *
 * <p>Using reflection (not a direct import) so the JVM doesn't classload Create-importing classes
 * when Create is absent. The lookup is cached after first success to keep the hot path fast.
 */
public final class PowerCalculator {

    private PowerCalculator() {}

    private static volatile java.lang.reflect.Method impactMethod;
    private static volatile java.lang.reflect.Method suForMethod;
    private static volatile boolean lookupAttempted;

    /** Stress impact PER UNIT OF RPM for one Create component running this recipe type. Returns
     *  0 when Create isn't loaded or the recipe type doesn't map to a Create component. */
    public static double impactPerRpm(ResourceLocation recipeTypeId) {
        if (recipeTypeId == null) return 0;
        if (!net.neoforged.fml.ModList.get().isLoaded("create")) return 0;
        if (!resolveMethods()) return 0;
        try {
            return (double) impactMethod.invoke(null, recipeTypeId);
        } catch (Throwable t) {
            CogwheelConstants.LOG.warn("PowerCalculator.impactPerRpm reflection invoke failed: {}", t.toString());
            return 0;
        }
    }

    /** SU draw for ONE Create component running the given recipe type at {@code rpm}. Returns 0
     *  when Create isn't loaded or the recipe type doesn't map to a known Create component. */
    public static double suFor(ResourceLocation recipeTypeId, int rpm) {
        if (recipeTypeId == null) return 0;
        if (!net.neoforged.fml.ModList.get().isLoaded("create")) return 0;
        if (!resolveMethods()) return 0;
        try {
            return (double) suForMethod.invoke(null, recipeTypeId, rpm);
        } catch (Throwable t) {
            CogwheelConstants.LOG.warn("PowerCalculator.suFor reflection invoke failed: {}", t.toString());
            return 0;
        }
    }

    private static boolean resolveMethods() {
        if (impactMethod != null && suForMethod != null) return true;
        if (lookupAttempted) return impactMethod != null && suForMethod != null;
        lookupAttempted = true;
        try {
            Class<?> cls = Class.forName("dev.kima.cogwheel.recipe.adapter.create.CreateStressLookup");
            impactMethod = cls.getMethod("impactPerRpm", ResourceLocation.class);
            suForMethod = cls.getMethod("suFor", ResourceLocation.class, int.class);
            return true;
        } catch (Throwable t) {
            CogwheelConstants.LOG.warn("PowerCalculator: CreateStressLookup unavailable: {}", t.toString());
            return false;
        }
    }
}
