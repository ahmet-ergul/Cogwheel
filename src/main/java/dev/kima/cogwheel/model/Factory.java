package dev.kima.cogwheel.model;

import java.util.List;
import java.util.UUID;

/**
 * A named factory — the top-level container that {@link Design} used to be. The user can have
 * many factories in the same save, switch between them in the left panel, and reference one
 * factory's {@link OutputNode}s from another's {@link SourceNode}s.
 */
public record Factory(UUID id, String name, Design design) {

    public static Factory create(String name) {
        UUID newId = UUID.randomUUID();
        Design empty = new Design(name, List.of(), List.of());
        return new Factory(newId, name == null ? "" : name, empty);
    }

    public Factory withName(String newName) {
        return new Factory(id, newName == null ? "" : newName, design);
    }

    public Factory withDesign(Design newDesign) {
        return new Factory(id, name, newDesign);
    }
}
