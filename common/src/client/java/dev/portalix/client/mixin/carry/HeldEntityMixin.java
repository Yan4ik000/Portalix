package dev.portalix.client.mixin.carry;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.portalix.client.PortalixClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class HeldEntityMixin {
  @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
  private void portalix$visible(
      Entity entity,
      Frustum frustum,
      double x,
      double y,
      double z,
      CallbackInfoReturnable<Boolean> result) {
    if (PortalixClient.carryController().predicted(entity)) {
      result.setReturnValue(
          frustum.isVisible(PortalixClient.carryController().predictedBounds(entity)));
    }
  }

  @Inject(method = "extractRenderState", at = @At("TAIL"))
  private void portalix$carry(
      Entity entity, EntityRenderState state, float partial, CallbackInfo ci) {
    Vec3 center = PortalixClient.carryController().renderCenter(entity, partial);
    if (center != null) {
      state.x = center.x;
      state.y = center.y - entity.getBbHeight() * 0.5;
      state.z = center.z;
    }
  }

  @ModifyExpressionValue(
      method =
          "extractNameTags(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;FDD)V",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;distanceToSqr(Lnet/minecraft/world/entity/Entity;)D"))
  private double portalix$predictedDistance(
      double original, Entity entity, EntityRenderState state, float partial) {
    Minecraft mc = Minecraft.getInstance();
    if (!PortalixClient.carryController().predicted(entity)) {
      return original;
    }

    Vec3 center = PortalixClient.carryController().renderCenter(entity, partial);
    return center == null
        ? original
        : mc.gameRenderer
            .mainCamera()
            .position()
            .distanceToSqr(center.add(0.0, -entity.getBbHeight() * 0.5, 0.0));
  }
}
