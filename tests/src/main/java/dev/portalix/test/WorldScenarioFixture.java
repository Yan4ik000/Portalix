package dev.portalix.test;

import dev.portalix.item.ModItems;
import dev.portalix.item.PortalixIdentity;
import dev.portalix.item.PortalixVariant;
import dev.portalix.network.gun.GunStatePayload;
import dev.portalix.portal.PortalLink;
import dev.portalix.portal.PortalTopology;
import dev.portalix.portal.query.PlayerPortalVisibility;
import dev.portalix.portal.traversal.EntityPortalState;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.vehicle.minecart.Minecart;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;

public final class WorldScenarioFixture implements AutoCloseable {
  public enum Area { GUN, COMBAT, VEHICLE }
  public record EntityRef(int id, UUID uuid) {}

  public record VehicleCourse(
      Vec3 setupFeet,
      Vec3 sourceAim,
      Vec3 exitAim,
      Vec3 boardingFeet,
      BlockPos lever,
      Vec3 cartStart) {}

  public record VehicleObservation(
      Vec3 cartPosition,
      Vec3 cartVelocity,
      List<UUID> passengerUuids,
      int playerVehicleId,
      UUID playerVehicleUuid,
      Vec3 playerPosition,
      double playerHeight,
      Vec3 cartLastExit,
      Vec3 playerLastExit,
      boolean sourceReady,
      boolean leverPowered) {
    public VehicleObservation {
      passengerUuids = List.copyOf(passengerUuids);
    }
  }

  public record ServerObservation(
      float targetHealth,
      Vec3 targetPosition,
      Vec3 targetVelocity,
      int targetBlockLight,
      List<PortalLink> links) {
    public ServerObservation {
      links = List.copyOf(links);
    }
  }

  public record Course(
      Vec3 spawn,
      Vec3 missAim,
      Vec3 traversalBlueAim,
      Vec3 traversalOrangeAim,
      Vec3 combatBlueAim,
      Vec3 combatOrangeAim,
      Vec3 combatApproach,
      Vec3 combatTargetFeet) {}

  private static final Course COURSE = new Course(
      new Vec3(.5, 100, 5.5),
      new Vec3(48, 101, 5.5),
      new Vec3(.5, 101, 0),
      new Vec3(16.5, 101, 0),
      new Vec3(24.5, 101, 16),
      new Vec3(32.5, 101, 16),
      new Vec3(24.5, 100, 14.8),
      new Vec3(32.5, 100, 14.7));

  private static final VehicleCourse VEHICLE_COURSE = new VehicleCourse(
      new Vec3(40.5, 100, 46),
      new Vec3(40.5, 101, 24), new Vec3(56, 101, 24.5),
      new Vec3(42.5, 100, 42.5),
      new BlockPos(41, 100, 42), new Vec3(40.5, 100.0625, 42.5));

  private final ServerLevel level;
  private final Area area;
  private final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
  private ItemStack gun = new ItemStack(ModItems.PORTALIX.get());
  private final UUID gunId = PortalixIdentity.ensure(gun);
  private Zombie combatTarget;
  private Minecart vehicleCart;
  private ServerPlayer owner;
  private List<ItemStack> originalHotbar;
  private int originalSelectedSlot;
  private ItemStack originalOffHand;
  private GameType originalMode;
  private boolean originalFlying;
  private boolean closed;

  private WorldScenarioFixture(ServerLevel level, Area area) {
    this.level = level;
    this.area = area;
  }

  public static Course course() {
    return COURSE;
  }

  public static VehicleCourse vehicleCourse() {
    return VEHICLE_COURSE;
  }

  public EntityRef vehicleCart() {
    return new EntityRef(vehicleCart.getId(), vehicleCart.getUUID());
  }

  public boolean vehicleSourceReady() {
    requireServerThread();
    if (closed || owner == null)
      throw new IllegalStateException("The course player must be bound before observing the vehicle portal");
    return liveLinks().stream().anyMatch(link ->
        link.entrance().endpoint().center().distanceTo(VEHICLE_COURSE.sourceAim()) < .1
            && PlayerPortalVisibility.of(owner).isReady(link));
  }

