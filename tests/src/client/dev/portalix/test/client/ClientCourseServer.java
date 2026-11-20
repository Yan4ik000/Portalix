package dev.portalix.test.client;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.block.Blocks;

public final class ClientCourseServer {

  private ClientCourseServer() {}

  public static void prepare(MinecraftServer server) {
    if (!ClientRegressionRunner.active()) return;
    var level = server.overworld();
    for (var position : BlockPos.betweenClosed(new BlockPos(-3, 99, 2), new BlockPos(3, 99, 8)))
      level.setBlockAndUpdate(position, Blocks.QUARTZ_BLOCK.defaultBlockState());
    level.getGameRules().set(GameRules.RESPAWN_RADIUS, 0, server);
    level.getGameRules().set(GameRules.ADVANCE_TIME, false, server);
    level.getGameRules().set(GameRules.ADVANCE_WEATHER, false, server);
    level.getGameRules().set(GameRules.SPAWN_MOBS, false, server);
    var daylight = level.registryAccess().lookupOrThrow(Registries.WORLD_CLOCK).getOrThrow(WorldClocks.OVERWORLD);
    level.clockManager().setTotalTicks(daylight, 6000);
    server.setRespawnData(LevelData.RespawnData.of(Level.OVERWORLD,
        new BlockPos(0, 100, 5), 180, 0));
  }

}
