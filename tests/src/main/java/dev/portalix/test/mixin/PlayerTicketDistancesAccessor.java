package dev.portalix.test.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "net.minecraft.server.level.DistanceManager$FixedPlayerDistanceChunkTracker")
public interface PlayerTicketDistancesAccessor {
  @Invoker("getLevel")
  int portalix$distance(long chunk);
}
