# Cogwheel — Handoff

> Read this if you're picking up Cogwheel in a fresh session. Pairs with:
> - Original design doc: `C:\Users\shift\Downloads\FACTORY_DESIGNER_DESIGN.md`
> - Original implementation plan (Phases 0–3 only): `C:\Users\shift\.claude\plans\c-users-shift-downloads-factory-designe-glowing-dream.md`
> - Memory: `C:\Users\shift\.claude\projects\C--Users-shift-Projects-Cogwheel\memory\`

## What this is

**Cogwheel** is a NeoForge 1.21.1 client-side mod — a node-based visual factory planner for Create-mod packs ("n8n for modded Minecraft"). It is feature-complete through Phase 9f of the original design doc and ships as a 163 KB jar.

- **Mod ID:** `cogwheel`
- **Java package:** `dev.kima.cogwheel`
- **License:** MIT
- **Version:** 0.1.0 (pre-alpha — no save migration guarantees)
- **Author:** kima (NOT shift — that's just the Windows username)
- **Project root:** `C:\Users\shift\Projects\Cogwheel\`

## Build / run

```sh
./gradlew build         # produces build/libs/cogwheel-0.1.0.jar
./gradlew runClient     # launches dev MC with Create + JEI in classpath
./gradlew compileJava   # fast compile-only check
```

Java 21, Gradle wrapper auto-downloads. First `runClient` pulls ~250MB of deps.

## Current state

| Phase | What | Status |
|---|---|---|
| 0 | Mod skeleton + K keybind + EditorScreen | ✅ |
| 1a | Recipe-access API spike | ✅ (verified empirically — see "Hard-won 1.21.1 facts" below) |
| 1b | RecipeIndex + adapters | ✅ |
| 2 | Create ProcessingRecipeAdapter | ✅ |
| 3 | Static canvas (pan/zoom/grid/edges/nodes) | ✅ |
| 4a–e | Interactive editing (drag, ports, context menu, toolbox, picker) | ✅ |
| 5 | PropertiesPanel | ✅ |
| 6 | Solver (forward-design rates) | ✅ |
| 7 | JSON Codec persistence + Ctrl+S/O | ✅ |
| 8 | JEI bridge (brewing + anvil virtual categories) | ✅ |
| 9a | UI bug pass 1 (port overlap, modal opacity, toolbox counter, item-level edges) | ✅ |
| 9b | Collapsible panels + plus button | ✅ |
| 9c | Stacking-nav left panel + categories | ✅ |
| 9d | JEI-rendered recipe picker | ✅ |
| 9e | Logic nodes (Splitter, Merger) | ✅ |
| 9f | Multi-factory + OutputNode + ExternalRef | ✅ |
| 9 polish 2 | Modal cap, 1-to-1 ports, edge delete, splitter/merger config, item-type propagation | ✅ |

**65 Java files total.** Clean compile, clean dev-runtime load.

## Architecture

Three loosely-coupled layers per the original design doc:

```
┌─────────────────────────────────────────────┐
│            UI Layer (client only)           │
│  EditorScreen, Canvas, NodeRenderer,        │
│  LeftPanel (stack-nav), PropertiesPanel,    │
│  RecipePickerScreen, ExternalOutputPicker   │
└──────────────────┬──────────────────────────┘
                   │
┌──────────────────▼──────────────────────────┐
│         Model / Solver / Persistence        │
│  Factory, Design, Node (sealed),            │
│  FactoryStore (singleton),                  │
│  DesignCodecs, FactoryFileStore, Solver     │
└──────────────────┬──────────────────────────┘
                   │
┌──────────────────▼──────────────────────────┐
│         Recipe Index Layer                  │
│  RecipeIndex, RecipeEntry,                  │
│  IngredientStack, RecipeAdapter (+impls)    │
│  + integration.jei (soft-loaded)            │
│  + integration.create (Phase 2 path,        │
│      bundled in recipe.adapter.create)      │
└─────────────────────────────────────────────┘
```

**Why this matters**: the model layer has zero MC-API dependencies in its core records (Vec2, Edge, ExternalRef) — they could in principle be unit-tested without MC. Only the integration packages touch Create / JEI types directly.

### Sealed `Node` hierarchy

```java
sealed interface Node permits
    SourceNode,    // origin of items (manual / external_factory / drop / infinite)
    RecipeNode,    // applies a recipe; has parallelism
    SinkNode,      // local terminal
    SplitterNode,  // 1 input, N outputs (wildcard); configurable count 2-8
    MergerNode,    // N inputs, 1 output (wildcard); configurable count 2-8
    OutputNode;    // exported terminal (referenceable from other factories)
