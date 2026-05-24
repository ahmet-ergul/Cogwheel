package dev.kima.cogwheel.model.codec;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.kima.cogwheel.model.Design;
import dev.kima.cogwheel.model.ClusterNode;
import dev.kima.cogwheel.model.Edge;
import dev.kima.cogwheel.model.ExternalRef;
import dev.kima.cogwheel.model.Factory;
import dev.kima.cogwheel.model.VoidNode;
import dev.kima.cogwheel.model.LimiterNode;
import dev.kima.cogwheel.model.LoopNode;
import dev.kima.cogwheel.model.MergerNode;
import dev.kima.cogwheel.model.Node;
import dev.kima.cogwheel.model.OutputNode;
import dev.kima.cogwheel.model.Port;
import dev.kima.cogwheel.model.PortType;
import dev.kima.cogwheel.model.RecipeNode;
import dev.kima.cogwheel.model.SinkNode;
import dev.kima.cogwheel.model.SourceNode;
import dev.kima.cogwheel.model.SplitterNode;
import dev.kima.cogwheel.model.Vec2;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Codecs for the model layer. Designs serialize to JSON (or any {@code DynamicOps} target) via
 * Mojang's Codec system.
 *
 * <p>Sealed {@link Node} hierarchy dispatches on a {@code "type"} string field. Empty
 * {@link ItemStack}s round-trip through {@link ItemStack#OPTIONAL_CODEC} (component-preserving).
 * Component titles serialize as plain strings — we don't store formatted titles yet, so this loses
 * nothing and produces cleaner JSON.
 *
 * <p>{@code formatVersion} on {@link Design} is reserved for future migrations; readers ignore the
 * value today but warn if it's a future version they don't understand.
 */
public final class DesignCodecs {
    public static final int FORMAT_VERSION = 1;

    private DesignCodecs() {}

    /** Round-trips empty as empty rather than throwing — Port.display and RecipeNode.icon can be empty. */
    private static final Codec<ItemStack> ITEM_STACK = ItemStack.OPTIONAL_CODEC;

    /** Display titles are plain strings today — see class doc. */
    private static final Codec<Component> SIMPLE_TITLE = Codec.STRING.xmap(
            Component::literal,
            Component::getString);

    public static final Codec<Vec2> VEC2 = RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.fieldOf("x").forGetter(Vec2::x),
            Codec.DOUBLE.fieldOf("y").forGetter(Vec2::y)
    ).apply(i, Vec2::new));

    public static final Codec<PortType> PORT_TYPE = Codec.STRING.xmap(
            PortType::valueOf,
            PortType::name);

    public static final Codec<Port> PORT = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("index").forGetter(Port::index),
            PORT_TYPE.fieldOf("type").forGetter(Port::type),
            Codec.STRING.fieldOf("label").forGetter(Port::label),
            ITEM_STACK.fieldOf("display").forGetter(Port::display)
    ).apply(i, Port::new));

    public static final Codec<SourceNode.Kind> SOURCE_KIND = Codec.STRING.xmap(
            SourceNode.Kind::valueOf,
            SourceNode.Kind::name);

    public static final Codec<ExternalRef> EXTERNAL_REF = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("factory_id").forGetter(ExternalRef::factoryId),
            UUIDUtil.CODEC.fieldOf("output_node_id").forGetter(ExternalRef::outputNodeId)
    ).apply(i, ExternalRef::new));

    public static final MapCodec<SourceNode> SOURCE_NODE = RecordCodecBuilder.mapCodec(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(SourceNode::id),
            VEC2.fieldOf("position").forGetter(SourceNode::position),
            ITEM_STACK.fieldOf("item").forGetter(SourceNode::item),
            SOURCE_KIND.fieldOf("kind").forGetter(SourceNode::kind),
            EXTERNAL_REF.optionalFieldOf("external_ref").forGetter(SourceNode::externalRef)
    ).apply(i, SourceNode::new));

    public static final MapCodec<RecipeNode> RECIPE_NODE = RecordCodecBuilder.mapCodec(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(RecipeNode::id),
            VEC2.fieldOf("position").forGetter(RecipeNode::position),
            ResourceLocation.CODEC.fieldOf("recipe_id").forGetter(RecipeNode::recipeId),
            SIMPLE_TITLE.fieldOf("title").forGetter(RecipeNode::title),
            ITEM_STACK.fieldOf("icon").forGetter(RecipeNode::icon),
            PORT.listOf().fieldOf("inputs").forGetter(RecipeNode::inputs),
            PORT.listOf().fieldOf("outputs").forGetter(RecipeNode::outputs),
            Codec.INT.fieldOf("parallelism").forGetter(RecipeNode::parallelism)
    ).apply(i, RecipeNode::new));

    public static final MapCodec<SinkNode> SINK_NODE = RecordCodecBuilder.mapCodec(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(SinkNode::id),
            VEC2.fieldOf("position").forGetter(SinkNode::position),
            ITEM_STACK.fieldOf("item").forGetter(SinkNode::item)
    ).apply(i, SinkNode::new));

    public static final Codec<SplitterNode.Mode> SPLITTER_MODE = Codec.STRING.xmap(
            SplitterNode.Mode::valueOf,
            SplitterNode.Mode::name);

    public static final MapCodec<SplitterNode> SPLITTER_NODE = RecordCodecBuilder.mapCodec(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(SplitterNode::id),
            VEC2.fieldOf("position").forGetter(SplitterNode::position),
            Codec.INT.fieldOf("output_count").forGetter(SplitterNode::outputCount),
            SPLITTER_MODE.optionalFieldOf("mode", SplitterNode.Mode.DEMAND).forGetter(SplitterNode::mode)
    ).apply(i, SplitterNode::new));

    public static final MapCodec<MergerNode> MERGER_NODE = RecordCodecBuilder.mapCodec(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(MergerNode::id),
            VEC2.fieldOf("position").forGetter(MergerNode::position),
            Codec.INT.fieldOf("input_count").forGetter(MergerNode::inputCount)
    ).apply(i, MergerNode::new));

    public static final MapCodec<OutputNode> OUTPUT_NODE = RecordCodecBuilder.mapCodec(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(OutputNode::id),
            VEC2.fieldOf("position").forGetter(OutputNode::position),
            ITEM_STACK.fieldOf("item").forGetter(OutputNode::item),
            Codec.STRING.fieldOf("export_name").forGetter(OutputNode::exportName)
    ).apply(i, OutputNode::new));

    public static final MapCodec<LimiterNode> LIMITER_NODE = RecordCodecBuilder.mapCodec(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(LimiterNode::id),
            VEC2.fieldOf("position").forGetter(LimiterNode::position),
            Codec.DOUBLE.fieldOf("limit_per_min").forGetter(LimiterNode::limitPerMin)
    ).apply(i, LimiterNode::new));

    public static final MapCodec<VoidNode> VOID_NODE = RecordCodecBuilder.mapCodec(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(VoidNode::id),
            VEC2.fieldOf("position").forGetter(VoidNode::position),
            Codec.INT.fieldOf("input_count").forGetter(VoidNode::inputCount)
    ).apply(i, VoidNode::new));

    public static final MapCodec<LoopNode> LOOP_NODE = RecordCodecBuilder.mapCodec(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(LoopNode::id),
            VEC2.fieldOf("position").forGetter(LoopNode::position),
            Codec.INT.fieldOf("loop_count").forGetter(LoopNode::loopCount)
    ).apply(i, LoopNode::new));

    public static final Codec<ClusterNode.Kind> CLUSTER_KIND = Codec.STRING.xmap(
            ClusterNode.Kind::valueOf, ClusterNode.Kind::name);

    // CLUSTER_NODE is recursive — it contains a Design, which contains a list of Nodes, one of
    // which can be another ClusterNode. Lazy init breaks the cycle: DESIGN is defined further down
    // and gets bound by the JVM before encode/decode actually runs.
    public static final MapCodec<ClusterNode> CLUSTER_NODE = RecordCodecBuilder.mapCodec(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(ClusterNode::id),
            VEC2.fieldOf("position").forGetter(ClusterNode::position),
            Codec.STRING.fieldOf("label").forGetter(ClusterNode::label),
            Codec.lazyInitialized(DesignCodecs::designCodec).fieldOf("inner_design").forGetter(ClusterNode::innerDesign),
            CLUSTER_KIND.fieldOf("kind").forGetter(ClusterNode::kind),
            Codec.INT.optionalFieldOf("parallelism", 1).forGetter(ClusterNode::parallelism)
    ).apply(i, ClusterNode::new));

    public static final Codec<Node> NODE = Codec.STRING.dispatch(
            "type",
            DesignCodecs::nodeTypeName,
            DesignCodecs::codecForNodeType);

    public static final Codec<Edge> EDGE = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("from").forGetter(Edge::from),
            Codec.INT.fieldOf("from_port").forGetter(Edge::fromPort),
            UUIDUtil.CODEC.fieldOf("to").forGetter(Edge::to),
            Codec.INT.fieldOf("to_port").forGetter(Edge::toPort),
            Codec.DOUBLE.optionalFieldOf("rate", 0.0).forGetter(Edge::rate),
            Codec.BOOL.optionalFieldOf("from_bottom", false).forGetter(Edge::fromBottom),
            Codec.BOOL.optionalFieldOf("to_bottom", false).forGetter(Edge::toBottom)
    ).apply(i, Edge::new));

    public static final Codec<Design> DESIGN = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("format_version", FORMAT_VERSION).forGetter(d -> FORMAT_VERSION),
            Codec.STRING.fieldOf("name").forGetter(Design::name),
            NODE.listOf().fieldOf("nodes").forGetter(Design::nodes),
            EDGE.listOf().fieldOf("edges").forGetter(Design::edges)
    ).apply(i, (version, name, nodes, edges) -> new Design(name, nodes, edges)));

    /** Indirection so {@link #CLUSTER_NODE} can reference {@link #DESIGN} via a method ref —
     *  static field forward references are illegal in Java init order, but method refs aren't. */
    private static Codec<dev.kima.cogwheel.model.Design> designCodec() {
        return DESIGN;
    }

    private static String nodeTypeName(Node node) {
        if (node instanceof SourceNode) return "source";
        if (node instanceof RecipeNode) return "recipe";
        if (node instanceof SinkNode) return "sink";
        if (node instanceof SplitterNode) return "splitter";
        if (node instanceof MergerNode) return "merger";
        if (node instanceof OutputNode) return "output";
        if (node instanceof LimiterNode) return "limiter";
        if (node instanceof VoidNode) return "void";
        if (node instanceof ClusterNode) return "cluster";
        if (node instanceof LoopNode) return "loop";
        throw new IllegalStateException("Unknown node type: " + node.getClass().getName());
    }

    private static MapCodec<? extends Node> codecForNodeType(String name) {
        return switch (name) {
            case "source"   -> SOURCE_NODE;
            case "recipe"   -> RECIPE_NODE;
            case "sink"     -> SINK_NODE;
            case "splitter" -> SPLITTER_NODE;
            case "merger"   -> MERGER_NODE;
            case "output"   -> OUTPUT_NODE;
            case "limiter"  -> LIMITER_NODE;
            case "void"     -> VOID_NODE;
            case "cluster"  -> CLUSTER_NODE;
            case "loop"     -> LOOP_NODE;
            default         -> throw new IllegalArgumentException("Unknown node type: " + name);
        };
    }

    // ─── Multi-factory store envelope ──────────────────────────────────────────

    public static final Codec<Factory> FACTORY = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(Factory::id),
            Codec.STRING.fieldOf("name").forGetter(Factory::name),
            DESIGN.fieldOf("design").forGetter(Factory::design)
    ).apply(i, Factory::new));

    /** Persisted shape of {@code FactoryStore} — saved to {@code <gameDir>/cogwheel/store.json}. */
    public record FactoryStoreData(int formatVersion, Optional<UUID> currentFactoryId, List<Factory> factories) {}

    public static final Codec<FactoryStoreData> FACTORY_STORE = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("format_version", FORMAT_VERSION).forGetter(FactoryStoreData::formatVersion),
            UUIDUtil.CODEC.optionalFieldOf("current_factory_id").forGetter(FactoryStoreData::currentFactoryId),
            FACTORY.listOf().fieldOf("factories").forGetter(FactoryStoreData::factories)
    ).apply(i, FactoryStoreData::new));
}