  public void spawnVehicleCart() {
    requireServerThread();
    if (closed || owner == null || level.getEntity(vehicleCart.getId()) != null)
      throw new IllegalStateException("The station minecart must be spawned once for its bound course player");
    if (!level.addFreshEntity(vehicleCart)) throw new IllegalStateException("Could not add the station minecart");
  }

  public VehicleObservation observeVehicle() {
    requireServerThread();
    if (closed || owner == null || level.getEntity(vehicleCart.getId()) != vehicleCart)
      throw new IllegalStateException("The station minecart must be spawned before observing the vehicle");
    var ridden = owner.getVehicle();
    var cartCrossing = EntityPortalState.of(vehicleCart).lastCrossing();
    var playerCrossing = EntityPortalState.of(owner).lastCrossing();
    return new VehicleObservation(vehicleCart.position(), vehicleCart.getDeltaMovement(),
        vehicleCart.getPassengers().stream().map(net.minecraft.world.entity.Entity::getUUID).toList(),
        ridden == null ? -1 : ridden.getId(), ridden == null ? GunStatePayload.EMPTY_TARGET : ridden.getUUID(),
        owner.position(), owner.getBoundingBox().getYsize(),
        cartCrossing == null ? Vec3.ZERO : cartCrossing.link().exit().endpoint().center(),
        playerCrossing == null ? Vec3.ZERO : playerCrossing.link().exit().endpoint().center(),
        vehicleSourceReady(), level.getBlockState(VEHICLE_COURSE.lever()).getValue(BlockStateProperties.POWERED));
  }

  public static WorldScenarioFixture build(ServerLevel level, Area area) {
    if (!level.getServer().isSameThread())
      throw new IllegalStateException("The course must be built on the server thread");
    var fixture = new WorldScenarioFixture(level, area);
    try {
      fixture.buildCourse();
      return fixture;
    } catch (RuntimeException | Error failure) {
      fixture.close();
      throw failure;
    }
  }

  public void bind(ServerPlayer player) {
    requireServerThread();
    if (closed || owner != null || player.level() != level)
      throw new IllegalStateException("The course requires one player in its own level");
    owner = player;
    var inventory = player.getInventory();
    originalHotbar = List.of(inventory.getItem(0).copy(), inventory.getItem(1).copy(), inventory.getItem(2).copy());
    originalSelectedSlot = inventory.getSelectedSlot();
    originalOffHand = player.getOffhandItem().copy();
    originalMode = player.gameMode();
    originalFlying = player.getAbilities().flying;
    player.setGameMode(GameType.CREATIVE);
    player.getAbilities().flying = false;
    player.onUpdateAbilities();
    player.setSprinting(false);
    player.setShiftKeyDown(false);
    player.stopUsingItem();
    player.setDeltaMovement(Vec3.ZERO);
    player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
    inventory.setItem(0, gun);
    inventory.setItem(1, new ItemStack(Items.DIAMOND_SWORD));
    inventory.setItem(2, ItemStack.EMPTY);
    inventory.setSelectedSlot(2);
    player.connection.send(new ClientboundSetHeldSlotPacket(2));
  }

  public UUID gunId() {
    return gunId;
  }

  public void equipVariant(PortalixVariant variant) {
    requireServerThread();
    if (closed || owner == null)
      throw new IllegalStateException("The course player must be bound before changing the gun variant");
    if (!gunId.equals(PortalixIdentity.get(owner.getInventory().getItem(0))))
      throw new IllegalStateException("The course gun is no longer in its assigned hotbar slot");
    gun = new ItemStack(switch (variant) {
      case STANDARD -> ModItems.PORTALIX.get();
      case POTATO -> ModItems.POTATO_PORTALIX.get();
      case ORANGE -> ModItems.ORANGE_PORTALIX.get();
      case BLUE -> ModItems.BLUE_PORTALIX.get();
    });
    gun.set(PortalixIdentity.ID.get(), gunId);
    owner.getInventory().setItem(0, gun);
  }

