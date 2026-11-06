package dev.portalix.test.client;

import static dev.portalix.test.client.ClientAssertions.check;

import dev.portalix.api.PortalColor;
import dev.portalix.api.PortalEndpoint;
import dev.portalix.carry.CarryBindings;
import dev.portalix.item.ModItems;
import dev.portalix.item.PortalixIdentity;
import dev.portalix.item.PortalixVariant;
import dev.portalix.portal.PortalTopology;
import dev.portalix.portal.placement.PortalSurface;
import dev.portalix.portal.traversal.EntityPortalState;
import dev.portalix.portal.traversal.PortalItemRotation;
import dev.portalix.server.portal.GunPortalRegistry;
import dev.portalix.test.client.mixin.PortalServerSessionAccessor;
import dev.portalix.test.client.mixin.PortalServerEventsAccessor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

final class EntitySystemFixture implements AutoCloseable {
  enum Kind { ITEM, CHICKEN, SNOWBALL, ARROW, TRIDENT, PIG }
  record Ref(int id, UUID uuid, Kind kind) {}
  record Observation(Vec3 position, Vec3 velocity, boolean crossed, int heldBy,
                     Quaternionf rotation, boolean alive) {}

  static final Vec3 OBSERVER = new Vec3(152.5, 100, 500.5);
  static final Vec3 BLUE = new Vec3(144.5, 101, 516);
  static final Vec3 ORANGE = new Vec3(160.5, 101, 516);
  final ServerPlayer owner;
  final ServerLevel level;
  final UUID gunId;
  final long portalGeneration;
  private final GunPortalRegistry registry;
  private final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
  private final List<Entity> actors = new ArrayList<>();
  private final ItemStack hand;
  private final Vec3 playerPosition;
  private final Vec3 playerVelocity;
  private final float yaw;
  private final float pitch;
  private boolean closed;

