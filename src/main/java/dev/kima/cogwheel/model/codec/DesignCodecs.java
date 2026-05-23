package dev.kima.cogwheel.model.codec;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.kima.cogwheel.model.Design;
import dev.kima.cogwheel.model.Edge;
import dev.kima.cogwheel.model.Node;
import dev.kima.cogwheel.model.Port;
import dev.kima.cogwheel.model.PortType;
import dev.kima.cogwheel.model.RecipeNode;
import dev.kima.cogwheel.model.SinkNode;
import dev.kima.cogwheel.model.SourceNode;
import dev.kima.cogwheel.model.Vec2;
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

    public static final MapCodec<SourceNode> SOURCE_NODE = RecordCodecBuilder.mapCodec(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(SourceNode::id),
            VEC2.fieldOf("position").forGetter(SourceNode::position),
            ITEM_STACK.fieldOf("item").forGetter(SourceNode::item),
            SOURCE_KIND.fieldOf("kind").forGetter(SourceNode::kind)
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

    public static final Codec<Node> NODE = Codec.STRING.dispatch(
            "type",
            DesignCodecs::nodeTypeName,
            DesignCodecs::codecForNodeType);

    public static final Codec<Edge> EDGE = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("from").forGetter(Edge::from),
            Codec.INT.fieldOf("from_port").forGetter(Edge::fromPort),
            UUIDUtil.CODEC.fieldOf("to").forGetter(Edge::to),
            Codec.INT.fieldOf("to_port").forGetter(Edge::toPort),
            Codec.DOUBLE.optionalFieldOf("rate", 0.0).forGetter(Edge::rate)
    ).apply(i, Edge::new));

    public static final Codec<Design> DESIGN = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("format_version", FORMAT_VERSION).forGetter(d -> FORMAT_VERSION),
            Codec.STRING.fieldOf("name").forGetter(Design::name),
            NODE.listOf().fieldOf("nodes").forGetter(Design::nodes),
            EDGE.listOf().fieldOf("edges").forGetter(Design::edges)
    ).apply(i, (version, name, nodes, edges) -> new Design(name, nodes, edges)));

    private static String nodeTypeName(Node node) {
        if (node instanceof SourceNode) return "source";
        if (node instanceof RecipeNode) return "recipe";
        if (node instanceof SinkNode) return "sink";
        throw new IllegalStateException("Unknown node type: " + node.getClass().getName());
    }

    private static MapCodec<? extends Node> codecForNodeType(String name) {
        return switch (name) {
            case "source" -> SOURCE_NODE;
            case "recipe" -> RECIPE_NODE;
            case "sink"   -> SINK_NODE;
            default       -> throw new IllegalArgumentException("Unknown node type: " + name);
        };
    }
}