  public ServerObservation observe() {
    requireServerThread();
    if (closed || owner == null)
      throw new IllegalStateException("The course player must be bound before observing its state");
    return new ServerObservation(combatTarget.getHealth(), combatTarget.position(), combatTarget.getDeltaMovement(),
        level.getBrightness(LightLayer.BLOCK, BlockPos.containing(combatTarget.getLightProbePosition(1))), liveLinks());
  }

  public EntityRef combatTarget() {
    return new EntityRef(combatTarget.getId(), combatTarget.getUUID());
  }

  public List<PortalLink> liveLinks() {
    requireServerThread();
    return PortalTopology.links(level).stream().filter(link -> link.gun().equals(gunId)).toList();
  }

  private void buildCourse() {
    BlockState black = Blocks.CONCRETE.pick(DyeColor.BLACK).defaultBlockState();
    if (area == Area.VEHICLE) {
      for (int x = 2; x <= 3; x++) for (int z = 0; z <= 2; z++) level.getChunk(x, z);
      buildVehicleStation(black);
      return;
    }
    if (area == Area.GUN) {
      int targetX = BlockPos.containing(COURSE.missAim()).getX();
      int targetZ = BlockPos.containing(COURSE.missAim()).getZ();
      for (int x = -1; x <= ((targetX + 2) >> 4); x++) for (int z = -1; z <= 0; z++) level.getChunk(x, z);
      fill(new BlockPos(-4, 100, -4), new BlockPos(targetX + 2, 106, 10), Blocks.AIR.defaultBlockState());
      fill(new BlockPos(-4, 99, -4), new BlockPos(16, 99, 10), Blocks.QUARTZ_BLOCK.defaultBlockState());
      fill(new BlockPos(2, 99, targetZ - 1), new BlockPos(targetX, 99, targetZ + 1), black);
      fill(new BlockPos(-3, 100, -1), new BlockPos(21, 104, -1), black);
      for (Vec3 aim : List.of(COURSE.traversalBlueAim(), COURSE.traversalOrangeAim()))
        fill(new BlockPos((int) aim.x, 100, -1), new BlockPos((int) aim.x, 102, -1),
            Blocks.QUARTZ_BLOCK.defaultBlockState());
      fill(new BlockPos(targetX, 100, targetZ), new BlockPos(targetX, 104, targetZ), Blocks.IRON_BARS.defaultBlockState());
      fill(new BlockPos(targetX + 1, 100, 1), new BlockPos(targetX + 1, 106, 10), black);
      return;
    }
    for (int x = 1; x <= 2; x++) for (int z = 0; z <= 1; z++) level.getChunk(x, z);
    fill(new BlockPos(22, 100, 10), new BlockPos(38, 106, 20), Blocks.AIR.defaultBlockState());
    fill(new BlockPos(22, 99, 10), new BlockPos(38, 99, 20), Blocks.QUARTZ_BLOCK.defaultBlockState());
    fill(new BlockPos(22, 100, 16), new BlockPos(38, 104, 16), black);
    for (Vec3 aim : List.of(COURSE.combatBlueAim(), COURSE.combatOrangeAim()))
      fill(new BlockPos((int) aim.x, 100, 16), new BlockPos((int) aim.x, 102, 16),
          Blocks.QUARTZ_BLOCK.defaultBlockState());
    // Keep the dyed armor lit inside the wall's sunlight shadow.
    block(new BlockPos(33, 101, 14), Blocks.SEA_LANTERN.defaultBlockState());

    // Husks keep a stable health baseline in daylight; the chestplate identifies their portal image.
    combatTarget = EntityTypes.HUSK.create(level, EntitySpawnReason.COMMAND);
    if (combatTarget == null) throw new IllegalStateException("Could not create the combat target");
    var chestplate = new ItemStack(Items.LEATHER_CHESTPLATE);
    chestplate.set(DataComponents.DYED_COLOR, new DyedItemColor(0x00ff00));
    combatTarget.setItemSlot(EquipmentSlot.CHEST, chestplate);
    combatTarget.snapTo(COURSE.combatTargetFeet(), 0, 0);
    combatTarget.setNoAi(true);
    combatTarget.setPersistenceRequired();
    combatTarget.setDeltaMovement(Vec3.ZERO);
    combatTarget.setCustomName(Component.literal("Portal combat target"));
    combatTarget.setCustomNameVisible(true);
    if (!level.addFreshEntity(combatTarget)) throw new IllegalStateException("Could not add the combat target");
  }

