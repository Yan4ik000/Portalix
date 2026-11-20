package dev.portalix.client.compat.minecraft;

import dev.portalix.client.mixin.scene.BlockEntityRenderDispatcherAccessor;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class SceneDispatcherScope implements AutoCloseable {
  private final EntityRenderDispatcher entities;
  private final BlockEntityRenderDispatcher blockEntities;
  private final Camera camera;
  private final Entity crosshair;
  private final Vec3 blockCameraPosition;

  public SceneDispatcherScope(LevelRenderer renderer) {
    entities = renderer.entityRenderDispatcher();
    blockEntities = renderer.blockEntityRenderDispatcher();
    camera = entities.camera;
    crosshair = entities.crosshairPickEntity;
    blockCameraPosition =
        ((BlockEntityRenderDispatcherAccessor) blockEntities).portalix$cameraPosition();
  }

  @Override
  public void close() {
    entities.prepare(camera, crosshair);
    blockEntities.prepare(blockCameraPosition);
  }
}
