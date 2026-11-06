package dev.portalix.client.portal.entity;

import dev.portalix.client.mixin.entity.EntityRendererAccessor;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public record PortalEntityShadows(float radius, List<EntityRenderState.ShadowPiece> pieces) {
  public static PortalEntityShadows extract(
      EntityRendererAccessor renderer,
      EntityRenderState state,
      Level level,
      Vec3 position,
      Vec3 camera) {
    double x = state.x, y = state.y, z = state.z, distance = state.distanceToCameraSq;
    float radius = state.shadowRadius;
    var pieces = List.copyOf(state.shadowPieces);
    try {
      state.x = position.x;
      state.y = position.y;
      state.z = position.z;
      state.distanceToCameraSq = position.distanceToSqr(camera);
      renderer.portalix$extractShadow(state, Minecraft.getInstance(), level);
      return new PortalEntityShadows(state.shadowRadius, List.copyOf(state.shadowPieces));
    } finally {
      state.x = x;
      state.y = y;
      state.z = z;
      state.distanceToCameraSq = distance;
      state.shadowRadius = radius;
      state.shadowPieces.clear();
      state.shadowPieces.addAll(pieces);
    }
  }
}
