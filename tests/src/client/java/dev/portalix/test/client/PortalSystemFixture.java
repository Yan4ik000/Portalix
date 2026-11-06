package dev.portalix.test.client;

import dev.portalix.api.PortalColor;
import dev.portalix.api.PortalEndpoint;
import dev.portalix.item.ModItems;
import dev.portalix.item.PortalixIdentity;
import dev.portalix.item.PortalixVariant;
import dev.portalix.portal.PortalTopology;
import dev.portalix.portal.geometry.PortalAperture;
import dev.portalix.portal.traversal.EntityPortalState;
import dev.portalix.portal.traversal.PortalPassagePose;
import dev.portalix.portal.traversal.PortalTraversalReadiness;
import dev.portalix.server.portal.GunPortalRegistry;
import dev.portalix.test.client.mixin.PortalServerEventsAccessor;
import dev.portalix.test.client.mixin.PortalServerSessionAccessor;
import dev.portalix.test.client.mixin.ServerChunkCacheAccessor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.BlockAndLightGetter;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** An isolated server-owned render laboratory; setup teleports are outside the observed action. */
final class PortalSystemFixture implements AutoCloseable {
  record Marker(AABB bounds, FrameCapture.Material material) {}
  record Nested(UUID gun, Arrangement arrangement) {}
  record ClipSentinel(AABB bounds, List<BlockPos> blocks, List<BlockPos> lightProbes, List<BlockPos> lights) {
    boolean ready(BlockAndLightGetter level) {
      return !level.getLightEngine().hasLightWork()
          && blocks.stream().allMatch(position -> level.getBlockState(position).is(Blocks.CONCRETE.pick(DyeColor.MAGENTA)))
          && lights.stream().allMatch(position -> level.getBlockState(position).is(Blocks.SEA_LANTERN))
          && lightProbes.stream().allMatch(position -> level.getBlockState(position).isAir()
              && level.getBrightness(LightLayer.BLOCK, position) >= 12);
    }

    Map<String, Object> lightingState(BlockAndLightGetter level) {
      return Map.of("pendingNativeLightWork", level.getLightEngine().hasLightWork(),
          "sources", lights.stream().map(position -> Map.of("position", position.toString(),
              "state", level.getBlockState(position).toString())).toList(),
          "probes", lightProbes.stream().map(position -> Map.of("position", position.toString(),
              "state", level.getBlockState(position).toString(), "blockLight", level.getBrightness(LightLayer.BLOCK, position))).toList());
    }
  }
  record Arrangement(PortalEndpoint entrance, PortalEndpoint exit, Vec3 feet, Vec3 aim,
                     List<Marker> markers, long generation) {
    Vec3 imagePoint(Vec3 point) { return mapped(point, exit, entrance); }
    Vec3 exitPoint(Vec3 point) { return mapped(point, entrance, exit); }
    AABB imageBounds(AABB box) { return mappedBounds(box, exit, entrance); }
    PortalEndpoint imagePortal(PortalEndpoint portal) {
      Vec3 center = imagePoint(portal.center());
      return new PortalEndpoint(entrance.dimension(), center,
          imagePoint(portal.center().add(portal.normal())).subtract(center),
          imagePoint(portal.center().add(portal.up())).subtract(center), 1, 2);
    }
    AABB exitBounds(AABB box) { return mappedBounds(box, entrance, exit); }
    AABB materialBounds() {
      BlockPos center = BlockPos.containing(exit.center().lerp(markers.getFirst().bounds().getCenter(), .55));
      return new AABB(center.offset(0, -1, 0)).minmax(new AABB(center.offset(1, 1, 0)));
    }
    List<BlockPos> markerLightProbes() {
      Direction face = Direction.getApproximateNearest(exit.normal()).getOpposite();
      return markers.stream().map(marker -> BlockPos.containing(marker.bounds().getCenter()).relative(face)).toList();
    }
    private static AABB mappedBounds(AABB box, PortalEndpoint from, PortalEndpoint to) {
      double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
      double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
      for (int corner = 0; corner < 8; corner++) {
        Vec3 p = mapped(new Vec3((corner & 1) == 0 ? box.minX : box.maxX,
            (corner & 2) == 0 ? box.minY : box.maxY, (corner & 4) == 0 ? box.minZ : box.maxZ), from, to);
        minX = Math.min(minX, p.x); minY = Math.min(minY, p.y); minZ = Math.min(minZ, p.z);
        maxX = Math.max(maxX, p.x); maxY = Math.max(maxY, p.y); maxZ = Math.max(maxZ, p.z);
      }
      return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }
  }

