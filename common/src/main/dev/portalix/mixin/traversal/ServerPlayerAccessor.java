package dev.portalix.mixin.traversal;

import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ServerPlayer.class)
public interface ServerPlayerAccessor {
  @Invoker("checkRidingStatistics")
  void portalix$ridingStatistics(double x, double y, double z);
}
