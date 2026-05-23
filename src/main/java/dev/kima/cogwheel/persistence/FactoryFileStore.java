package dev.kima.cogwheel.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.kima.cogwheel.CogwheelConstants;
import dev.kima.cogwheel.model.Factory;
import dev.kima.cogwheel.model.FactoryStore;
import dev.kima.cogwheel.model.codec.DesignCodecs;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Per-factory JSON persistence. Each factory lives in its own file under
 * {@code <gameDir>/cogwheel/factories/<sanitized-name>.json} so users can share factories the same
 * way as schematics — drop a .json into the folder, hit Refresh, it appears.
 *
 * <p>The pre-alpha {@code store.json} envelope is intentionally NOT migrated. Users on a fresh
 * install start with a single bootstrapped "Default Factory".
 *
 * <p>UUIDs are still the canonical identity. {@link #filePaths} tracks where each factory was
 * loaded from / last saved to, so renames clean up the stale file before writing the new one.
 */
public final class FactoryFileStore {
    private static final String FACTORIES_SUBDIR = "cogwheel/factories";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** factory UUID → current on-disk file path. Repopulated on load + maintained on save/delete. */
    private static final Map<UUID, Path> filePaths = new HashMap<>();

    private FactoryFileStore() {}

    public static Path factoriesDir() {
        Path gameDir = Minecraft.getInstance() != null
                ? Minecraft.getInstance().gameDirectory.toPath()
                : FMLPaths.GAMEDIR.get();
        return gameDir.resolve(FACTORIES_SUBDIR);
    }

    /**
     * Scans {@link #factoriesDir()} for {@code *.json} files, parses each into a {@link Factory},
     * and replaces the singleton {@link FactoryStore}'s contents. Returns the number of factories
     * loaded (0 means empty dir or read errors — caller should bootstrap).
     */
    public static int loadAll() {
        Path dir = factoriesDir();
        filePaths.clear();
        java.util.List<Factory> loaded = new java.util.ArrayList<>();
        if (!Files.exists(dir)) {
            FactoryStore.get().loadFrom(loaded, null);
            return 0;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
            for (Path file : stream) {
                try {
                    String json = Files.readString(file);
                    JsonElement root = JsonParser.parseString(json);
                    Factory f = DesignCodecs.FACTORY.parse(JsonOps.INSTANCE, root)
                            .getOrThrow(IllegalStateException::new);
                    loaded.add(f);
                    filePaths.put(f.id(), file);
                } catch (Exception e) {
                    CogwheelConstants.LOG.error("Cogwheel: failed to read factory {}: {}", file, e.getMessage());
                }
            }
        } catch (IOException e) {
            CogwheelConstants.LOG.error("Cogwheel: failed to scan factories dir {}: {}", dir, e.getMessage());
        }
        FactoryStore.get().loadFrom(loaded, loaded.isEmpty() ? null : loaded.get(0).id());
        CogwheelConstants.LOG.info("Cogwheel: loaded {} factories from {}", loaded.size(), dir);
        return loaded.size();
    }

    /** Writes every factory in the store to its own file. Returns true if all succeeded. */
    public static boolean saveAll() {
        boolean allOk = true;
        for (Factory f : FactoryStore.get().all()) {
            allOk &= saveFactory(f);
        }
        if (allOk) FactoryStore.get().markClean();
        return allOk;
    }

    /**
     * Persist a single factory. If its name has changed since the last save, the old file is
     * deleted so we don't accumulate stale copies on disk.
     */
    public static boolean saveFactory(Factory factory) {
        Path dir = factoriesDir();
        try {
            Files.createDirectories(dir);
            Path newPath = uniquePathFor(factory, dir);
            Path oldPath = filePaths.get(factory.id());

            JsonElement encoded = DesignCodecs.FACTORY.encodeStart(JsonOps.INSTANCE, factory)
                    .getOrThrow(IllegalStateException::new);
            Files.writeString(newPath, GSON.toJson(encoded));
            filePaths.put(factory.id(), newPath);

            if (oldPath != null && !oldPath.equals(newPath) && Files.exists(oldPath)) {
                try { Files.delete(oldPath); } catch (IOException ignored) {}
            }
            return true;
        } catch (Exception e) {
            CogwheelConstants.LOG.error("Cogwheel: failed to save factory {}: {}", factory.name(), e.getMessage(), e);
            return false;
        }
    }

    /** Delete the on-disk file for a factory (called when the user deletes it in the panel). */
    public static void deleteFactoryFile(UUID id) {
        Path path = filePaths.remove(id);
        if (path != null && Files.exists(path)) {
            try { Files.delete(path); }
            catch (IOException e) {
                CogwheelConstants.LOG.error("Cogwheel: failed to delete factory file {}: {}", path, e.getMessage());
            }
        }
    }

    /** Open the factories folder in the OS file explorer. Standard MC pattern. */
    public static void openFactoriesFolder() {
        Path dir = factoriesDir();
        try {
            Files.createDirectories(dir);
        } catch (IOException ignored) {}
        Util.getPlatform().openFile(dir.toFile());
    }

    /**
     * Pick a path that doesn't clash with an existing file owned by a different factory. If the
     * desired name is already used by another factory's file, suffix with the short UUID so both
     * survive.
     */
    private static Path uniquePathFor(Factory factory, Path dir) {
        String base = sanitize(factory.name());
        if (base.isEmpty()) base = "untitled";
        Path desired = dir.resolve(base + ".json");
        Path owned = filePaths.get(factory.id());
        if (owned != null && owned.equals(desired)) return desired;
        if (!Files.exists(desired)) return desired;
        // Collision with another factory's file — disambiguate with a short UUID slice.
        String shortId = factory.id().toString().substring(0, 8);
        return dir.resolve(base + "-" + shortId + ".json");
    }

    private static String sanitize(String name) {
        String lower = name.toLowerCase(Locale.ROOT).trim();
        StringBuilder sb = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '_') {
                sb.append(c);
            } else if (c == ' ' || c == '.') {
                sb.append('_');
            }
            // drop everything else
        }
        return sb.toString();
    }
}
