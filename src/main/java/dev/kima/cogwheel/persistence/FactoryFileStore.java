package dev.kima.cogwheel.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.kima.cogwheel.CogwheelConstants;
import dev.kima.cogwheel.model.FactoryStore;
import dev.kima.cogwheel.model.codec.DesignCodecs;
import dev.kima.cogwheel.model.codec.DesignCodecs.FactoryStoreData;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/**
 * Reads / writes the whole multi-factory state to a single JSON file under
 * {@code <gameDir>/cogwheel/store.json}. Replaces the Phase 7 {@code DesignStore} — old
 * single-factory data is intentionally NOT migrated; the user starts with a fresh "Default
 * Factory" on first launch.
 */
public final class FactoryFileStore {
    private static final String STORE_FILE = "cogwheel/store.json";
    private static final int BACKUP_COUNT = 5;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private FactoryFileStore() {}

    public static Path storeFilePath() {
        Path gameDir = Minecraft.getInstance() != null
                ? Minecraft.getInstance().gameDirectory.toPath()
                : FMLPaths.GAMEDIR.get();
        return gameDir.resolve(STORE_FILE);
    }

    /**
     * Saves the singleton {@link FactoryStore} to disk. Rotates backups so an accidental overwrite
     * is recoverable. Returns true on success.
     */
    public static boolean save() {
        Path target = storeFilePath();
        FactoryStore store = FactoryStore.get();
        try {
            Files.createDirectories(target.getParent());
            rotateBackups(target);

            FactoryStoreData data = new FactoryStoreData(
                    DesignCodecs.FORMAT_VERSION,
                    Optional.ofNullable(store.currentId()),
                    store.all());
            JsonElement encoded = DesignCodecs.FACTORY_STORE.encodeStart(JsonOps.INSTANCE, data)
                    .getOrThrow(IllegalStateException::new);
            Files.writeString(target, GSON.toJson(encoded));
            store.markClean();
            CogwheelConstants.LOG.info("Cogwheel: saved {} factories to {}", store.all().size(), target);
            return true;
        } catch (Exception e) {
            CogwheelConstants.LOG.error("Cogwheel: failed to save factory store to {}: {}", target, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Loads {@code store.json} into the singleton {@link FactoryStore}. Returns true if a file was
     * found and parsed; false if no file existed yet (caller bootstraps a default factory).
     */
    public static boolean load() {
        Path source = storeFilePath();
        if (!Files.exists(source)) return false;
        try {
            String json = Files.readString(source);
            JsonElement root = JsonParser.parseString(json);
            FactoryStoreData data = DesignCodecs.FACTORY_STORE.parse(JsonOps.INSTANCE, root)
                    .getOrThrow(IllegalStateException::new);
            FactoryStore.get().loadFrom(data.factories(), data.currentFactoryId().orElse(null));
            CogwheelConstants.LOG.info("Cogwheel: loaded {} factories from {}", data.factories().size(), source);
            return true;
        } catch (Exception e) {
            CogwheelConstants.LOG.error("Cogwheel: failed to load factory store from {}: {}", source, e.getMessage(), e);
            return false;
        }
    }

    private static void rotateBackups(Path target) throws IOException {
        if (!Files.exists(target)) return;
        for (int i = BACKUP_COUNT - 1; i >= 0; i--) {
            Path src = target.resolveSibling(target.getFileName() + ".bak." + i);
            Path dst = target.resolveSibling(target.getFileName() + ".bak." + (i + 1));
            if (Files.exists(src)) {
                Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        Path bak0 = target.resolveSibling(target.getFileName() + ".bak.0");
        Files.move(target, bak0, StandardCopyOption.REPLACE_EXISTING);
        Path drop = target.resolveSibling(target.getFileName() + ".bak." + BACKUP_COUNT);
        Files.deleteIfExists(drop);
    }
}
