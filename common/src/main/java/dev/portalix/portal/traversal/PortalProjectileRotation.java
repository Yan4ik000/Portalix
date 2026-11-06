package dev.portalix.portal.traversal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.List;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.jspecify.annotations.Nullable;

public final class PortalProjectileRotation {
  public static final Codec<Quaternionfc> CODEC = ExtraCodecs.QUATERNIONF.validate(
      rotation -> isValid(rotation) ? DataResult.success(rotation)
          : DataResult.error(() -> "Invalid portal projectile model rotation"));

  private PortalProjectileRotation() {}

  public static boolean isValid(Quaternionfc rotation) {
    return Float.isFinite(rotation.x()) && Float.isFinite(rotation.y())
        && Float.isFinite(rotation.z()) && Float.isFinite(rotation.w())
        && Math.abs(rotation.lengthSquared() - 1) < 1e-4f;
  }

  public static @Nullable Quaternionfc residual(AbstractArrow arrow) {
    return ((Access) arrow).portalix$projectileModelRotation();
  }

  public static void rotate(Entity entity, PortalEntityMovement.Crossing crossing, boolean undo) {
    if (!(entity instanceof AbstractArrow arrow)) return;
    var before = arrowRotation(crossing.beforeYaw(), crossing.beforePitch());
    var angles = PortalEntityAngles.transform(
        entity, crossing.link().transform(), crossing.beforeYaw(), crossing.beforePitch());
    var after = arrowRotation(angles.yaw(), angles.pitch());
    var rotation = crossing.link().transform().rotation();
    if (undo) rotation = before.conjugate().mul(rotation.conjugate()).mul(after);
    else rotation = after.conjugate().mul(rotation).mul(before);
    var previous = residual(arrow);
    if (previous != null) rotation.mul(previous);
    ((Access) arrow).portalix$projectileModelRotation(rotation.normalize());
  }

  static void acknowledge(Entity entity, List<PortalEntityMovement.Crossing> predictions,
      int index, PortalEntityMovement.Crossing authoritative) {
    if (!(entity instanceof AbstractArrow)) return;
    var predicted = predictions.get(index);
    if (predicted.beforeYaw() == authoritative.beforeYaw()
        && predicted.beforePitch() == authoritative.beforePitch()) return;
    for (int i = predictions.size() - 1; i >= index; i--) rotate(entity, predictions.get(i), true);
    rotate(entity, authoritative, false);
    for (int i = index + 1; i < predictions.size(); i++) rotate(entity, predictions.get(i), false);
  }

  public static @Nullable Quaternionf modelRotation(Entity entity, float partial) {
    if (!(entity instanceof AbstractArrow arrow)) return null;
    var rotation = residual(arrow);
    if (rotation == null) return null;
    var basis = arrowRotation(entity.getYRot(partial), entity.getXRot(partial));
    return new Quaternionf(basis).mul(rotation).mul(basis.conjugate());
  }

  private static Quaternionf arrowRotation(float yaw, float pitch) {
    return new Quaternionf().rotationY((float) Math.toRadians(yaw - 90))
        .rotateZ((float) Math.toRadians(pitch));
  }

  public interface Access {
    @Nullable Quaternionfc portalix$projectileModelRotation();

    void portalix$projectileModelRotation(@Nullable Quaternionfc rotation);
  }
}
