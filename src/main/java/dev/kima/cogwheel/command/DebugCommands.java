package dev.kima.cogwheel.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.kima.cogwheel.CogwheelConstants;
import dev.kima.cogwheel.recipe.RecipeEntry;
import dev.kima.cogwheel.recipe.RecipeIndex;
import dev.kima.cogwheel.recipe.source.RebuildTrigger;
import dev.kima.cogwheel.recipe.spike.RecipeAccessSpike;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

import java.util.List;

@EventBusSubscriber(modid = CogwheelConstants.MODID, value = Dist.CLIENT)
public final class DebugCommands {
    private DebugCommands() {}

    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("cogwheel")
                        .then(Commands.literal("debug")
                                .then(Commands.literal("recipes")
                                        .executes(DebugCommands::runRecipeSpike))
                                .then(Commands.literal("ways-to-produce")
                                        .then(Commands.argument("item_id", StringArgumentType.greedyString())
                                                .executes(DebugCommands::runWaysToProduce)))
                                .then(Commands.literal("recipes-by-type")
                                        .then(Commands.argument("type_id", StringArgumentType.greedyString())
                                                .executes(DebugCommands::runRecipesByType)))));
    }

    private static int runRecipeSpike(CommandContext<CommandSourceStack> ctx) {
        RecipeAccessSpike.probe("command");
        ctx.getSource().sendSystemMessage(Component.literal(
                "Cogwheel: recipe-access spike fired — see game log for counts."));
        return Command.SINGLE_SUCCESS;
    }

    private static int runWaysToProduce(CommandContext<CommandSourceStack> ctx) {
        String raw = StringArgumentType.getString(ctx, "item_id");
        ResourceLocation id = ResourceLocation.tryParse(raw);
        if (id == null) {
            ctx.getSource().sendFailure(Component.literal("Not a valid resource location: " + raw));
            return 0;
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            ctx.getSource().sendFailure(Component.literal("Unknown item: " + id));
            return 0;
        }

        RebuildTrigger.ensureBuilt();
        RecipeIndex index = RebuildTrigger.index();
        List<RecipeEntry> ways = index.waysToProduce(item);
        CogwheelConstants.LOG.info("ways-to-produce {} = {} entries", id, ways.size());
        for (RecipeEntry entry : ways) {
            CogwheelConstants.LOG.info("  {} ({})", entry.id(), entry.typeId());
        }
        ctx.getSource().sendSystemMessage(Component.literal(
                "Cogwheel: " + ways.size() + " way(s) to produce " + id + " — see log for ids."));
        return Command.SINGLE_SUCCESS;
    }

    private static int runRecipesByType(CommandContext<CommandSourceStack> ctx) {
        String raw = StringArgumentType.getString(ctx, "type_id");
        ResourceLocation id = ResourceLocation.tryParse(raw);
        if (id == null) {
            ctx.getSource().sendFailure(Component.literal("Not a valid resource location: " + raw));
            return 0;
        }

        RebuildTrigger.ensureBuilt();
        RecipeIndex index = RebuildTrigger.index();
        List<RecipeEntry> entries = index.byType(id);
        CogwheelConstants.LOG.info("recipes-by-type {} = {} entries", id, entries.size());
        int shown = 0;
        for (RecipeEntry entry : entries) {
            CogwheelConstants.LOG.info("  {} -> {} inputs, {} outputs, {} ticks",
                    entry.id(), entry.inputs().size(), entry.outputs().size(), entry.processingTimeTicks());
            if (++shown >= 20) {
                CogwheelConstants.LOG.info("  ... ({} more)", entries.size() - shown);
                break;
            }
        }
        ctx.getSource().sendSystemMessage(Component.literal(
                "Cogwheel: " + entries.size() + " recipe(s) of type " + id + " — see log for details."));
        return Command.SINGLE_SUCCESS;
    }
}
