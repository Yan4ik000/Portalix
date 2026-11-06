package dev.portalix.client.mixin.entity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(EntityRenderer.class)
public interface EntityRendererAccessor {
  @Invoker("extractShadow")
  void portalix$extractShadow(EntityRenderState state, Minecraft client, Level level);

  @Invoker("getBoundingBoxForCulling")
  AABB portalix$renderBounds(Entity entity);

  @Invoker("getBlockLightLevel")
  int portalix$blockLight(Entity entity, BlockPos position);

  @Invoker("getSkyLightLevel")
  int portalix$skyLight(Entity entity, BlockPos position);
}
