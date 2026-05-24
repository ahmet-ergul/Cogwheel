package dev.kima.cogwheel.settings;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.kima.cogwheel.CogwheelConstants;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Singleton config for editor colors + sizes + behavior. Persisted in {@code cogwheel/settings.json}
 * — loaded once on first access, saved on every setter call.
 *
 * <p>Settings are organized along TWO axes:
 * <ul>
 *   <li>{@link Category} — the top-level tab the setting lives under in the Settings page UI
 *       (Colors / Texts / Cogwheel).</li>
 *   <li>{@link Section} — finer in-tab grouping, drives the visual section dividers within a tab.</li>
 * </ul>
 *
 * <p>Three setting flavors:
 * <ul>
 *   <li>{@link Key} — ARGB color overrides</li>
 *   <li>{@link NumKey} — numeric (float) settings with min/max/step</li>
 *   <li>{@link BoolKey} — on/off toggles</li>
 * </ul>
 *
 * <p>All three round-trip through the same {@code settings.json} file via stable enum names; new
 * keys added in a future Cogwheel version simply fall back to their defaults on older saves.
 */
public final class CogwheelSettings {

    /** Top-level tab grouping shown in the Settings page. */
    public enum Category { COLORS, TEXTS, COGWHEEL }

    /** Finer grouping inside a tab — drives section dividers / headers. */
    public enum Section {
        CANVAS  (Category.COLORS, "Canvas"),
        NODES   (Category.COLORS, "Nodes"),
        EDGES   (Category.COLORS, "Edges"),
        TEXT    (Category.TEXTS,  "Text colors"),
        SIZES   (Category.TEXTS,  "Sizes"),
        COGWHEEL(Category.COGWHEEL, "Cogwheel");

        public final Category category;
        public final String label;

        Section(Category category, String label) {
            this.category = category;
            this.label = label;
        }
    }

    /** ARGB color overrides — clickable swatch in the UI opens the picker. */
    public enum Key {
        CANVAS_BG       (Section.CANVAS, 0xFF1A1E2A, "Canvas background"),
        CANVAS_BG_LOCKED(Section.CANVAS, 0xFF222632, "Locked cluster background"),
        GRID_DOT        (Section.CANVAS, 0xFF2A3148, "Grid dot color"),

        NODE_BG         (Section.NODES,  0xF21F2233, "Node body"),
        NODE_BORDER     (Section.NODES,  0xFF4A556E, "Node border"),
        NODE_BORDER_SEL (Section.NODES,  0xFFFFCC55, "Selected border"),
        NODE_HEADER_TOP (Section.NODES,  0xFF323A55, "Node header (top)"),
        NODE_HEADER_BOT (Section.NODES,  0xFF272D42, "Node header (bottom)"),
        CLUSTER_BORDER  (Section.NODES,  0xFFB89AE8, "Cluster border"),
        CLUSTER_HDR_TOP (Section.NODES,  0xFF483F66, "Cluster header (top)"),
        CLUSTER_HDR_BOT (Section.NODES,  0xFF3A3252, "Cluster header (bottom)"),
        PORT_ITEM       (Section.NODES,  0xFFE8B86E, "Item port color"),
        PORT_FLUID      (Section.NODES,  0xFF6EBBE8, "Fluid port color"),

        EDGE_ITEM       (Section.EDGES,  0xFFE8B86E, "Edge (item) color"),
        EDGE_FLUID      (Section.EDGES,  0xFF6EBBE8, "Edge (fluid) color"),
        EDGE_SELECTED   (Section.EDGES,  0xFFFFCC55, "Selected edge color"),

        TEXT            (Section.TEXT,   0xFFEAEAEA, "Primary text"),
        TEXT_DIM        (Section.TEXT,   0xFFA8A8B8, "Dim text");

        public final Section section;
        public final int defaultColor;
        public final String label;

        Key(Section section, int defaultColor, String label) {
            this.section = section;
            this.defaultColor = defaultColor;
            this.label = label;
        }
    }

    /** Numeric (float) settings — sliders / steppers in the UI. */
    public enum NumKey {
        CONTENT_SCALE(Section.SIZES, 0.8f, 0.5f, 1.5f, 0.05f, "Node text & icon scale"),
        SIDEPANEL_SCALE(Section.SIZES, 1.0f, 0.7f, 1.5f, 0.05f, "Side panel text scale");

        public final Section section;
        public final float defaultValue;
        public final float min;
        public final float max;
        public final float step;
        public final String label;

        NumKey(Section section, float defaultValue, float min, float max, float step, String label) {
            this.section = section;
            this.defaultValue = defaultValue;
            this.min = min;
            this.max = max;
            this.step = step;
            this.label = label;
        }
    }

    /** Boolean toggles — checkbox rows in the UI. */
    public enum BoolKey {
        /** When ON, the recipe picker excludes vanilla minecraft recipes so the user only sees
         *  modded options (typically Create + addons). Useful when planning Create-heavy factories
         *  where the vanilla "X → 9X compaction" recipes would otherwise drown the picker. */
        HIDE_VANILLA_RECIPES(Section.COGWHEEL, true, "Hide default Minecraft recipes"),
        /** Reserved for a future feature: dynamically build example Ponder scenes for the user's
         *  factory using the Ponder mod. Today this is just persisted; future versions will read it. */
        ALLOW_PONDER_CREATION(Section.COGWHEEL, true, "Allow Ponder creation");

        public final Section section;
        public final boolean defaultValue;
        public final String label;

