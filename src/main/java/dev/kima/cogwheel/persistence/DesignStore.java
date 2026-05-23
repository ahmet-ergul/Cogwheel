package dev.kima.cogwheel.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.kima.cogwheel.CogwheelConstants;
import dev.kima.cogwheel.model.Design;
import dev.kima.cogwheel.model.codec.DesignCodecs;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Reads / writes {@link Design}s as JSON files under {@code <gameDir>/cogwheel/designs/}.
 *
 * <p>For Phase 7 we use a single fixed slot — {@code current.cw.json} — so Ctrl+S and Ctrl+O have
 * no ambiguity. Phase 9 polish adds a file-browser screen and named designs.
 *
 * <p>The latest version saved is also rotated into a numbered backup ({@code current.cw.json.bak.0..4})
 * so an accidental overwrite of a complex graph is recoverable.
 */
public final class DesignStore {
    private static final String DESIGNS_DIR = "cogwheel/designs";
    private static final String CURRENT_FILE = "current.cw.json";
    private static final int BACKUP_COUNT = 5;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private DesignStore() {}

    public static Path designsDir() {
        Path gameDir = Minecraft.getInstance() != null
                ? Minecraft.getInstance().gameDirectory.toPath()
                : FMLPaths.GAMEDIR.get();
        return gameDir.resolve(DESIGNS_DIR);
    }

    public static Path currentDesignPath() {
        return designsDir().resolve(CURRENT_FILE);
    }

    /**
     * Save {@code design} to {@code current.cw.json}, rotating the previous file into {@code .bak.0}
     * (older backups shift up by one, oldest is dropped).
     *
     * @return true on success
     */
    public static boolean save(Design design) {
        Path target = currentDesignPath();
        try {
            Files.createDirectories(target.getParent());
            rotateBackups(target);
            JsonElement encoded = DesignCodecs.DESIGN.encodeStart(JsonOps.INSTANCE, design)
                    .getOrThrow(IllegalStateException::new);
            String json = GSON.toJson(encoded);
            Files.writeString(target, json);
            CogwheelConstants.LOG.info("Cogwheel: saved design to {}", target);
            return true;
        } catch (Exception e) {
            CogwheelConstants.LOG.error("Cogwheel: failed to save design to {}: {}", target, e.getMessage(), e);
            return false;
        }
    }

    /** Loads {@code current.cw.json}. Returns empty if absent or unparsable. */
    public static Optional<Design> load() {
        Path source = currentDesignPath();
        if (!Files.exists(source)) {
            return Optional.empty();
        }
        try {
            String json = Files.readString(source);
            JsonElement root = JsonParser.parseString(json);
            Design design = DesignCodecs.DESIGN.parse(JsonOps.INSTANCE, root)
                    .getOrThrow(IllegalStateException::new);
            CogwheelConstants.LOG.info("Cogwheel: loaded design from {} ({} nodes, {} edges)",
                    source, design.nodes().size(), design.edges().size());
            return Optional.of(design);
        } catch (Exception e) {
            CogwheelConstants.LOG.error("Cogwheel: failed to load design from {}: {}", source, e.getMessage(), e);
            return Optional.empty();
        }
    }

    private static void rotateBackups(Path target) throws IOException {
        if (!Files.exists(target)) return;
        // Shift backups: bak.3 -> bak.4 (overwrite), bak.2 -> bak.3, ...
        for (int i = BACKUP_COUNT - 1; i >= 0; i--) {
            Path src = target.resolveSibling(target.getFileName() + ".bak." + i);
            Path dst = target.resolveSibling(target.getFileName() + ".bak." + (i + 1));
            if (Files.exists(src)) {
                Files.move(src, dst, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
        // current -> bak.0
        Path bak0 = target.resolveSibling(target.getFileName() + ".bak.0");
        Files.move(target, bak0, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        // Drop the oldest if it exists.
        Path drop = target.resolveSibling(target.getFileName() + ".bak." + BACKUP_COUNT);
        if (Files.exists(drop)) {
            Files.deleteIfExists(drop);
        }
    }
}
