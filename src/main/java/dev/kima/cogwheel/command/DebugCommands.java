package dev.kima.cogwheel.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.kima.cogwheel.CogwheelConstants;
import dev.kima.cogwheel.recipe.IngredientStack;
import dev.kima.cogwheel.recipe.RecipeEntry;
import dev.kima.cogwheel.recipe.RecipeIndex;
import dev.kima.cogwheel.recipe.adapter.RecipeAdapterRegistry;
import dev.kima.cogwheel.recipe.source.RebuildTrigger;
import dev.kima.cogwheel.recipe.spike.RecipeAccessSpike;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

import java.util.List;
import java.util.Optional;

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
                                                .executes(DebugCommands::runRecipesByType)))
                                .then(Commands.literal("recipe")
                                        .then(Commands.argument("recipe_id", StringArgumentType.greedyString())
                                                .executes(DebugCommands::runDiagnoseRecipe)))));
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

    private static int runDiagnoseRecipe(CommandContext<CommandSourceStack> ctx) {
        String raw = StringArgumentType.getString(ctx, "recipe_id");
        ResourceLocation id = ResourceLocation.tryParse(raw);
        if (id == null) {
            ctx.getSource().sendFailure(Component.literal("Not a valid resource location: " + raw));
            return 0;
        }

        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            ctx.getSource().sendFailure(Component.literal("No client level — run from inside a world."));
            return 0;
        }

        Optional<RecipeHolder<?>> holderOpt = level.getRecipeManager().byKey(id);
        if (holderOpt.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("No recipe in client RecipeManager with id: " + id));
            return 0;
        }
        RecipeHolder<?> holder = holderOpt.get();
        Class<?> recipeClass = holder.value().getClass();
        ResourceLocation typeId = BuiltInRegistries.RECIPE_TYPE.getKey(holder.value().getType());

        ctx.getSource().sendSystemMessage(Component.literal(
                "Recipe " + id + " — type=" + typeId + ", class=" + recipeClass.getName()));
        CogwheelConstants.LOG.info("Diagnose recipe {} (type={}, class={})", id, typeId, recipeClass.getName());

        List<RecipeAdapterRegistry.DiagnoseAttempt> attempts =
                RecipeAdapterRegistry.get().diagnose(holder, level.registryAccess());
        for (RecipeAdapterRegistry.DiagnoseAttempt a : attempts) {
            String outcome;
            if (!a.matchedType()) {
                outcome = "skipped (RecipeType not matched)";
            } else if (a.error() != null) {
                outcome = "THREW " + a.error().getClass().getSimpleName() + ": " + a.error().getMessage();
            } else if (a.result().isEmpty()) {
                outcome = "returned Optional.empty()";
            } else {
                outcome = "→ " + summarize(a.result().get());
            }
            String line = "  " + a.adapterName() + ": " + outcome;
            ctx.getSource().sendSystemMessage(Component.literal(line));
            CogwheelConstants.LOG.info(line);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static String summarize(RecipeEntry entry) {
        StringBuilder sb = new StringBuilder();
        sb.append("inputs=[");
        for (int i = 0; i < entry.inputs().size(); i++) {
            if (i > 0) sb.append(", ");
            IngredientStack in = entry.inputs().get(i);
            sb.append(in.count()).append("×");
            if (in.tag().isPresent()) {
                sb.append("#").append(in.tag().get().location());
            } else if (!in.matchingItems().isEmpty()) {
                sb.append(BuiltInRegistries.ITEM.getKey(in.matchingItems().get(0)));
                if (in.matchingItems().size() > 1) {
                    sb.append("(+").append(in.matchingItems().size() - 1).append(" alts)");
                }
            } else {
                sb.append("?");
            }
        }
        sb.append("], outputs=[");
        for (int i = 0; i < entry.outputs().size(); i++) {
            if (i > 0) sb.append(", ");
            ItemStack out = entry.outputs().get(i);
            sb.append(out.getCount()).append("×").append(BuiltInRegistries.ITEM.getKey(out.getItem()));
        }
        sb.append("]");
        if (entry.inputFluid().isPresent()) sb.append(", inputFluid=yes");
        if (entry.outputFluid().isPresent()) sb.append(", outputFluid=yes");
        if (entry.processingTimeTicks() > 0) sb.append(", ").append(entry.processingTimeTicks()).append("t");
        return sb.toString();
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
