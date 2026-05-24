package dev.kima.cogwheel.model;

import java.util.UUID;

/**
 * A cross-factory rotational power source — abstractly, a windmill, water wheel, steam engine, or
 * whatever the user wires up in-world. Has a stable id, a user-friendly name, and an SU capacity.
 *
 * <p>Not a {@link Node}: power sources are SHARED across all factories in the save. Each
 * {@link Factory} optionally references one by id. The aggregate SU consumption of every factory
 * pointing at the same source is compared against {@link #suCapacity} to surface over-budget
 * warnings in the editor.
 */
public record PowerSource(UUID id, String name, double suCapacity) {

    public static PowerSource create(String name, double capacity) {
        return new PowerSource(UUID.randomUUID(), name == null ? "Power Source" : name,
                Math.max(0, capacity));
    }

    public PowerSource withName(String newName) {
        return new PowerSource(id, newName == null ? "" : newName, suCapacity);
    }

    public PowerSource withCapacity(double newCap) {
        return new PowerSource(id, name, Math.max(0, newCap));
    }
}
