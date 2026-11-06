package dev.portalix.mixin.interaction;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.portalix.portal.traversal.PortalPassagePose;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntitySection.class)
abstract class PortalEntityQueryBoundsMixin {
  @WrapOperation(
      method = {
        "getEntities(Lnet/minecraft/world/phys/AABB;Lnet/minecraft/util/AbortableIterationConsumer;)Lnet/minecraft/util/AbortableIterationConsumer$Continuation;",
        "getEntities(Lnet/minecraft/world/level/entity/EntityTypeTest;Lnet/minecraft/world/phys/AABB;Lnet/minecraft/util/AbortableIterationConsumer;)Lnet/minecraft/util/AbortableIterationConsumer$Continuation;"
      },
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/entity/EntityAccess;getBoundingBox()Lnet/minecraft/world/phys/AABB;"),
      require = 2,
      allow = 2)
  private AABB portalix$queryBounds(EntityAccess access, Operation<AABB> original) {
    AABB bounds = original.call(access);
    return access instanceof Entity entity
        ? PortalPassagePose.interactionBounds(entity, bounds)
        : bounds;
  }
}