        BoolKey(Section section, boolean defaultValue, String label) {
            this.section = section;
            this.defaultValue = defaultValue;
            this.label = label;
        }
    }

    private static final CogwheelSettings INSTANCE = new CogwheelSettings();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Map<Key, Integer> overrides = new LinkedHashMap<>();
    private final Map<NumKey, Float> numOverrides = new LinkedHashMap<>();
    private final Map<BoolKey, Boolean> boolOverrides = new LinkedHashMap<>();
    private boolean loaded = false;

    private CogwheelSettings() {}

    public static CogwheelSettings get() {
        if (!INSTANCE.loaded) INSTANCE.load();
        return INSTANCE;
    }

    public int color(Key key) {
        if (!loaded) load();
        Integer v = overrides.get(key);
        return v != null ? v : key.defaultColor;
    }

    public void set(Key key, int value) {
        if (!loaded) load();
        if (value == key.defaultColor) overrides.remove(key);
        else overrides.put(key, value);
        save();
    }

    public void reset(Key key) {
        if (!loaded) load();
        overrides.remove(key);
        save();
    }

    public void resetAll() {
        overrides.clear();
        numOverrides.clear();
        boolOverrides.clear();
        save();
    }

    public boolean isOverridden(Key key) {
        return overrides.containsKey(key);
    }

    public float num(NumKey key) {
        if (!loaded) load();
        Float v = numOverrides.get(key);
        return v != null ? v : key.defaultValue;
    }

    public void setNum(NumKey key, float value) {
        if (!loaded) load();
        value = Math.max(key.min, Math.min(key.max, value));
        if (Math.abs(value - key.defaultValue) < 0.0001f) numOverrides.remove(key);
        else numOverrides.put(key, value);
        save();
    }

    public void resetNum(NumKey key) {
        if (!loaded) load();
        numOverrides.remove(key);
        save();
    }

    public boolean isOverridden(NumKey key) {
        return numOverrides.containsKey(key);
    }

    public boolean bool(BoolKey key) {
        if (!loaded) load();
        Boolean v = boolOverrides.get(key);
        return v != null ? v : key.defaultValue;
    }

    public void setBool(BoolKey key, boolean value) {
        if (!loaded) load();
        if (value == key.defaultValue) boolOverrides.remove(key);
        else boolOverrides.put(key, value);
        save();
    }

    public void resetBool(BoolKey key) {
        if (!loaded) load();
        boolOverrides.remove(key);
        save();
    }

    public boolean isOverridden(BoolKey key) {
        return boolOverrides.containsKey(key);
    }

    private Path filePath() {
        Path gameDir = Minecraft.getInstance() != null
                ? Minecraft.getInstance().gameDirectory.toPath()
                : FMLPaths.GAMEDIR.get();
        return gameDir.resolve("cogwheel/settings.json");
    }

    private void load() {
        loaded = true;
        Path file = filePath();
        if (!Files.exists(file)) return;
        try {
            String json = Files.readString(file);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            for (Key k : Key.values()) {
                if (root.has(k.name())) {
                    String hex = root.get(k.name()).getAsString();
                    Integer parsed = parseHex(hex);
                    if (parsed != null) overrides.put(k, parsed);
                }
            }
            for (NumKey k : NumKey.values()) {
                if (root.has(k.name())) {
                    try { numOverrides.put(k, root.get(k.name()).getAsFloat()); } catch (Exception ignored) {}
                }
            }
            for (BoolKey k : BoolKey.values()) {
                if (root.has(k.name())) {
                    try { boolOverrides.put(k, root.get(k.name()).getAsBoolean()); } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            CogwheelConstants.LOG.error("Cogwheel: failed to load settings.json: {}", e.getMessage());
        }
    }

    private void save() {
        Path file = filePath();
        try {
            Files.createDirectories(file.getParent());
            JsonObject root = new JsonObject();
            for (Map.Entry<Key, Integer> e : overrides.entrySet()) {
                root.addProperty(e.getKey().name(), formatHex(e.getValue()));
            }
            for (Map.Entry<NumKey, Float> e : numOverrides.entrySet()) {
                root.addProperty(e.getKey().name(), e.getValue());
            }
            for (Map.Entry<BoolKey, Boolean> e : boolOverrides.entrySet()) {
                root.addProperty(e.getKey().name(), e.getValue());
            }
            Files.writeString(file, GSON.toJson(root));
        } catch (IOException e) {
            CogwheelConstants.LOG.error("Cogwheel: failed to save settings.json: {}", e.getMessage());
        }
    }

    /** "#AARRGGBB" or "0xAARRGGBB" or "AARRGGBB" → int, null if unparseable. */
    public static Integer parseHex(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.startsWith("#")) s = s.substring(1);
        else if (s.startsWith("0x") || s.startsWith("0X")) s = s.substring(2);
        if (s.length() != 8 && s.length() != 6) return null;
        try {
            if (s.length() == 6) s = "FF" + s;
            return (int) Long.parseLong(s, 16);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String formatHex(int argb) {
        return String.format("#%08X", argb);
    }

    /** Convenience: short-circuit color lookup if you just want the default. */
    public static int defaultColor(Key key) { return key.defaultColor; }

    /** Static-import-friendly helper: {@code c(NODE_BG)} reads through the singleton. */
    public static int c(Key key) { return get().color(key); }

    /** Functional helper for renderers that want a {@code Function<Key, Integer>} they can stub
     *  in tests if needed. */
    public static final Function<Key, Integer> COLOR = CogwheelSettings::c;
}
