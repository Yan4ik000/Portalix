package dev.portalix.client.render;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class ParticleVisibility {
  private static final double RENDER_DISTANCE = 128.0;

  private ParticleVisibility() {}

  public static AABB ropeBounds(Vec3 start, Vec3 auxiliary, Vec3 end) {
    return new AABB(start, end).minmax(new AABB(auxiliary, auxiliary)).inflate(0.32);
  }

  public static boolean inRange(AABB bounds, Vec3 camera) {
    double x = Math.max(bounds.minX - camera.x, Math.max(0.0, camera.x - bounds.maxX));
    double y = Math.max(bounds.minY - camera.y, Math.max(0.0, camera.y - bounds.maxY));
    double z = Math.max(bounds.minZ - camera.z, Math.max(0.0, camera.z - bounds.maxZ));
    return x * x + y * y + z * z <= RENDER_DISTANCE * RENDER_DISTANCE;
  }

  public static boolean visible(AABB bounds, ParticleView view) {
    return inRange(bounds, view.position()) && view.frustum().test(bounds);
  }
}
