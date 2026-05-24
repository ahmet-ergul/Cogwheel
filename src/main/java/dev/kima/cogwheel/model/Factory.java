package dev.kima.cogwheel.model;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A named factory — the top-level container that {@link Design} used to be. The user can have
 * many factories in the same save, switch between them in the left panel, and reference one
 * factory's {@link OutputNode}s from another's {@link SourceNode}s.
 *
 * <p>Power model (Create integration): each factory optionally attaches to a {@link PowerSource}
 * and declares a default operating {@code rpm}. Individual Create-backed {@link RecipeNode}s may
 * override the RPM via {@link RecipeNode#rpmOverride()}; otherwise the factory RPM applies.
 */
public record Factory(UUID id, String name, Design design,
                       Optional<UUID> powerSourceId, int rpm) {
    /** Create's hard cap on rotational speed (signed in-game; we treat it as a positive max). */
    public static final int MAX_RPM = 256;
    public static final int MIN_RPM = 1;
    public static final int DEFAULT_RPM = 128;

    public Factory {
        if (powerSourceId == null) powerSourceId = Optional.empty();
        rpm = Math.max(MIN_RPM, Math.min(MAX_RPM, rpm));
    }

    /** Back-compat constructor for pre-power-model callers / saves. */
    public Factory(UUID id, String name, Design design) {
        this(id, name, design, Optional.empty(), DEFAULT_RPM);
    }

    public static Factory create(String name) {
        UUID newId = UUID.randomUUID();
        Design empty = new Design(name, List.of(), List.of());
        return new Factory(newId, name == null ? "" : name, empty, Optional.empty(), DEFAULT_RPM);
    }

    public Factory withName(String newName) {
        return new Factory(id, newName == null ? "" : newName, design, powerSourceId, rpm);
    }

    public Factory withDesign(Design newDesign) {
        return new Factory(id, name, newDesign, powerSourceId, rpm);
    }

    public Factory withPowerSource(Optional<UUID> newSourceId) {
        return new Factory(id, name, design, newSourceId == null ? Optional.empty() : newSourceId, rpm);
    }

    public Factory withRpm(int newRpm) {
        return new Factory(id, name, design, powerSourceId, newRpm);
    }
}
