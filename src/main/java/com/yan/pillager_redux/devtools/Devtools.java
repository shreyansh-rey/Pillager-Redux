package com.yan.pillager_redux.devtools;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.yan.pillager_redux.main.Mod;
import com.yan.pillager_redux.main.WorldData;
import com.yan.pillager_redux.setup.PillagerRedux;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Objects;

@EventBusSubscriber(modid=PillagerRedux.MODID)
public class Devtools {
    @SubscribeEvent
    public static void onRegisterCommand(RegisterCommandsEvent event){
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("PillagerRedux")
                .then(Commands.argument("Spawn", StringArgumentType.string())
                        .then(Commands.argument("Outpost", StringArgumentType.string())
                                .executes(
                                        context -> {
                                            CommandSourceStack source = context.getSource();

                                            source.sendSuccess(
                                                    () -> Component.literal("Spawning Pillager Outpost!"),
                                                    false
                                            );

                                            BlockPos playerPos = Objects.requireNonNull(source.getPlayer()).getBlockPosBelowThatAffectsMyMovement();
                                            ServerLevel serverLevel = source.getLevel();

                                            if (!Mod.spawnPillagerOutpost(serverLevel, playerPos)) return 0;

                                            return 1;
                                        })
                        )));

        dispatcher.register(Commands.literal("PillagerRedux")
                .then(Commands.argument("Spawn", StringArgumentType.string())
                        .then(Commands.argument("PillagerWave", StringArgumentType.string())
                                .executes(
                                        context -> {
                                            CommandSourceStack source = context.getSource();

                                            source.sendSuccess(
                                                    () -> Component.literal("Spawning Pillager Wave"),
                                                    false
                                            );

                                            BlockPos playerPos = Objects.requireNonNull(source.getPlayer()).getBlockPosBelowThatAffectsMyMovement();
                                            ServerLevel level = source.getLevel();

                                            if (Mod.spawnWave(level, WorldData.World.getChunkPosFromBlockPos(playerPos), 5).isEmpty()) Debug.LogWarning("Failed wave spawn");

                                            return 1;
                                        })
                        )));

        dispatcher.register(Commands.literal("PillagerRedux")
                .then(Commands.literal("RebuildTest")
                        .executes(context -> runRebuildTest(context.getSource(), 30))
                        .then(Commands.argument("percent", IntegerArgumentType.integer(0, 100))
                                .executes(context -> runRebuildTest(
                                        context.getSource(), IntegerArgumentType.getInteger(context, "percent"))))
                ));
    }

    private static int runRebuildTest(CommandSourceStack source, int wreckPercent) {
        BlockPos playerPos = Objects.requireNonNull(source.getPlayer()).blockPosition();
        ServerLevel level = source.getLevel();

        boolean ok = Mod.testRebuild(level, playerPos, wreckPercent,
                msg -> source.sendSuccess(() -> Component.literal(msg), false));

        return ok ? 1 : 0;
    }
}