```

All implementations are records. Each has `withPosition(Vec2)` and type-specific withers (e.g. `RecipeNode.withParallelism(int)`).

### Multi-factory

Replaces the Phase 7 single-design model:

```java
record Factory(UUID id, String name, Design design)
record Design(String name, List<Node> nodes, List<Edge> edges)
```

`FactoryStore` is a singleton holding `List<Factory> factories` + `UUID currentId`. State survives EditorScreen open/close. Persistence is single-file: `<gameDir>/cogwheel/store.json` containing `FactoryStoreData(formatVersion, currentFactoryId, factories[])`.

### Soft-dependency pattern

For Create + JEI, the integration packages are **only class-loaded when the dep is present**:

- `integration.jei.JeiBridge` has **zero JEI imports** — safe to reference from main code (`RebuildTrigger`, `RecipePickerScreen`)
- `integration.jei.CogwheelJeiPlugin` (annotated `@JeiPlugin`) and `integration.jei.JeiRecipeExtractor` + `JeiPickerRenderer` import JEI but are loaded only via JEI's annotation scan OR via JeiBridge gated by `jeiRuntime != null`
- Same pattern for `recipe.adapter.create.CreateAdapterBundle` / `ProcessingRecipeAdapter` — gated by `ModList.isLoaded("create")` in `Cogwheel.registerAdapters()`

This means the production jar contains the integration classes but they never load on a system without Create/JEI installed.

## Hard-won 1.21.1 / NeoForge / JEI / Create facts

These are gotchas that took research to figure out. Reference for the next session.

### Recipes are accessible client-side in 1.21.1

The design doc was right that 1.21.2+ moved recipes server-side, but wrong about the API name:

- ❌ `ClientLevel#recipeAccess()` — **does not exist**
- ✅ `level.getRecipeManager()` — the actual method
- ✅ `connection.getRecipeManager()` — alternative, **returns the same instance**

