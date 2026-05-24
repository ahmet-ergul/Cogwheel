package dev.kima.cogwheel.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * In-memory list of all factories the user has + which one is currently being edited. Singleton so
 * factory state survives the editor screen closing and reopening.
 *
 * <p>Persistence is handled by {@code FactoryFileStore} (one JSON file containing the whole store).
 * {@link #loadFrom} is called once at startup to populate from disk; mutating operations mark the
 * store dirty so the file is rewritten on Ctrl+S / on factory switch.
 */
public final class FactoryStore {
    private static final FactoryStore INSTANCE = new FactoryStore();

    public static FactoryStore get() {
        return INSTANCE;
    }

    private final List<Factory> factories = new ArrayList<>();
    private UUID currentId;
    private boolean dirty;

    private FactoryStore() {}

    public List<Factory> all() {
        return List.copyOf(factories);
    }

    public UUID currentId() {
        return currentId;
    }

    public Factory current() {
        return findById(currentId);
    }

    public Factory findById(UUID id) {
        if (id == null) return null;
        for (Factory f : factories) {
            if (f.id().equals(id)) return f;
        }
        return null;
    }

    /**
     * Creates a brand-new empty factory and immediately switches to it.
     */
    public Factory createFactory(String name) {
        Factory f = Factory.create(name);
        factories.add(f);
        currentId = f.id();
        dirty = true;
        return f;
    }

    public void rename(UUID id, String newName) {
        for (int i = 0; i < factories.size(); i++) {
            if (factories.get(i).id().equals(id)) {
                factories.set(i, factories.get(i).withName(newName));
                dirty = true;
                return;
            }
        }
    }

    /** Replace an existing factory wholesale, matched by {@link Factory#id()}. Used by callers that
     *  need to update multiple fields atomically (e.g. {@code withPowerSource} + {@code withRpm}). */
    public void replaceFactory(Factory updated) {
        if (updated == null) return;
        for (int i = 0; i < factories.size(); i++) {
            if (factories.get(i).id().equals(updated.id())) {
                factories.set(i, updated);
                dirty = true;
                return;
            }
        }
    }

    public void delete(UUID id) {
        boolean removed = factories.removeIf(f -> f.id().equals(id));
        if (!removed) return;
        if (id.equals(currentId)) {
            currentId = factories.isEmpty() ? null : factories.get(0).id();
        }
        dirty = true;
    }

    public void switchTo(UUID id) {
        if (findById(id) != null) {
            currentId = id;
            dirty = true;
        }
    }

    /** Replace the current factory's design (called whenever the user edits the graph). */
    public void updateCurrent(Design newDesign) {
        if (currentId == null) return;
        for (int i = 0; i < factories.size(); i++) {
            if (factories.get(i).id().equals(currentId)) {
                factories.set(i, factories.get(i).withDesign(newDesign));
                dirty = true;
                return;
            }
        }
    }

    /** Bulk replace — called by {@code FactoryFileStore.load}. */
    public void loadFrom(List<Factory> loaded, UUID current) {
        factories.clear();
        factories.addAll(loaded);
        if (current != null && findById(current) != null) {
            currentId = current;
        } else {
            currentId = factories.isEmpty() ? null : factories.get(0).id();
        }
        dirty = false;
    }

    public boolean isDirty() { return dirty; }
    public void markClean() { dirty = false; }
    public void markDirty() { dirty = true; }

    /**
     * If the store is empty (first run / fresh install), create a single default factory so the
     * editor never has to handle a null current state.
     */
    public Factory bootstrapIfEmpty(String defaultName) {
        if (!factories.isEmpty()) return current();
        return createFactory(defaultName);
    }

    /**
     * Resolve a cross-factory reference: returns the OutputNode the ref points at, if any.
     */
    public Optional<OutputNode> resolveOutput(ExternalRef ref) {
        if (ref == null) return Optional.empty();
        Factory f = findById(ref.factoryId());
        if (f == null) return Optional.empty();
        for (Node n : f.design().nodes()) {
            if (n instanceof OutputNode out && out.id().equals(ref.outputNodeId())) {
                return Optional.of(out);
            }
        }
        return Optional.empty();
    }
}
