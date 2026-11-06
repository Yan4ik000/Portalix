package dev.portalix.portal.traversal;

import dev.portalix.portal.PortalLink;
import dev.portalix.portal.PortalTopology;
import dev.portalix.portal.geometry.PortalAperture;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class PortalPassagePose {
  private PortalPassagePose() {}

  public interface Access {
    float portalix$poseScale();

    void portalix$poseScale(float scale);

    boolean portalix$poseField(int id);
  }

  public interface NetworkAccess {
    boolean portalix$acceptPoseScale(float scale);
  }

  public static boolean acceptNetworkScale(Player player, float scale) {
    return !(player instanceof NetworkAccess access) || access.portalix$acceptPoseScale(scale);
  }

  public static boolean compact(Entity entity) {
    return scale(entity) < 1;
  }

  public static void set(Entity entity, boolean compact) {
    set(entity, compact ? .5f : 1);
  }

  public static float scale(Entity entity) {
    return entity instanceof Access access ? access.portalix$poseScale() : 1;
  }

  public static void set(Entity entity, float scale) {
    if (entity instanceof Access access) access.portalix$poseScale(scale);
  }

  public static void enter(Entity entity, PortalLink link) {
    set(entity, Math.min(scale(entity), passageScale(entity, link)));
  }

  public static boolean validScale(Entity entity, float requested) {
    if (!Float.isFinite(requested) || requested <= 0 || requested > 1) return false;
    if (requested >= scale(entity)) return true;
    return PortalTopology.links(entity.level()).stream()
        .anyMatch(
            link ->
                PortalEntityMovement.canTraverse(entity, link)
                    && passageScale(entity, link) <= requested);
  }

  private static float passageScale(Entity entity, PortalLink link) {
    if (!required(entity, link)) return 1;
    EntityDimensions normal = uncompressedDimensions(entity, Pose.STANDING);
    double preferred = normal.height();
    double height = PortalAperture.passageHeight(link.exit().endpoint(), normal.width(), preferred);
    if (height > 0) {
      return height < preferred ? Math.nextDown((float) (height / preferred)) : 1;
    }
    return 1;
  }

  public static void changed(Entity entity, EntityDataAccessor<?> field) {
    if (isPoseField(entity, field.id())) changed(entity);
  }

  public static void changed(Entity entity) {
    float eyeHeight = entity.getEyeHeight();
    EntityPortalState.of(entity).poseStarted = entity.tickCount;
    entity.refreshDimensions();
    EntityPortalState.of(entity).eyeHeightChanged(eyeHeight, entity.getEyeHeight());
  }

  public static boolean isPoseField(Entity entity, int id) {
    return entity instanceof Access access && access.portalix$poseField(id);
  }

  public static EntityDimensions dimensions(Avatar entity, Pose pose, EntityDimensions normal) {
    if (pose != Pose.STANDING && pose != Pose.CROUCHING) return normal;
    var state = EntityPortalState.of(entity);
    if (!compact(entity) || state.measuringPose) return normal;
    EntityDimensions standing = normal;
    if (pose == Pose.CROUCHING) {
      boolean measuring = state.measuringPose;
      state.measuringPose = true;
      try {
        standing = entity.getDefaultDimensions(Pose.STANDING);
      } finally {
        state.measuringPose = measuring;
      }
    }
    float scale = scale(entity);
    return standing.scale(1, scale).withEyeHeight(standing.eyeHeight() * scale * (28f / 32));
  }

  public static EntityDimensions interactionDimensions(Entity entity) {
    // Passage compression belongs to movement and the camera, not to the visible body.
    return uncompressedDimensions(entity, entity.getPose());
  }

  private static EntityDimensions uncompressedDimensions(Entity entity, Pose pose) {
    var state = EntityPortalState.of(entity);
    boolean measuring = state.measuringPose;
    state.measuringPose = true;
    try {
      return entity.getDimensions(pose);
    } finally {
      state.measuringPose = measuring;
    }
  }

  public static AABB interactionBounds(Entity entity) {
    return interactionBounds(entity, entity.getBoundingBox());
  }

  public static AABB interactionBounds(Entity entity, AABB bounds) {
    return compact(entity)
        ? bounds.minmax(interactionDimensions(entity).makeBoundingBox(entity.position()))
        : bounds;
  }

  public static AABB hull(Entity entity, Pose pose, float scale, Vec3 feet) {
    boolean compact = scale < 1 && (pose == Pose.STANDING || pose == Pose.CROUCHING);
    EntityDimensions dimensions = uncompressedDimensions(entity, compact ? Pose.STANDING : pose);
    return (compact ? dimensions.scale(1, scale) : dimensions).makeBoundingBox(feet);
  }

  public static boolean required(Entity entity, PortalLink link) {
    if (!(entity instanceof Player)
        || entity.isPassenger()
        || entity.getPose() != Pose.STANDING && entity.getPose() != Pose.CROUCHING) return false;

    return link.exit().endpoint().normal().y > .1
        || link.transform().vector(new Vec3(0, 1, 0)).y < 1 - 1e-7;
  }

  public static boolean fits(Entity entity, AABB hull) {
    var body = hull.deflate(1e-7);
    Boolean portal = fitsThroughPortal(entity, body);
    return portal != null ? portal : entity.level().noCollision(entity, body);
  }

  public static Boolean fitsThroughPortal(Entity entity, AABB body) {
    var link =
        PortalEntityCollisions.intersectingPortal(
            entity,
            body.minmax(entity.getBoundingBox()),
            Vec3.ZERO,
            entity.getBoundingBox().getCenter());
    if (link == null) return null;
    // Resizing cannot cross the portal plane; only movement can change the occupied space.
    return PortalAperture.signedDistance(link.entrance().endpoint(), body.getCenter())
            >= -PortalAperture.PLANE_EPSILON
        && !PortalCollisionWorld.query(entity, link, body).intersects(body);
  }

  public static void tick(Player player) {
    EntityPortalState.of(player).cameraHistory().discardThrough(player.tickCount - 1);
    if (!compact(player) || player.level().isClientSide() && !player.isLocalInstanceAuthoritative())
      return;
    var state = EntityPortalState.of(player);
    if (player.tickCount - state.poseStarted < 4) return;
    EntityDimensions standing = uncompressedDimensions(player, player.getPose());
    Vec3 feet = player.position();
    var hull = standing.makeBoundingBox(feet).deflate(1e-7);
    var last = state.lastCrossing();
    if (!player.onGround()
        && last != null
        && PortalTopology.links(player.level()).contains(last.link())) {
      var exit = last.link().exit().endpoint();

      if (exit.normal().y > .1
          && PortalAperture.touches(exit, hull)
          && PortalAperture.signedDistance(exit, hull.getCenter())
              < PortalAperture.supportRadius(hull, exit.normal())) return;
    }
    var link = PortalEntityCollisions.intersectingPortal(player, hull, Vec3.ZERO);
    boolean blocked =
        link == null
            ? !player.level().noCollision(player, hull)
            : PortalCollisionWorld.query(player, link, hull).intersects(hull);
    if (blocked) return;
    Vec3 oldEye = player.getEyePosition();
    set(player, false);
    state.cameraHistory().shifted(player.tickCount, oldEye.subtract(player.getEyePosition()));
  }
}
