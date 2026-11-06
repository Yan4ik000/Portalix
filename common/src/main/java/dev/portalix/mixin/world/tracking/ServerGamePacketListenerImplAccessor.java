package dev.portalix.mixin.world.tracking;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerGamePacketListenerImpl.class)
public interface ServerGamePacketListenerImplAccessor {
  @Accessor("awaitingPositionFromClient")
  Vec3 portalix$awaitingTeleport();

  @Accessor("awaitingTeleport")
  int portalix$teleportId();

  @Accessor("lastVehicle")
  void portalix$lastVehicle(Entity vehicle);

  @Accessor("vehicleFirstGoodX")
  void portalix$vehicleFirstX(double value);

  @Accessor("vehicleFirstGoodY")
  void portalix$vehicleFirstY(double value);

  @Accessor("vehicleFirstGoodZ")
  void portalix$vehicleFirstZ(double value);

  @Accessor("vehicleLastGoodX")
  void portalix$vehicleLastX(double value);

  @Accessor("vehicleLastGoodY")
  void portalix$vehicleLastY(double value);

  @Accessor("vehicleLastGoodZ")
  void portalix$vehicleLastZ(double value);
}