  EntitySystemFixture(ServerPlayer owner, boolean portals) {
    this.owner = owner;
    level = owner.level();
    hand = owner.getMainHandItem().copy();
    playerPosition = owner.position();
    playerVelocity = owner.getDeltaMovement();
    yaw = owner.getYRot();
    pitch = owner.getXRot();
    var session = PortalServerEventsAccessor.portalix$session();
    registry = ((PortalServerSessionAccessor) (Object) session).portalix$registry();
    var gun = new ItemStack(ModItems.PORTALIX.get());
    gunId = PortalixIdentity.ensure(gun);
    try {
      for (int x = 8; x <= 10; x++) for (int z = 31; z <= 32; z++) level.getChunk(x, z);
      for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(136, 99, 497), new BlockPos(168, 105, 521)))
        block(pos, pos.getY() == 99 ? Blocks.QUARTZ_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState());
      var backdrop = Blocks.CONCRETE.pick(DyeColor.GRAY).defaultBlockState();
      if (portals) {
        for (int x : new int[] {144, 160})
          for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(x - 2, 100, 496), new BlockPos(x + 2, 105, 496)))
            block(pos, backdrop);
      } else {
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(136, 100, 521), new BlockPos(168, 105, 521)))
          block(pos, backdrop);
      }
      owner.setItemSlot(EquipmentSlot.MAINHAND, gun);
      owner.setDeltaMovement(Vec3.ZERO);
      check(owner.teleportTo(level, OBSERVER.x, OBSERVER.y, OBSERVER.z, Set.of(), 0, 0, true),
          "Entity scene observer setup failed");
      if (portals) {
        for (PortalColor color : PortalColor.values()) {
          Vec3 center = color == PortalColor.BLUE ? BLUE : ORANGE;
          var endpoint = new PortalEndpoint(level.dimension(), center, new Vec3(0, 0, -1),
              new Vec3(0, 1, 0), 1, 2);
          for (BlockPos pos : PortalSurface.placementFootprint(endpoint).blocks())
            block(pos, Blocks.STONE.defaultBlockState());
          check(registry.apply(owner, gunId, color, endpoint, PortalixVariant.STANDARD),
              "Entity scene registry rejected a valid supported portal");
        }
      }
      portalGeneration = portals ? PortalTopology.links(level).stream().filter(link -> link.gun().equals(gunId))
          .findFirst().orElseThrow().generation() : 0;
    } catch (RuntimeException | Error failure) {
      try { close(); } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
      throw failure;
    }
  }

  private void block(BlockPos position, BlockState state) {
    BlockPos key = position.immutable();
    blocks.putIfAbsent(key, level.getBlockState(key));
    level.setBlock(key, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
  }

  Ref spawn(Kind kind, Vec3 center) {
    return addEntity(kind, createEntity(kind, center), center);
  }

  Entity createEntity(Kind kind, Vec3 center) {
    return switch (kind) {
      case ITEM -> new ItemEntity(level, center.x, center.y, center.z, new ItemStack(Items.DIAMOND_BLOCK, 7));
      case CHICKEN -> EntityTypes.CHICKEN.create(level, EntitySpawnReason.COMMAND);
      case PIG -> EntityTypes.PIG.create(level, EntitySpawnReason.COMMAND);
      case SNOWBALL -> EntityTypes.SNOWBALL.create(level, EntitySpawnReason.COMMAND);
      case ARROW -> EntityTypes.ARROW.create(level, EntitySpawnReason.COMMAND);
      case TRIDENT -> EntityTypes.TRIDENT.create(level, EntitySpawnReason.COMMAND);
    };
  }

  Ref addEntity(Kind kind, Entity entity, Vec3 center) {
    check(entity != null, "Entity scene native factory failed: " + kind);
    entity.setPos(center.subtract(0, entity.getBbHeight() / 2, 0));
    entity.setDeltaMovement(Vec3.ZERO);
    entity.setNoGravity(true);
    if (entity instanceof Mob mob) mob.setNoAi(true);
    if (entity instanceof ItemEntity item) { item.setNeverPickUp(); item.setUnlimitedLifetime(); }
    if (entity instanceof Projectile projectile) projectile.setOwner(owner);
    check(level.addFreshEntity(entity), "Entity scene native spawn failed: " + kind);
    actors.add(entity);
    return new Ref(entity.getId(), entity.getUUID(), kind);
  }

  Entity entity(Ref ref) {
    var actor = level.getEntity(ref.id());
    check(actor != null && actor.getUUID().equals(ref.uuid()), "Entity scene lost actor identity: " + ref);
    return actor;
  }

  void launch(Ref ref) {
    var actor = entity(ref);
    actor.setNoGravity(false);
    if (actor instanceof Mob mob) mob.setNoAi(false);
    if (actor instanceof Projectile projectile) projectile.shoot(0, 0, 1, 1, 0);
    else actor.setDeltaMovement(0, 0, .7);
  }

  Observation observe(Ref ref) {
    var actor = entity(ref);
    return new Observation(actor.position(), actor.getDeltaMovement(), EntityPortalState.of(actor).lastCrossing() != null,
        CarryBindings.holderId(actor), actor instanceof ItemEntity item ? PortalItemRotation.rotation(item) : null,
        actor.isAlive());
  }

  void remove(Ref ref) {
    var actor = actors.stream().filter(owned -> owned.getId() == ref.id()
        && owned.getUUID().equals(ref.uuid())).findFirst()
        .orElseThrow(() -> new AssertionError("Entity scene does not own actor: " + ref));
    actor.discard();
  }

  @Override public void close() {
    if (closed) return;
    closed = true;
    CarryBindings.unbindOwner(level, owner.getId());
    registry.clear(gunId);
    actors.forEach(Entity::discard);
    for (var entry : blocks.entrySet())
      level.setBlock(entry.getKey(), entry.getValue(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    owner.setItemSlot(EquipmentSlot.MAINHAND, hand);
    owner.teleportTo(level, playerPosition.x, playerPosition.y, playerPosition.z, Set.of(), yaw, pitch, true);
    owner.setDeltaMovement(playerVelocity);
  }
}
