package dev.portalix.test.mixin;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerGamePacketListenerImpl.class)
public interface ServerGamePacketListenerImplAccessor {
  @Accessor("awaitingTeleport")
  int portalix$teleportId();

  @Accessor("awaitingPositionFromClient")
  Vec3 portalix$teleportPosition();
}