  private void buildVehicleStation(BlockState black) {
    fill(new BlockPos(41, 100, 14), new BlockPos(62, 106, 48), Blocks.AIR.defaultBlockState());
    fill(new BlockPos(36, 100, 25), new BlockPos(40, 106, 48), Blocks.AIR.defaultBlockState());
    fill(new BlockPos(36, 99, 14), new BlockPos(62, 99, 48), Blocks.QUARTZ_BLOCK.defaultBlockState());
    fill(new BlockPos(38, 100, 23), new BlockPos(42, 104, 23), black);
    fill(new BlockPos(40, 100, 23), new BlockPos(40, 102, 23), Blocks.QUARTZ_BLOCK.defaultBlockState());
    fill(new BlockPos(56, 100, 22), new BlockPos(56, 104, 26), black);
    fill(new BlockPos(56, 100, 24), new BlockPos(56, 102, 24), Blocks.QUARTZ_BLOCK.defaultBlockState());
    for (int distance = 0; distance <= 18; distance++) {
      var rail = distance < 10
          ? Blocks.RAIL.defaultBlockState().setValue(BlockStateProperties.RAIL_SHAPE, RailShape.NORTH_SOUTH)
          : Blocks.POWERED_RAIL.defaultBlockState()
              .setValue(BlockStateProperties.RAIL_SHAPE_STRAIGHT, RailShape.NORTH_SOUTH);
      block(new BlockPos(40, 100, 24 + distance), rail);
    }
    for (int distance = 0; distance <= 6; distance++)
      block(new BlockPos(55 - distance, 100, 24), Blocks.POWERED_RAIL.defaultBlockState()
          .setValue(BlockStateProperties.RAIL_SHAPE_STRAIGHT, RailShape.EAST_WEST));
    block(new BlockPos(40, 100, 43), Blocks.QUARTZ_BLOCK.defaultBlockState());
    block(VEHICLE_COURSE.lever(), Blocks.LEVER.defaultBlockState()
        .setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR)
        .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    vehicleCart = EntityTypes.MINECART.create(level, EntitySpawnReason.COMMAND);
    if (vehicleCart == null) throw new IllegalStateException("Could not create the station minecart");
    vehicleCart.snapTo(VEHICLE_COURSE.cartStart(), 0, 0);
    vehicleCart.setCustomName(Component.literal("Portal passenger transport"));
  }

  private void fill(BlockPos from, BlockPos to, BlockState state) {
    for (BlockPos pos : BlockPos.betweenClosed(from, to)) block(pos, state);
  }

  private void block(BlockPos pos, BlockState state) {
    var key = pos.immutable();
    if (level.getBlockEntity(key) != null)
      throw new IllegalStateException("The course must be built in a fresh test world: " + key);
    blocks.putIfAbsent(key, level.getBlockState(key));
    level.setBlock(key, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
  }

  private void requireServerThread() {
    if (!level.getServer().isSameThread())
      throw new IllegalStateException("Course state must be accessed on the server thread");
  }

  @Override
  public void close() {
    requireServerThread();
    if (closed) return;
    closed = true;
    if (combatTarget != null) combatTarget.discard();
    if (vehicleCart != null) {
      vehicleCart.ejectPassengers();
      vehicleCart.discard();
    }
    if (owner != null) {
      var inventory = owner.getInventory();
      for (int slot = 0; slot < originalHotbar.size(); slot++) inventory.setItem(slot, originalHotbar.get(slot));
      inventory.setSelectedSlot(originalSelectedSlot);
      owner.connection.send(new ClientboundSetHeldSlotPacket(originalSelectedSlot));
      owner.setItemSlot(EquipmentSlot.OFFHAND, originalOffHand);
      owner.setGameMode(originalMode);
      owner.getAbilities().flying = originalFlying;
      owner.onUpdateAbilities();
    }
    for (var entry : blocks.entrySet())
      level.setBlock(entry.getKey(), entry.getValue(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    blocks.clear();
  }
}