  private final ServerPlayer player;
  private final ServerLevel level;
  private final int originX;
  private final int originZ;
  private final Vec3 previousPosition;
  private final float previousYaw;
  private final float previousPitch;
  private final GameType previousMode;
  private final boolean previousFlying;
  private final float previousPoseScale;
  private final List<ItemStack> inventory = new ArrayList<>();
  private final int previousSlot;
  private final ItemStack previousChest;
  private final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
  private final Map<BlockPos, BlockState> materialBefore = new LinkedHashMap<>();
  private final List<Entity> entities = new ArrayList<>();
  private final ItemStack gun = new ItemStack(ModItems.PORTALIX.get());
  private final UUID gunId = PortalixIdentity.ensure(gun);
  private final List<UUID> additionalGuns = new ArrayList<>();
  private final GunPortalRegistry registry;
  private boolean closed;
  private Arrangement current;

  PortalSystemFixture(ServerPlayer player, int originX, int originZ) {
    this.player = player;
    level = player.level();
    requireServerThread();
    this.originX = originX;
    this.originZ = originZ;
    previousPosition = player.position();
    previousYaw = player.getYRot(); previousPitch = player.getXRot();
    previousMode = player.gameMode(); previousFlying = player.getAbilities().flying;
    previousPoseScale = PortalPassagePose.scale(player);
    previousSlot = player.getInventory().getSelectedSlot();
    previousChest = player.getItemBySlot(EquipmentSlot.CHEST).copy();
    for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++)
      inventory.add(player.getInventory().getItem(slot).copy());
    var session = PortalServerEventsAccessor.portalix$session();
    if (session == null) throw new IllegalStateException("The integrated server has no mod session");
    registry = ((PortalServerSessionAccessor) (Object) session).portalix$registry();
    for (int x = (originX - 8) >> 4; x <= (originX + 26) >> 4; x++)
      for (int z = (originZ - 8) >> 4; z <= (originZ + 9) >> 4; z++) level.getChunk(x, z);
  }

  UUID gunId() { return gunId; }

  Arrangement prepare(Direction entryFace, Direction exitFace, float exitRoll, double eyeDistance,
                      boolean reverseLook) {
    requireServerThread();
    clearArrangement();
    player.setGameMode(GameType.CREATIVE);
    player.getAbilities().flying = true;
    player.onUpdateAbilities();
    player.setDeltaMovement(Vec3.ZERO);
    player.setShiftKeyDown(false);
    player.setSprinting(false);
    PortalPassagePose.set(player, 1);
    for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++)
      player.getInventory().setItem(slot, ItemStack.EMPTY);
    player.getInventory().setItem(0, gun.copy());
    player.getInventory().setSelectedSlot(1);
    player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
    player.connection.send(new ClientboundSetHeldSlotPacket(1));
    double ceiling = Math.max(102, Math.ceil(100 + player.getEyeHeight() + eyeDistance - PortalAperture.PLANE_EPSILON));
    var entry = endpoint(new Vec3(originX + .5, entryFace == Direction.UP ? 100 : entryFace == Direction.DOWN ? ceiling : 101,
        originZ), entryFace, 0);
    var exit = endpoint(new Vec3(originX + 16.5, exitFace == Direction.UP ? 100 : exitFace == Direction.DOWN ? 104 : 101,
        originZ), exitFace, exitRoll);
    panel(entry); panel(exit);
    // The broad panel clearance must not remove the walking surface below a wall aperture.
    fill(new BlockPos(originX - 6, 99, originZ - 6), new BlockPos(originX + 22, 99, originZ + 6),
        Blocks.QUARTZ_BLOCK.defaultBlockState());
    List<Marker> markers = List.of(marker(exit, -.6, -.5, DyeColor.RED, FrameCapture.Material.RED),
        marker(exit, .6, -.5, DyeColor.LIME, FrameCapture.Material.GREEN),
        marker(exit, 0, .5, DyeColor.BLUE, FrameCapture.Material.BLUE));
    paintPlane(exit.center().add(exit.normal().scale(6)), exit, Blocks.CONCRETE.pick(DyeColor.GRAY).defaultBlockState());
    lightLandmarks(exit, markers);
    if (!registry.apply(player, gunId, PortalColor.BLUE, entry, PortalixVariant.STANDARD)
        || !registry.apply(player, gunId, PortalColor.ORANGE, exit, PortalixVariant.STANDARD))
      throw new AssertionError("Authored portal surfaces did not pass the real placement validation");
    Vec3 eye = entry.center().add(entry.normal().scale(eyeDistance));
    if (entryFace.getAxis() != Direction.Axis.Y) eye = eye.add(0, player.getEyeHeight() - 1, 0);
    Vec3 feet = eye.add(0, -player.getEyeHeight(), 0);
    Vec3 aim = eye.add(entry.normal().scale(reverseLook ? 5 : -5));
    teleport(feet, aim);
    long generation = PortalTopology.links(level).stream().filter(link -> link.gun().equals(gunId))
        .findFirst().orElseThrow().generation();
    current = new Arrangement(entry, exit, feet, aim, markers, generation);
    return current;
  }

  void teleport(Vec3 feet, Vec3 aim) {
    requireServerThread();
    Vec3 direction = aim.subtract(feet.add(0, player.getEyeHeight(), 0));
    float yaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
    float pitch = (float) -Math.toDegrees(Math.atan2(direction.y, direction.horizontalDistance()));
    // Native block packets must precede the position packet, so old walls cannot push the new observer away.
    ((ServerChunkCacheAccessor) (Object) level.getChunkSource()).portalix$broadcastChangedChunks(Profiler.get());
    if (!player.teleportTo(level, feet.x, feet.y, feet.z, Set.of(), yaw, pitch, true))
      throw new AssertionError("Native setup teleport failed");
    player.setDeltaMovement(Vec3.ZERO);
  }

  void releaseFlight() {
    requireServerThread();
    player.getAbilities().flying = false;
    player.onUpdateAbilities();
  }

  AABB cameraObstacle() {
    requireServerThread();
    var center = BlockPos.containing(current.exit.center().add(current.exit.normal().scale(2.5)));
    var first = center.offset(-1, -1, 0);
    var last = center.offset(1, 2, 0);
    fill(first, last, Blocks.QUARTZ_BLOCK.defaultBlockState());
    var chest = new ItemStack(Items.LEATHER_CHESTPLATE);
    chest.set(DataComponents.DYED_COLOR, new DyedItemColor(0x00ffff));
    player.setItemSlot(EquipmentSlot.CHEST, chest);
    return new AABB(first).minmax(new AABB(last));
  }

  void clearPortals() { requireServerThread(); registry.clear(gunId); }

  ClipSentinel clipSentinel() {
    requireServerThread();
    var positions = paintPlane(current.exit.center().subtract(current.exit.normal().scale(1.5)), current.exit,
        Blocks.CONCRETE.pick(DyeColor.MAGENTA).defaultBlockState());
    Direction backward = Direction.getApproximateNearest(current.exit.normal()).getOpposite();
    var probes = new ArrayList<BlockPos>();
    var lights = new ArrayList<BlockPos>();
    for (BlockPos position : positions) {
      BlockPos probe = position.relative(backward);
      BlockPos light = probe.relative(backward);
      block(probe, Blocks.AIR.defaultBlockState());
      block(light, Blocks.SEA_LANTERN.defaultBlockState());
      probes.add(probe); lights.add(light);
    }
    AABB bounds = new AABB(positions.getFirst());
    for (BlockPos position : positions) bounds = bounds.minmax(new AABB(position));
    return new ClipSentinel(bounds, positions, List.copyOf(probes), List.copyOf(lights));
  }

  Nested recursivePair() {
    requireServerThread();
    UUID nestedGun = UUID.randomUUID();
    additionalGuns.add(nestedGun);
    for (int x = (originX + 24) >> 4; x <= (originX + 42) >> 4; x++)
      for (int z = (originZ - 8) >> 4; z <= (originZ + 9) >> 4; z++) level.getChunk(x, z);
    var entry = endpoint(current.exit.center().add(0, 0, 4), Direction.NORTH, 0);
    var exit = endpoint(new Vec3(originX + 32.5, 101, originZ), Direction.SOUTH, 0);
    panel(entry); panel(exit);
    var markers = List.of(marker(exit, -.6, -.5, DyeColor.CYAN, FrameCapture.Material.CYAN),
        marker(exit, .6, -.5, DyeColor.ORANGE, FrameCapture.Material.ORANGE),
        marker(exit, 0, .5, DyeColor.LIME, FrameCapture.Material.GREEN));
    paintPlane(exit.center().add(exit.normal().scale(6)), exit, Blocks.CONCRETE.pick(DyeColor.GRAY).defaultBlockState());
    lightLandmarks(exit, markers);
    if (!registry.apply(player, nestedGun, PortalColor.BLUE, entry, PortalixVariant.STANDARD)
        || !registry.apply(player, nestedGun, PortalColor.ORANGE, exit, PortalixVariant.STANDARD))
      throw new AssertionError("The second real portal pair failed placement validation");
    long generation = PortalTopology.links(level).stream().filter(link -> link.gun().equals(nestedGun))
        .findFirst().orElseThrow().generation();
    return new Nested(nestedGun, new Arrangement(entry, exit, current.feet, current.aim, markers, generation));
  }

  void block(BlockPos position, BlockState state) {
    requireServerThread();
    var immutable = position.immutable();
    blocks.putIfAbsent(immutable, level.getBlockState(immutable));
    level.setBlock(immutable, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
  }

  void fill(BlockPos first, BlockPos last, BlockState state) {
    for (var position : BlockPos.betweenClosed(first, last)) block(position, state);
  }

  AABB placeMaterial(BlockState state) {
    requireServerThread();
    AABB bounds = current.materialBounds();
    materialBefore.clear();
    for (BlockPos position : materialPositions(bounds)) {
      materialBefore.put(position.immutable(), level.getBlockState(position));
      block(position, state);
    }
    return bounds;
  }

  void prepareMaterialContainer() {
    requireServerThread();
    AABB bounds = current.materialBounds();
    BlockPos first = BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ);
    BlockPos last = BlockPos.containing(bounds.maxX - .01, bounds.maxY - .01, bounds.maxZ - .01);
    for (BlockPos position : BlockPos.betweenClosed(first.offset(-1, -1, -1), last.offset(1, 1, 1)))
      if (!level.getBlockState(position).is(Blocks.SEA_LANTERN)) block(position, Blocks.BARRIER.defaultBlockState());
    fill(first, last, Blocks.AIR.defaultBlockState());
  }

  void restoreMaterial() {
    requireServerThread();
    materialBefore.forEach(this::block);
  }

  static Iterable<BlockPos> materialPositions(AABB bounds) {
    return BlockPos.betweenClosed(BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ),
        BlockPos.containing(bounds.maxX - .01, bounds.maxY - .01, bounds.maxZ - .01));
  }

  int spawnSliceItem(double depth) {
    requireServerThread();
    Vec3 point = current.exit.center().add(current.exit.normal().scale(depth));
    var item = new ItemEntity(level, point.x, point.y - .125, point.z, new ItemStack(Items.DIAMOND_BLOCK));
    item.setNoGravity(true); item.setNeverPickUp(); item.setUnlimitedLifetime();
    item.setDeltaMovement(Vec3.ZERO);
    if (!level.addFreshEntity(item)) throw new AssertionError("Could not spawn the real clipping actor");
    entities.add(item);
    return item.getId();
  }

  Vec3 moveSliceItem(int id, double depth) {
    requireServerThread();
    Entity item = level.getEntity(id);
    if (item == null || !entities.contains(item)) throw new IllegalStateException("The clipping actor is missing");
    Vec3 point = current.exit.center().add(current.exit.normal().scale(depth)).add(0, -.125, 0);
    item.teleportTo(point.x, point.y, point.z);
    item.setDeltaMovement(Vec3.ZERO);
    return point;
  }

  boolean traversalReady() {
    requireServerThread();
    return PortalTopology.links(level).stream().anyMatch(link -> link.gun().equals(gunId)
        && link.generation() == current.generation
        && link.entrance().endpoint().equals(current.entrance)
        && PortalTraversalReadiness.ready(player, link));
  }

  boolean crossedEntrance() {
    requireServerThread();
    var crossing = EntityPortalState.of(player).lastCrossing();
    return crossing != null && crossing.link().gun().equals(gunId)
        && crossing.link().generation() == current.generation
        && crossing.link().entrance().endpoint().equals(current.entrance)
        && player.position().distanceTo(current.exit.center()) < 5;
  }

  Map<String, Object> diagnostics() {
    requireServerThread();
    var result = new LinkedHashMap<String, Object>();
    result.put("position", player.position().toString());
    result.put("velocity", player.getDeltaMovement().toString());
    result.put("bounds", player.getBoundingBox().toString());
    result.put("flying", player.getAbilities().flying);
    result.put("onGround", player.onGround());
    result.put("poseScale", PortalPassagePose.scale(player));
    result.put("lastCrossing", String.valueOf(EntityPortalState.of(player).lastCrossing()));
    result.put("links", PortalTopology.links(level).stream().filter(link -> link.gun().equals(gunId))
        .map(link -> Map.of("link", link.toString(), "traversalReady", PortalTraversalReadiness.ready(player, link))).toList());
    result.put("blockBelowFeet", level.getBlockState(player.blockPosition().below()).toString());
    if (current != null) result.put("markers", current.markers.stream().map(marker -> {
      var position = BlockPos.containing(marker.bounds.getCenter());
      return Map.of("material", marker.material.name(), "position", position.toString(),
          "bounds", marker.bounds.toString(), "state", level.getBlockState(position).toString());
    }).toList());
    if (current != null) result.put("markerLighting", markerLightingState(level, current));
    if (!materialBefore.isEmpty()) result.put("materialBlocks", materialBefore.entrySet().stream().map(entry ->
        Map.of("position", entry.getKey().toString(), "before", entry.getValue().toString(),
            "actual", level.getBlockState(entry.getKey()).toString())).toList());
    return result;
  }

  private PortalEndpoint endpoint(Vec3 center, Direction face, float roll) {
    if (face.getAxis() == Direction.Axis.X) center = new Vec3(Math.floor(center.x), center.y, center.z);
    Vec3 normal = Vec3.atLowerCornerOf(face.getUnitVec3i());
    double angle = Math.toRadians(roll);
    Vec3 up = face.getAxis().isVertical() ? new Vec3(-Math.sin(angle), 0, Math.cos(angle)) : new Vec3(0, 1, 0);
    return new PortalEndpoint(level.dimension(), center, normal, up, 1, 2);
  }

  private void panel(PortalEndpoint portal) {
    var black = Blocks.CONCRETE.pick(DyeColor.GRAY).defaultBlockState();
    for (double x = -3; x <= 3; x += .25) for (double y = -3; y <= 3; y += .25) {
      Vec3 point = portal.center().subtract(portal.normal().scale(.5))
          .add(portal.right().scale(x)).add(portal.up().scale(y));
      block(BlockPos.containing(point), black);
      block(BlockPos.containing(point.add(portal.normal())), Blocks.AIR.defaultBlockState());
    }
  }

  private Marker marker(PortalEndpoint exit, double horizontal, double vertical,
                        DyeColor color, FrameCapture.Material material) {
    Vec3 point = exit.center().add(exit.normal().scale(5)).add(exit.right().scale(horizontal))
        .add(exit.up().scale(vertical));
    BlockPos pos = BlockPos.containing(point);
    block(pos, Blocks.CONCRETE.pick(color).defaultBlockState());
    return new Marker(new AABB(pos), material);
  }

  private void lightLandmarks(PortalEndpoint exit, List<Marker> markers) {
    for (BlockPos position : markerLightPositions(exit)) block(position, Blocks.SEA_LANTERN.defaultBlockState());
    Direction face = Direction.getApproximateNearest(exit.normal()).getOpposite();
    for (Marker marker : markers) {
      BlockPos probe = BlockPos.containing(marker.bounds().getCenter()).relative(face);
      if (!level.getBlockState(probe).isAir())
        throw new AssertionError("A marker camera-facing light probe is obstructed: " + probe + " " + level.getBlockState(probe));
    }
  }

  private static List<BlockPos> markerLightPositions(PortalEndpoint exit) {
    Vec3 plane = exit.center().add(exit.normal().scale(4));
    return List.of(BlockPos.containing(plane.add(exit.right().scale(-2)).add(exit.up().scale(-2))),
        BlockPos.containing(plane.add(exit.right().scale(-2)).add(exit.up().scale(2))),
        BlockPos.containing(plane.add(exit.right().scale(2)).add(exit.up().scale(-2))),
        BlockPos.containing(plane.add(exit.right().scale(2)).add(exit.up().scale(2))),
        BlockPos.containing(plane.add(exit.up().scale(2))));
  }

  static boolean markerLightingReady(BlockAndLightGetter level, Arrangement scene) {
    return !level.getLightEngine().hasLightWork()
        && markerLightPositions(scene.exit()).stream().allMatch(position -> level.getBlockState(position).is(Blocks.SEA_LANTERN))
        && scene.markerLightProbes().stream().allMatch(position -> level.getBrightness(LightLayer.BLOCK, position) >= 12);
  }

  static Map<String, Object> markerLightingState(BlockAndLightGetter level, Arrangement scene) {
    return Map.of("pendingNativeLightWork", level.getLightEngine().hasLightWork(),
        "sources", markerLightPositions(scene.exit()).stream().map(position -> Map.of(
            "position", position.toString(), "state", level.getBlockState(position).toString())).toList(),
        "probes", scene.markerLightProbes().stream().map(position -> Map.of("position", position.toString(),
            "state", level.getBlockState(position).toString(), "blockLight", level.getBrightness(LightLayer.BLOCK, position))).toList());
  }

  private List<BlockPos> paintPlane(Vec3 center, PortalEndpoint orientation, BlockState state) {
    var positions = new LinkedHashSet<BlockPos>();
    for (double x = -3; x <= 3; x += .25) for (double y = -3; y <= 3; y += .25)
      positions.add(BlockPos.containing(center.add(orientation.right().scale(x)).add(orientation.up().scale(y))));
    positions.forEach(position -> block(position, state));
    return List.copyOf(positions);
  }

  private static Vec3 mapped(Vec3 point, PortalEndpoint from, PortalEndpoint to) {
    Vec3 local = point.subtract(from.center());
    return to.center().subtract(to.right().scale(local.dot(from.right())))
        .add(to.up().scale(local.dot(from.up()))).subtract(to.normal().scale(local.dot(from.normal())));
  }

  private void clearArrangement() {
    registry.clear(gunId);
    for (UUID gun : additionalGuns) registry.clear(gun);
    additionalGuns.clear();
    for (Entity entity : entities) entity.discard();
    entities.clear();
    for (var entry : blocks.entrySet())
      level.setBlock(entry.getKey(), entry.getValue(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    blocks.clear();
    materialBefore.clear();
  }

  private void requireServerThread() {
    if (!level.getServer().isSameThread()) throw new IllegalStateException("Render fixtures belong to the server thread");
  }

  @Override public void close() {
    requireServerThread();
    if (closed) return;
    closed = true;
    try {
      registry.clear(gunId);
      player.teleportTo(level, previousPosition.x, previousPosition.y, previousPosition.z,
          Set.of(), previousYaw, previousPitch, true);
    } finally {
      clearArrangement();
      for (int slot = 0; slot < inventory.size(); slot++) player.getInventory().setItem(slot, inventory.get(slot));
      player.getInventory().setSelectedSlot(previousSlot);
      player.setItemSlot(EquipmentSlot.CHEST, previousChest);
      player.connection.send(new ClientboundSetHeldSlotPacket(previousSlot));
      player.setGameMode(previousMode);
      player.getAbilities().flying = previousFlying;
      player.onUpdateAbilities();
      PortalPassagePose.set(player, previousPoseScale);
    }
  }
}
