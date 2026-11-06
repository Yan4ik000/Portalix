package dev.portalix.mixin.traversal;

import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Player.class)
public interface PlayerAccessor {
  @Invoker("updatePlayerPose")
  void portalix$updatePose();

  @Invoker("maybeBackOffFromEdge")
  Vec3 portalix$backOffFromEdge(Vec3 movement, MoverType mover);

  @Invoker("isStayingOnGroundSurface")
  boolean portalix$stayingOnGroundSurface();

  @Invoker("isAboveGround")
  boolean portalix$aboveGround(float depth);
}