Empirical verification (`[spike:RecipesUpdated]`): `level.getRecipeManager()` and `connection.getRecipeManager()` returned the same instance with 2816 total recipes on a fresh world (1551 crafting + 86 smelting + Create's contribution).

The spike code lives in `recipe/spike/RecipeAccessSpike.java` + `RecipeSpikeListeners.java` and can be re-run anytime via `/cogwheel debug recipes` (a client command).

### `RecipeManager.byType()` doesn't exist

The design doc was wrong here too. Actual methods:
- `getAllRecipesFor(RecipeType<T>)` → `List<RecipeHolder<T>>`
- `getRecipes()` → `Collection<RecipeHolder<?>>` (ALL recipes)
- `byKey(ResourceLocation)` → `Optional<RecipeHolder<?>>`

### Recipes are populated on `RecipesUpdatedEvent`, NOT `LoggingIn`

At `ClientPlayerNetworkEvent.LoggingIn`, the recipe count is 0 — recipes haven't synced yet. The right event is `net.neoforged.neoforge.client.event.RecipesUpdatedEvent`, fired on the main NeoForge event bus once the server has synced its recipes to the client.

`RebuildTrigger` listens to both for belt-and-suspenders + lazy `ensureBuilt()` from EditorScreen.

### `Ingredient` is `Value[]`-backed, not `HolderSet`-backed

The design doc said tag detection via `HolderSet.Named`. Wrong for 1.21.1. Actual API:
- `Ingredient.values` is `Ingredient.Value[]` (package-private)
- Subtypes: `ItemValue` (concrete item) and `TagValue` (tag-backed)
- `Ingredient.getValues()` returns the array; the `TagValue` record's `tag()` accessor is package-private

`IngredientStack.findTag` uses reflection on the simple-name "TagValue" to extract the `TagKey<Item>` for display. This is fragile but the alternative is an access transformer or mixin — overkill for what is currently a UI nicety.

### `ItemStack.OPTIONAL_CODEC` is already `Codec<ItemStack>`

Don't double-wrap with xmap. The "OPTIONAL" in the name means it handles `ItemStack.EMPTY` correctly — it's still typed as `Codec<ItemStack>`, not `Codec<Optional<ItemStack>>`.

### JEI 19.27.0+ API in 1.21.1

- `IRecipeLayoutDrawable.getRect()` returns `Rect2i` (NOT `ScreenRectangle`). Methods are `getWidth()` / `getHeight()`.
- `IRecipeLayoutDrawable.drawRecipe(GuiGraphics, int, int)` (NOT `draw()`). There's a separate `drawOverlays()`.
- `IModPlugin.onRuntimeAvailable(IJeiRuntime)` fires only after world join, not at mod load.
- `IRecipeManager.createRecipeCategoryLookup().get()` returns a `Stream<IRecipeCategory<?>>` for iterating all categories.
- JEI virtual recipes (`IJeiBrewingRecipe`, `IJeiAnvilRecipe`, etc.) have a `getUid()` returning a `ResourceLocation`.

The JeiPickerRenderer uses `PoseStack.scale()` to shrink JEI's natural drawable sizes (crafting ≈ 116×54, smelting ≈ 82×34) to fit the picker's 56px row. Mouse coordinates are inverse-transformed so hover/tooltips work inside the scaled drawable.

### Create 6.0.10 dev runtime gotcha

The non-slim `create-1.21.1:6.0.10-280` jar pulls in a HUGE transitive tree (FTB Chunks, JourneyMap, Curios, CC: Tweaked, Architectury…) that breaks `runClient` because those mavens don't include them at the same coords.

**Fix:** `localRuntime` declares the **slim** variant with `transitive = false`. Cogwheel still sees Create's `ProcessingRecipe` etc. but none of the integration mods get loaded.

```groovy
localRuntime(group: "com.simibubi.create", name: "create-${minecraft_version}",
             version: "6.0.10-280", classifier: "slim") {
    transitive = false
}
```

### NeoForge 21.1.228 deprecations

- `@EventBusSubscriber(bus = Bus.MOD/GAME)` is deprecated — drop the parameter, it's auto-detected
- `Screen.addRenderableWidget` / `removeWidget` are `protected` — pages outside the Screen subclass can't call them directly. We pass a `LeftPanelPage.WidgetHost` interface that EditorScreen implements with delegates.

### ItemRenderer pushes items to higher Z than `GuiGraphics.fill()`

This is why the recipe picker modal originally bled canvas icons through the dim overlay even at 93% alpha. **The fix is NOT a darker dim — it's to stop rendering the parent screen entirely.** RecipePickerScreen uses a solid `0xFF101522` backdrop instead.

## File index

### Top of mod tree
| File | Purpose |
|---|---|
| `src/main/java/dev/kima/cogwheel/Cogwheel.java` | `@Mod` entry. Calls `registerAdapters()` on `FMLClientSetupEvent` |
| `src/main/java/dev/kima/cogwheel/CogwheelConstants.java` | `MODID = "cogwheel"`, shared logger |

### Client UI
| File | Purpose |
|---|---|
| `client/CogwheelClient.java` | Game-bus subscriber: `ClientTickEvent.Post` consumes the K keybind |
| `client/KeyBindings.java` | Mod-bus subscriber: registers `OPEN_EDITOR` keymap (K) |
| `client/ui/EditorScreen.java` | Main screen. Manages canvas + panels + factory state + Ctrl+S/O + Delete/Backspace. **Big file (~600 lines)** |
| `client/ui/canvas/Canvas.java` | Pan/zoom state + selection (node/edge) + drag state |
| `client/ui/canvas/CanvasRenderer.java` | Background + grid + edges + nodes, scissor-clipped, pose-stack-transformed |
| `client/ui/canvas/NodeRenderer.java` | Node rectangle + ports (with PortDisplayResolver for wildcards) |
| `client/ui/canvas/EdgeRenderer.java` | Bezier tessellation; renders selected edges thicker + brighter |
| `client/ui/canvas/LineRenderer.java` | Thick-line quads via `VertexConsumer` + `RenderType.gui()` |
| `client/ui/canvas/GridRenderer.java` | World-space dotted grid with viewport culling |
| `client/ui/canvas/HitTest.java` | Hit-test for nodes / ports (radius-based) / edges (bezier-segment distance) |
| `client/ui/canvas/EdgeValidation.java` | Connection rules: port type, item match, 1-to-1 cardinality |
| `client/ui/canvas/ContextMenu.java` | Right-click popup with (label, action) entries |
| `client/ui/canvas/PortDisplayResolver.java` | Topo-walks design to fill in Splitter/Merger wildcard port displays |
| `client/ui/canvas/DemoDesigns.java` | Hardcoded "iron smelting" demo (Raw Iron → Blasting → Iron Ingot → Sink). Used to bootstrap a fresh Default Factory |
| `client/ui/panel/LeftPanel.java` | Collapsible stack-nav container. Holds the page stack |
| `client/ui/panel/LeftPanelPage.java` | Sealed interface for pages |
| `client/ui/panel/CategoriesPage.java` | Root page; lists Recipes/Sources/Sinks/Outputs/Logic/Factories cards |
| `client/ui/panel/ItemListPage.java` | Searchable scrollable item list (used by Recipes/Sources/Sinks/Outputs) |
| `client/ui/panel/FactoriesPage.java` | Lists factories with switch/rename/delete; "+ New factory" at top |
| `client/ui/panel/PropertiesPanel.java` | Right sidebar — branches per node type. Slider, cycle button, swap-recipe button, rename button, bind-output button |
| `client/ui/panel/SolverOverlay.java` | Bottom status bar: NEEDS / PRODUCES / UNUSED with item-icon + rate badges |
| `client/ui/picker/RecipePickerScreen.java` | Multi-recipe picker for an item. Each row JEI-rendered or fallback layout |
| `client/ui/picker/ExternalOutputPickerScreen.java` | Cross-factory output picker for EXTERNAL_FACTORY sources |
| `client/ui/modal/TextInputModal.java` | Reusable single-line text input modal (used for create / rename factory and rename output) |

### Model
| File | Purpose |
|---|---|
| `model/Node.java` | Sealed interface |
| `model/SourceNode.java`, `RecipeNode.java`, `SinkNode.java`, `SplitterNode.java`, `MergerNode.java`, `OutputNode.java` | The six concrete node records |
| `model/Edge.java` | Edge record (from, fromPort, to, toPort, rate) |
| `model/Port.java`, `PortType.java` | Port + ITEM/FLUID enum |
| `model/Vec2.java` | Simple 2D point record |
| `model/Design.java` | Immutable record. Has `with*` mutators that all return new Designs (including auto-prune of orphaned edges in `withNodeReplaced`) |
| `model/Factory.java` | (id, name, design) record |
| `model/ExternalRef.java` | (factoryId, outputNodeId) record for cross-factory references |
| `model/FactoryStore.java` | Singleton holding all factories + current selection. `resolveOutput(ExternalRef)` for ref lookup |
| `model/codec/DesignCodecs.java` | All Mojang Codecs. `DESIGN`, `NODE`, `FACTORY`, `FACTORY_STORE`. format_version = 1 |

### Recipe / persistence / solver
| File | Purpose |
|---|---|
| `recipe/RecipeIndex.java` | byId / byType / recipesProducing / recipesConsuming. fastutil multimap-flavor maps |
| `recipe/RecipeEntry.java` | Normalized recipe record |
| `recipe/IngredientStack.java` | Wraps `Ingredient` with count + tag detection. `SizedIngredient` unwrapping (NeoForge wrapper) |
| `recipe/FluidIngredientStack.java` | Placeholder for fluid inputs (Create) |
| `recipe/RecipeNodeFactory.java` | Static factories: `fromRecipeEntry`, `source(Item)`, `output(Item)` |
| `recipe/adapter/RecipeAdapter.java` | Interface |
| `recipe/adapter/RecipeAdapterRegistry.java` | First-match-wins dispatcher. `GenericAdapter` is always last and always matches |
| `recipe/adapter/GenericAdapter.java` | Fallback. Uses `Recipe.getIngredients()` + `getResultItem(registries)` |
| `recipe/adapter/CookingAdapter.java` | Smelting/blasting/smoking/campfire — captures `cookingTime` |
| `recipe/adapter/create/ProcessingRecipeAdapter.java` | Create's `ProcessingRecipe` family. Matches by `"create"` namespace. Captures duration, fluid inputs/outputs |
| `recipe/adapter/create/CreateAdapterBundle.java` | Entry point — only loaded when `ModList.isLoaded("create")` |
| `recipe/source/VanillaRecipeSource.java` | Iterates `RecipeManager.getRecipes()` and feeds adapter registry |
| `recipe/source/RebuildTrigger.java` | Singleton index + lifecycle. Subscribes to `LoggingIn`, `RecipesUpdatedEvent`, `LoggingOut`. Calls `JeiBridge.addToIndex` after vanilla pass |
| `recipe/spike/*` | Phase 1a spike code (left in place for future debugging) |
| `solver/Solver.java` | Kahn's topo sort, forward rate propagation. Two passes: recipes first, then logic nodes (Splitter aggregates outputs → input; Merger distributes output → inputs) |
| `solver/SolverResult.java` | Output record |
| `persistence/FactoryFileStore.java` | Save/load `store.json`. Codec-driven. 5 rotating backups |
| `command/DebugCommands.java` | `/cogwheel debug recipes`, `ways-to-produce <id>`, `recipes-by-type <id>` |

### Integration (soft-loaded)
| File | Purpose |
|---|---|
| `integration/jei/JeiBridge.java` | No-JEI-imports API surface. Stores `IJeiRuntime` as `Object`. `tryRenderPickerRow` is the picker's entry point |
| `integration/jei/CogwheelJeiPlugin.java` | `@JeiPlugin` annotated. `onRuntimeAvailable` extracts brewing + anvil recipes and stashes runtime in JeiBridge |
| `integration/jei/JeiRecipeExtractor.java` | Brewing → RecipeEntry + Anvil → RecipeEntry conversion |
| `integration/jei/JeiPickerRenderer.java` | Reflective lookup of recipe instance + JEI category, scale-to-fit `drawRecipe()` |

### Build config + resources
| File | Purpose |
|---|---|
| `build.gradle` | ModDevGradle. Create + JEI as `compileOnly` + `localRuntime` (slim/transitive=false for Create) |
| `gradle.properties` | `neo_version=21.1.228`, `minecraft_version=1.21.1`, parchment, mod id/name/group/author |
| `settings.gradle` | NeoForge plugin repo |
| `src/main/templates/META-INF/neoforge.mods.toml` | Templated mods.toml. `displayTest="IGNORE_ALL_SERVERS"`. Create + JEI as `type="optional"` |
| `src/main/resources/assets/cogwheel/lang/en_us.json` | All UI strings |
| `LICENSE`, `README.md`, `.gitignore`, `.gitattributes` | Project metadata |

## Save data format

Lives at `<gameDir>/cogwheel/store.json`. 5 rotating backups: `store.json.bak.0` … `bak.4`.

```json
{
  "format_version": 1,
  "current_factory_id": "uuid",
  "factories": [
    {
      "id": "uuid",
      "name": "Default Factory",
      "design": {
        "name": "Default Factory",
        "nodes": [
          { "type": "source",   "id": "...", "position": {"x":60,"y":80}, "item": {...}, "kind": "MANUAL", "external_ref": {"factory_id":"...","output_node_id":"..."} },
          { "type": "recipe",   "id": "...", "position": {...}, "recipe_id": "minecraft:iron_ingot_from_blasting", "title": "...", "icon": {...}, "inputs": [...], "outputs": [...], "parallelism": 1 },
          { "type": "sink",     "id": "...", "position": {...}, "item": {...} },
          { "type": "splitter", "id": "...", "position": {...}, "output_count": 2 },
          { "type": "merger",   "id": "...", "position": {...}, "input_count": 2 },
          { "type": "output",   "id": "...", "position": {...}, "item": {...}, "export_name": "Iron Plates" }
        ],
        "edges": [
          { "from": "uuid", "from_port": 0, "to": "uuid", "to_port": 0, "rate": 0.0 }
        ]
      }
    }
  ]
}
```

Sealed `Node` dispatches on the `"type"` string field. `ItemStack` uses `ItemStack.OPTIONAL_CODEC` (component-preserving + handles empty). Format version is read on load but ignored beyond logging — there are no migrations yet.

## Default keybinds + shortcuts

| Key | Action |
|---|---|
| K | Open / close editor |
| Middle-mouse drag | Pan canvas |
| Scroll | Zoom (anchored to cursor) |
| Left-click on node + drag | Move node |
| Left-drag from output port | Create edge (validates port type + item) |
| Left-click on edge | Select edge (yellow highlight) |
| Right-click on node/edge | Context menu (Delete) |
| Delete / Backspace | Remove selected edge or node |
| Ctrl+S | Save all factories |
| Ctrl+O | Reload from disk |
| **+** button at canvas bottom-right | Toggle left panel |
| × button in panel header | Close that panel |
| ‹ button in left panel header | Back (pops navigation stack) |

## Solver semantics (Phase 6 + 9e + 9f)

Forward-design model. **NOT bottleneck-aware.**

For each `RecipeNode`:
- `cycleTicks = max(recipe.processingTimeTicks, 20)` (treat "instant" recipes as 1 second)
- `cyclesPerMin = (1200 / cycleTicks) × parallelism`
- For each input port [i]: demand = `inputs[i].count * cyclesPerMin`
- For each output port [j]: supply = `outputs[j].count * cyclesPerMin`

Edges' rates are set by the **downstream node's demand** (not the upstream's supply). If an edge has no upstream, it's still set; if it has no downstream, supply goes into `unusedOutputs`.

Second pass in reverse topo order:
- **Splitter**: incoming edge rate = sum of outgoing edge rates
- **Merger**: incoming edge rates = outgoing edge rate / inputCount (equal distribution)

`SinkNode` and `OutputNode` both aggregate incoming rates into `finalOutputs`. `SourceNode` (any kind, including EXTERNAL_FACTORY) is treated as infinite supply — no aggregation.

## What's broken / known-risky / deferred

### Probably needs attention soon
- **Tag-aware edge compatibility.** Currently strict-item match. For Create's `#c:plates/iron` (which includes iron plate, copper plate, etc.), only the first matching item works at the port — others get rejected. Need to add `Optional<TagKey<Item>> acceptedTag` to `Port` and update `EdgeValidation`.
- **Delete factory has no confirmation.** Pre-alpha. Add a "Are you sure?" modal when stakes get higher.
- **Broken ExternalRef isn't visually flagged on the canvas.** PropertiesPanel says "(missing)" but the source node itself looks normal. Should add a warning chip / red border.
- **JEI brewing 1041 → 251 dedup.** JEI's brewing enumeration produces 1041 recipes but they collide on `getUid()` down to 251 unique. Either JEI is reusing UIDs for similar recipes, or our adapter loses something. Not blocking but worth investigating.

### Deferred to a future phase
- **Cross-factory solver propagation.** EXTERNAL_FACTORY source is currently treated as infinite — should resolve to the referenced factory's output rate.
- **Sequenced Assembly** recipes. Multi-stage shape doesn't fit single-entry RecipeEntry cleanly. ProcessingRecipeAdapter has a TODO comment.
- **Create heat condition** (`HeatCondition.SUPERHEATED` etc.). Currently ignored. Design doc suggests rendering as a chip on the node header.
- **Undo / redo.** Architecture supports it cheaply because Design is immutable — just push the old Design onto a stack on each `setDesign()` call.
- **Multi-select.**
- **Copy / paste subgraphs.**
- **Note nodes.**
- **Image export of a design.**
- **Per-output / per-input weighted distribution in Splitter / Merger.**
- **Filter nodes** (1-in, 1-out with item predicate).
- **JEI / EMI bridge for composter + fueling** (deliberately skipped — neither produces an item output).
- **EMI integration.** Same pattern as JEI but separate plugin entry point. Currently a stubbed-out section in build.gradle.

### Things you should NOT do
- Don't remove the spike code in `recipe/spike/` — it's the source of truth for "did our recipe-access assumption stop working?" and is the first thing to run if the index suddenly empties.
- Don't make `JeiBridge` import JEI types. The lazy-load gate (`if (jeiRuntime != null)`) depends on JeiBridge being verifiable without JEI on the classpath.
- Don't switch from the `slim` Create variant for `localRuntime`. The non-slim transitive tree breaks dev.
- Don't reintroduce `EventBusSubscriber.Bus` — it's deprecated and emits warnings.
- Don't render the parent screen behind a modal expecting a translucent dim to cover items. ItemRenderer Z-order beats `GuiGraphics.fill()`.

## How to add things (recipes for common changes)

### Add a new node type
1. New record in `model/`, implement `Node`, add to sealed permits
2. Add codec + dispatch case in `DesignCodecs.NODE`
3. Decide port shape and icon
4. If logic-style (wildcard), update `PortDisplayResolver` to propagate through it
5. Update `Solver` if it has special semantics
6. Add to PropertiesPanel render-switch
7. Add to LeftPanel category (probably Logic or new category)
8. Update lang strings

### Add a new recipe adapter (e.g. for another mod)
1. New class in `recipe/adapter/<mod>/` or `recipe/adapter/`
2. Implement `RecipeAdapter`. `matches()` filters by `RecipeType<?>`, `adapt()` returns `Optional<RecipeEntry>`
3. If mod-specific, gate the registration with `ModList.isLoaded("modid")` in `Cogwheel.registerAdapters()`
4. If mod-specific, put it in its own package and create a "bundle" class that owns the registration — that's the only class that imports mod types
5. Add mod's maven repo + `compileOnly` + (optionally) `localRuntime` for dev testing

### Add a new left-panel page
1. New class implementing `LeftPanelPage`
2. Add to sealed permits
3. Add to `LeftPanel.buildCategoriesPage` OR push from another page
4. If it owns widgets (EditBox etc.), register/unregister via `LeftPanelPage.WidgetHost`

### Bump the format version
- Increment `DesignCodecs.FORMAT_VERSION`
- Update `FACTORY_STORE` codec to handle the old version gracefully (use `Codec.optionalFieldOf` + default values for new fields)
- Document the migration in this handoff

## User preferences (from memory)

| Preference | Details |
|---|---|
| **Handle** | `kima` (NOT `shift` — that's the Windows username). Use in package names, mod authors, GitHub-style attribution |
| **Communication** | Terse, code-aware. Reference files with `file:line` markdown links |
| **Plans** | Doesn't want every file path enumerated — patterns are fine. Prefers scannable plans over exhaustive |

These are stored in `C:\Users\shift\.claude\projects\C--Users-shift-Projects-Cogwheel\memory\`:
- `MEMORY.md` (index)
- `user_handle.md`
- `project_cogwheel.md`

## Useful commands during development

```sh
# Find anything matching a pattern
grep -rn "PATTERN" src/main/java

# Verify a class is in the jar
unzip -l build/libs/cogwheel-0.1.0.jar | grep -i SomeClass

# Quick check of mod's metadata
unzip -p build/libs/cogwheel-0.1.0.jar META-INF/neoforge.mods.toml

# Stop a stuck gradle daemon (rare)
./gradlew --stop
```

In-game debugging:
```
/cogwheel debug recipes                    # logs counts via both API paths
/cogwheel debug ways-to-produce minecraft:iron_ingot
/cogwheel debug recipes-by-type create:mixing
```

## When something breaks

| Symptom | First place to look |
|---|---|
| Mod doesn't load | Check `neoforge.mods.toml` versionRange, check for compile errors |
| RecipeIndex is empty in game | Run `/cogwheel debug recipes` — if connection or level is null, the trigger event didn't fire |
| Create recipes missing | Check `Cogwheel: Create detected` log line. Check Create version is 6.0.10+ |
| JEI bridge not contributing | Look for `Cogwheel: JEI bridge contributed N` log line. If absent, JEI's `onRuntimeAvailable` didn't fire — usually means you haven't joined a world yet |
| Picker rows look wrong | JEI's drawable size mismatch with our 56px row. Increase `ROW_HEIGHT` in `RecipePickerScreen` or improve `JeiPickerRenderer`'s scale logic |
| Item-type mismatch rejecting valid connection | `EdgeValidation` is strict-equality. Tag-aware compat is deferred — see "Known-risky" |
| Saved store.json won't load | Check the log for "failed to load factory store". A `.bak.0` is one save behind |
| Selected node won't delete | Make sure the canvas has focus, not the toolbox search box (key events go to focused widget first) |

## Final state at handoff

- **65 Java files**
- **`cogwheel-0.1.0.jar`** built at `build/libs/cogwheel-0.1.0.jar` (163 KB, MIT, client-only)
- All features from the original design doc Phases 0–9 are implemented except the explicitly deferred polish items above
- User is testing the v0.1.0 jar in their pack and will report back

Good luck.
