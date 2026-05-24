package dev.kima.cogwheel.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.kima.cogwheel.CogwheelConstants;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Singleton store of {@link PowerSource}s persisted to {@code cogwheel/power_sources.json}. Lazy
 * loaded on first access, written on every CRUD operation.
 *
 * <p>Storage shape:
 * <pre>{
 *   "sources": [
 *     {"id": "...", "name": "Steam Engine", "su_capacity": 16384.0},
 *     ...
 *   ]
 * }</pre>
 *
 * <p>No "current" pointer — unlike {@link FactoryStore} the user works with the full list of power
 * sources at all times; they pick which one to assign on a per-factory basis.
 */
public final class PowerSourceStore {
    private static final PowerSourceStore INSTANCE = new PowerSourceStore();

    public static PowerSourceStore get() {
        if (!INSTANCE.loaded) INSTANCE.load();
        return INSTANCE;
    }

    private final List<PowerSource> sources = new ArrayList<>();
    private boolean loaded;

    private PowerSourceStore() {}

    public List<PowerSource> all() {
        if (!loaded) load();
        return List.copyOf(sources);
    }

    public PowerSource findById(UUID id) {
        if (id == null) return null;
        if (!loaded) load();
        for (PowerSource ps : sources) {
            if (ps.id().equals(id)) return ps;
        }
        return null;
    }

    public PowerSource create(String name, double capacity) {
        if (!loaded) load();
        PowerSource ps = PowerSource.create(name, capacity);
        sources.add(ps);
        save();
        return ps;
    }

    public void rename(UUID id, String newName) {
        if (!loaded) load();
        for (int i = 0; i < sources.size(); i++) {
            if (sources.get(i).id().equals(id)) {
                sources.set(i, sources.get(i).withName(newName));
                save();
                return;
            }
        }
    }

    public void setCapacity(UUID id, double newCap) {
        if (!loaded) load();
        for (int i = 0; i < sources.size(); i++) {
            if (sources.get(i).id().equals(id)) {
                sources.set(i, sources.get(i).withCapacity(newCap));
                save();
                return;
            }
        }
    }

    public void delete(UUID id) {
        if (!loaded) load();
        if (sources.removeIf(ps -> ps.id().equals(id))) {
            save();
        }
    }

    private Path filePath() {
        Path gameDir = Minecraft.getInstance() != null
                ? Minecraft.getInstance().gameDirectory.toPath()
                : FMLPaths.GAMEDIR.get();
        return gameDir.resolve("cogwheel/power_sources.json");
    }

    private void load() {
        loaded = true;
        Path file = filePath();
        if (!Files.exists(file)) return;
        try {
            String json = Files.readString(file);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray arr = root.has("sources") ? root.getAsJsonArray("sources") : new JsonArray();
            for (int i = 0; i < arr.size(); i++) {
                JsonObject obj = arr.get(i).getAsJsonObject();
                try {
                    UUID id = UUID.fromString(obj.get("id").getAsString());
                    String name = obj.has("name") ? obj.get("name").getAsString() : "Power Source";
                    double cap = obj.has("su_capacity") ? obj.get("su_capacity").getAsDouble() : 0;
                    sources.add(new PowerSource(id, name, cap));
                } catch (Exception parseEx) {
                    CogwheelConstants.LOG.warn("Skipped malformed PowerSource entry: {}", parseEx.getMessage());
                }
            }
        } catch (Exception e) {
            CogwheelConstants.LOG.error("Failed to load power_sources.json: {}", e.getMessage());
        }
    }

    private void save() {
        Path file = filePath();
        try {
            Files.createDirectories(file.getParent());
            JsonObject root = new JsonObject();
            JsonArray arr = new JsonArray();
            for (PowerSource ps : sources) {
                JsonObject obj = new JsonObject();
                obj.addProperty("id", ps.id().toString());
                obj.addProperty("name", ps.name());
                obj.addProperty("su_capacity", ps.suCapacity());
                arr.add(obj);
            }
            root.add("sources", arr);
            Files.writeString(file, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(root));
        } catch (IOException e) {
            CogwheelConstants.LOG.error("Failed to save power_sources.json: {}", e.getMessage());
        }
    }
}
