package dev.portalix.client.portal.camera;

import dev.portalix.portal.PortalLink;
import dev.portalix.portal.PortalTopology;
import dev.portalix.portal.geometry.PortalAperture;
import dev.portalix.portal.query.PortalRaycast;
import dev.portalix.portal.traversal.PortalCollisionWorld;
import dev.portalix.portal.traversal.PortalEntityMovement;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class PortalCameraEnvironment {
  private PortalCameraEnvironment() {}

  public static boolean applies(Player player) {
    var camera = Minecraft.getInstance().gameRenderer.mainCamera();
    if (camera.entity() != player) return false;
    if (PortalCameraController.active()) return true;
    var bounds = player.getBoundingBox().inflate(PortalAperture.RIM_THICKNESS);
    return PortalTopology.links(player.level()).stream()
        .anyMatch(
            link ->
                PortalEntityMovement.canTraverse(player, link)
                    && (PortalAperture.touches(link.entrance().endpoint(), bounds)
                        || insideOpening(link, camera.position())));
  }

  public static List<PortalLink> samplingLinks(Camera camera) {
    if (!(camera.entity() instanceof Player player) || !applies(player)) return List.of();
    return PortalTopology.links(player.level()).stream()
        .filter(candidate -> PortalEntityMovement.canTraverse(player, candidate))
        .toList();
  }

  public record BlockingBlock(BlockState state, BlockPos position) {}

  public static BlockingBlock blockingBlock(Player player) {
    if (player.noPhysics) return null;
    var camera = Minecraft.getInstance().gameRenderer.mainCamera();
    Vec3 eye = camera.position();
    BlockPos block = BlockPos.containing(eye);
    BlockState state = player.level().getBlockState(block);
    if (state.getRenderShape() == RenderShape.INVISIBLE
        || !state.isViewBlocking(player.level(), block)) return null;
    var opening =
        samplingLinks(camera).stream()
            .filter(link -> insideOpening(link, eye))
            .min(
                Comparator.comparingDouble(
                    link -> -PortalAperture.signedDistance(link.entrance().endpoint(), eye)));
    if (opening.isPresent()
        && !PortalCollisionWorld.query(
                player,
                opening.orElseThrow(),
                new AABB(eye, eye).inflate(PortalAperture.PLANE_EPSILON))
            .contains(eye)) return null;
    return new BlockingBlock(state, block);
  }

  private static boolean insideOpening(PortalLink link, Vec3 point) {
    var portal = link.entrance().endpoint();
    double depth = PortalAperture.signedDistance(portal, point);
    return depth <= 0
        && depth >= -PortalAperture.CAVITY_DEPTH
        && PortalAperture.containsPassage(portal, point);
  }

  public static Vec3 sample(Vec3 eye, Vec3 point, List<PortalLink> links) {
    return PortalRaycast.cast(
            eye,
            point.subtract(eye),
            links,
            link -> true,
            (from, to) -> BlockHitResult.miss(to, Direction.NORTH, BlockPos.containing(to)))
        .hit()
        .getLocation();
  }

  public static boolean eyeInFluid(Player player, TagKey<Fluid> tag) {
    Vec3 eye = Minecraft.getInstance().gameRenderer.mainCamera().position();
    BlockPos block = BlockPos.containing(eye);
    var fluid = player.level().getFluidState(block);
    return fluid.is(tag) && eye.y < block.getY() + fluid.getHeight(player.level(), block);
  }
}
