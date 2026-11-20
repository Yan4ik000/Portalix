package dev.portalix.mixin.world.tracking;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ServerGamePacketListenerImpl.class)
public interface ServerGamePacketListenerImplAccessor {
  @Invoker("handlePlayerKnownMovement")
  void portalix$recordKnownMovement(Vec3 movement);

  @Accessor("awaitingPositionFromClient")
  Vec3 portalix$awaitingTeleport();

  @Accessor("lastVehicle")
  void portalix$lastVehicle(Entity vehicle);

  @Accessor("firstGoodX")
  void portalix$playerFirstX(double value);

  @Accessor("firstGoodY")
  void portalix$playerFirstY(double value);

  @Accessor("firstGoodZ")
  void portalix$playerFirstZ(double value);

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
