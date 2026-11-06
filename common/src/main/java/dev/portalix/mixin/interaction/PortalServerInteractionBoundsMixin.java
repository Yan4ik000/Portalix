package dev.portalix.mixin.interaction;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.portalix.portal.traversal.PortalPassagePose;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerGamePacketListenerImpl.class)
abstract class PortalServerInteractionBoundsMixin {
  @WrapOperation(
      method = {
        "handleAttack(Lnet/minecraft/network/protocol/game/ServerboundAttackPacket;)V",
        "handleInteract(Lnet/minecraft/network/protocol/game/ServerboundInteractPacket;)V",
        "handleSpectatorAction(Lnet/minecraft/network/protocol/game/ServerboundSpectatorActionPacket;)V"
      },
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/entity/Entity;getBoundingBox()Lnet/minecraft/world/phys/AABB;"),
      require = 3,
      allow = 3)
  private AABB portalix$targetBounds(Entity entity, Operation<AABB> original) {
    return PortalPassagePose.interactionBounds(entity, original.call(entity));
  }
}
