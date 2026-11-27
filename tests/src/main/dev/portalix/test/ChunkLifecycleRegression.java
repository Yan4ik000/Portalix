package dev.portalix.test;

import static dev.portalix.test.ServerRegressionSuite.check;

import dev.portalix.PortalixMod;
import dev.portalix.api.PortalColor;
import dev.portalix.item.ModItems;
import dev.portalix.item.PortalixIdentity;
import dev.portalix.item.PortalixVariant;
import dev.portalix.mixin.world.tracking.ChunkMapAccessor;
import dev.portalix.portal.PortalTopology;
import dev.portalix.portal.traversal.EntityPortalState;
import dev.portalix.portal.PortalLink;
import dev.portalix.portal.PortalLinkId;
import dev.portalix.portal.PortalViewLimits;
import dev.portalix.network.portal.PortalViewDemandPayload;
import dev.portalix.server.portal.PortalClosureResolver;
import dev.portalix.portal.query.PlayerPortalVisibility;
import dev.portalix.server.portal.GunPortalRegistry;
import dev.portalix.server.portal.PortalChunkTracker;
import dev.portalix.world.chunk.PortalLoadingObservers;
import dev.portalix.world.chunk.WarmColumnAccess;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.FullChunkStatus;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;

final class ChunkLifecycleRegression implements ServerRegressionSuite.Scenario {
  private final RegressionWorld world;
  private final GunPortalRegistry registry;
  private final PortalChunkTracker tracker;
  private final UUID gun;
  private final boolean noGravity;
  private final ChunkPos destination = new ChunkPos(64, 64);
  private final BlockPos marker = new BlockPos(1032, 105, 1032);
  private int started = -1;
  private boolean released;
  private final WarmColumnAccess warmLoading;
  private int warmHolders;
  private int firstReleaseTick;
  private PortalObserverRegression observers;

  ChunkLifecycleRegression(RegressionWorld world) {
    this.world = world;
    noGravity = world.player.isNoGravity();
    warmLoading = ((PortalLoadingObservers) world.level.getChunkSource().chunkMap.getDistanceManager()).portalix$warmColumns();
    registry = new GunPortalRegistry(world.level.getServer());
    tracker = new PortalChunkTracker(registry);
    verifyCameraFrames();
    var stack = new ItemStack(ModItems.gun(PortalixVariant.STANDARD));
    gun = PortalixIdentity.ensure(stack);
  }

  private void verifyCameraFrames() {
    var previousLinks = PortalTopology.links(world.level);
    var feet = world.player.position();
    var velocity = world.player.getDeltaMovement();
    try (var probe = new PortalChunkTracker(registry)) {
      world.player.setPos(144, 104, 144);
      world.player.setDeltaMovement(Vec3.ZERO);
      probe.clientTickEnded(world.player);
      for (boolean reachable : new boolean[] {false, true}) {
        if (reachable) {
          PortalClosureResolver.updateLinks(world.level, List.of());
          probe.tick(world.level.getServer());
          world.player.setPos(144, 104, 120);
          probe.clientTickEnded(world.player);
          world.player.setPos(144, 104, 144);
        }
        probe.resetCamera(world.player);
        var path = new ArrayList<PortalLink>();
        var links = new ArrayList<PortalLink>();
        for (int index = 0; index < 3; index++) {
          double entranceZ = index == 0 ? (reachable ? 146 : 164)
              : 144 + index * 10000 + (reachable ? 1 : 16);
          var entrance = world.endpoint(Direction.NORTH, new Vec3(144, 105, entranceZ));
          var exit = world.endpoint(Direction.SOUTH, new Vec3(144, 105, 144 + (index + 1) * 10000));
          var link = world.link(entrance, exit);
          path.add(link);
          links.add(link);
          links.add(link.inverse());
        }
        PortalClosureResolver.updateLinks(world.level, links);
        for (var link : links) {
          var center = BlockPos.containing(PortalViewLimits.origin(link.exit().endpoint()));
          for (int x = (center.getX() >> 4) - 1; x <= (center.getX() >> 4) + 1; x++)
            for (int z = (center.getZ() >> 4) - 1; z <= (center.getZ() >> 4) + 1; z++)
              world.level.getChunk(x, z);
          PlayerPortalVisibility.of(world.player).ready(link);
        }
        if (reachable) {
          probe.clientTickEnded(world.player);
          world.player.setPos(144, 104, 120);
        }
        var demand = new PortalViewDemandPayload(path.stream().map(PortalLinkId::fromLink).toList(), List.of());
        probe.requestViews(world.player, demand);
        probe.tick(world.level.getServer());
        var distant = ChunkPos.containing(BlockPos.containing(PortalViewLimits.origin(path.getLast().exit().endpoint())));
        var visible = PlayerPortalVisibility.of(world.player);
        check(visible.simulationChunks().contains(distant.pack()) == reachable,
            "Camera demand ignored the first linked eye sample or physical reach: reachable=" + reachable);
        check(visible.chunks().contains(distant.pack()) == reachable,
            "A camera frame's delivered terrain ignored its physical reach");
        var tickets = world.level.getChunkSource().getDataStorage().computeIfAbsent(TicketStorage.TYPE);
        var type = BuiltInRegistries.TICKET_TYPE.getValue(PortalixMod.id("portal_simulation"));
        check(tickets.getTickets(distant.pack()).stream().anyMatch(ticket -> ticket.getType() == type) == reachable,
            "A camera frame's native simulation ticket ignored its physical reach");
        if (reachable) {
          world.player.setPos(144, 104, 200);
          probe.clientTickEnded(world.player);
          probe.requestViews(world.player, demand);
          probe.tick(world.level.getServer());
          check(visible.simulationChunks().contains(distant.pack()),
              "Moving the body discarded an already authorized third-person camera frame");
          var state = EntityPortalState.of(world.player);
          state.cameraHistory().shifted(world.player.tickCount, new Vec3(0, 1, 0));
          check(state.eyeCorrection(world.player.tickCount).lengthSqr() > .5,
              "The camera reset test has no prior eye correction");
          probe.resetCamera(world.player);
          probe.tick(world.level.getServer());
          check(state.eyeCorrection(world.player.tickCount).equals(Vec3.ZERO),
              "An ordinary teleport retained the old camera's eye correction");
          check(!visible.simulationChunks().contains(distant.pack()),
              "An ordinary teleport retained the previous camera frame");
          world.player.setPos(144, 104, 100);
          probe.resetCamera(world.player);
          probe.requestViews(world.player, demand);
          probe.tick(world.level.getServer());
          check(!visible.simulationChunks().contains(distant.pack()),
              "An out-of-reach camera demand was accepted at a new position");
          world.player.setPos(144, 104, 144);
          probe.clientTickEnded(world.player);
          probe.tick(world.level.getServer());
          check(visible.simulationChunks().contains(distant.pack()),
              "A pending camera frame was not reconsidered as the body came within reach");
          PortalClosureResolver.updateLinks(world.level, List.of());
          probe.tick(world.level.getServer());
          check(!visible.simulationChunks().contains(distant.pack()),
              "Removing the portals retained a camera simulation region");
        }
      }
      probe.disconnect(world.player);
    } finally {
      world.player.setPos(feet);
      world.player.setDeltaMovement(velocity);
      PortalClosureResolver.updateLinks(world.level, previousLinks);
    }
  }

  @Override
  public boolean tick() {
    if (observers != null) return observers.tick();
    int now = world.level.getServer().getTickCount();
    if (started < 0) {
      world.player.setNoGravity(true);
      var stack = new ItemStack(ModItems.gun(PortalixVariant.STANDARD));
      stack.set(PortalixIdentity.ID.get(), gun);
      world.player.setItemSlot(EquipmentSlot.MAINHAND, stack);
      var entrance = world.endpoint(Direction.NORTH, new Vec3(144, 104, 144));
      var exit = world.endpoint(Direction.SOUTH, new Vec3(1032, 104, 1032));
      world.surface(entrance, Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState());
      world.surface(exit, Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState());
      world.block(marker, Blocks.GOLD_BLOCK.defaultBlockState());
      world.player.snapTo(entrance.center().add(0, -world.player.getEyeHeight(), -2), 0, 0);
      world.player.setDeltaMovement(Vec3.ZERO);
      check(registry.apply(world.player, gun, PortalColor.BLUE, entrance, PortalixVariant.STANDARD, () -> true),
          "Could not place the chunk course entrance");
      check(registry.apply(world.player, gun, PortalColor.ORANGE, exit, PortalixVariant.STANDARD, () -> true),
          "Could not place the distant chunk course exit");
      started = now;
    }
    check(now - started < 1000, "Portal terrain did not finish its load and release lifecycle");
    tracker.tick(world.level.getServer());
    if (!released) {
      if (!world.level.areEntitiesActuallyLoadedAndTicking(destination)) {
        var chunks = (ChunkMapAccessor) world.level.getChunkSource().chunkMap;
        var holder = chunks.portalix$getVisibleChunk(destination.pack());
        if (holder == null) return false;
        // GameTest ticks are unpaced. Wait for the work scheduled by the portal's ticket.
        var ticking = holder.getEntityTickingChunkFuture();
        world.level.getServer().managedBlock(ticking::isDone);
        if (!ticking.join().isSuccess() || !world.level.isPositionEntityTicking(marker)) return false;
        world.level.waitForEntities(destination, 0);
      }
      check(world.level.getBlockState(marker).is(Blocks.GOLD_BLOCK), "Remote terrain lost its block edit");
      check(!PlayerPortalVisibility.of(world.player).eyes().isEmpty(), "Linked portal did not observe its exit terrain");
      verifySpectatorTickets();
      warmLoading.portalix$retainColumn(destination.pack());
      warmLoading.portalix$retainColumn(destination.pack());
      warmHolders = 2;
      verifySoundDelivery();
      released = true;
      return false;
    }
    check(PortalTopology.links(world.level).isEmpty(), "Reset left a portal pair active");
    if (warmHolders > 0) {
      var manager = world.level.getChunkSource().chunkMap.getDistanceManager();
      if (manager.getChunkLevel(destination.pack(), false) < ChunkLevel.byStatus(FullChunkStatus.BLOCK_TICKING)) return false;
      check(manager.getChunkLevel(destination.pack(), false) == ChunkLevel.byStatus(FullChunkStatus.BLOCK_TICKING)
              && world.level.getChunkSource().getChunkNow(destination.x(), destination.z()) != null,
          "An already delivered column unloaded before its last observer released it");
      if (warmHolders == 2 || now > firstReleaseTick + 3) {
        warmLoading.portalix$releaseColumn(destination.pack());
        warmHolders--;
        firstReleaseTick = now;
      }
      return false;
    }
    if (!PlayerPortalVisibility.of(world.player).chunks().isEmpty()
        || world.level.getChunkSource().getChunkNow(destination.x(), destination.z()) != null) return false;
    world.level.getChunk(destination.x(), destination.z());
    check(world.level.getBlockState(marker).is(Blocks.GOLD_BLOCK), "Chunk unload and reload lost the world edit");
    observers = new PortalObserverRegression(world);
    return false;
  }

  private void verifySpectatorTickets() {
    var level = world.level;
    var server = level.getServer();
    var rule = GameRules.SPECTATORS_GENERATE_CHUNKS;
    boolean original = level.getGameRules().get(rule);
    var gameMode = world.player.gameMode();
    var tickets = level.getChunkSource().getDataStorage().computeIfAbsent(TicketStorage.TYPE);
    var type = BuiltInRegistries.TICKET_TYPE.getValue(PortalixMod.id("portal_simulation"));
    check(type != null, "Portal simulation ticket is unregistered");
    try {
      check(tickets.getTickets(destination.pack()).stream().anyMatch(ticket -> ticket.getType() == type),
          "Active player did not hold a portal simulation ticket");
      level.getGameRules().set(rule, false, server);
      world.player.setGameMode(GameType.SPECTATOR);
      tracker.tick(server);
      check(tickets.getTickets(destination.pack()).stream().noneMatch(ticket -> ticket.getType() == type),
          "Spectator retained a portal simulation ticket with chunk generation disabled");
      check(!PlayerPortalVisibility.of(world.player).eyes().isEmpty(),
          "Disabling spectator generation also removed the existing portal view");
    } finally {
      world.player.setGameMode(gameMode);
      level.getGameRules().set(rule, original, server);
      tracker.tick(server);
    }
  }

  private void verifySoundDelivery() {
    var link = PortalTopology.links(world.level).stream()
        .filter(candidate -> candidate.gun().equals(gun))
        .filter(candidate -> candidate.exit().endpoint().center().distanceToSqr(Vec3.atCenterOf(marker)) < 4)
        .findFirst().orElseThrow();
    var source = link.exit().endpoint().center().add(link.exit().endpoint().normal().scale(3));
    var visibility = PlayerPortalVisibility.of(world.player);
    var chunks = visibility.chunks();
    var observers = visibility.observers();
    var simulation = visibility.simulationChunks();
    visibility.resetObservation(new LongOpenHashSet());
    var chicken = new Chicken(EntityTypes.CHICKEN, world.level);
    chicken.setPos(source);
    world.add(chicken);
    world.channel.flushOutbound();
    world.channel.runPendingTasks();
    check(world.channel.outboundMessages().stream().noneMatch(packet -> announcesEntity(packet, chicken.getId())),
        "The remote sound fixture must be untracked by its recipient");

    long seed = 0x706f7274616cL;
    var sound = SoundEvents.CHICKEN_STEP;
    world.level.playSeededSound(null, chicken, sound, SoundSource.NEUTRAL, .8F, 1.25F, seed);
    var remote = sounds(seed);
    check(remote.size() == 1 && remote.getFirst() instanceof ClientboundSoundPacket,
        "An untracked source must deliver exactly one positional sound through an unseen portal: " + remote);
    var positional = (ClientboundSoundPacket) remote.getFirst();
    check(positional.getSound().value() == sound.value()
        && positional.getSource() == SoundSource.NEUTRAL
        && positional.getVolume() == .8F && positional.getPitch() == 1.25F,
        "Remote delivery changed the sound, category, volume or pitch");
    ServerRegressionSuite.near(new Vec3(positional.getX(), positional.getY(), positional.getZ()),
        source, .125, "Remote delivery changed the original emission position");

    world.level.playSeededSound(world.player, chicken, sound, SoundSource.NEUTRAL, 1, 1, seed + 1);
    check(sounds(seed + 1).isEmpty(), "Portal delivery ignored the excluded listener");

    chicken.setPos(world.player.position().add(2, 0, 0));
    world.level.playSeededSound(null, chicken, sound, SoundSource.NEUTRAL, 1, 1, seed + 2);
    var local = sounds(seed + 2);
    check(local.size() == 1 && local.getFirst() instanceof ClientboundSoundEntityPacket,
        "A normal recipient must retain exactly one native entity sound: " + local);

    visibility.observe(chunks, observers, simulation);
    registry.clear(gun);
    chicken.setPos(source);
    world.level.playSeededSound(null, chicken, sound, SoundSource.NEUTRAL, 1, 1, seed + 3);
    check(sounds(seed + 3).isEmpty(), "Closing a portal retained remote sound delivery");
  }

  private List<Object> sounds(long seed) {
    world.channel.flushOutbound();
    world.channel.runPendingTasks();
    return world.channel.outboundMessages().stream().filter(packet ->
        packet instanceof ClientboundSoundPacket sound && sound.getSeed() == seed
            || packet instanceof ClientboundSoundEntityPacket entitySound && entitySound.getSeed() == seed)
        .toList();
  }

  private static boolean announcesEntity(Object packet, int id) {
    if (packet instanceof ClientboundAddEntityPacket spawn) return spawn.getId() == id;
    if (packet instanceof ClientboundBundlePacket bundle)
      for (var child : bundle.subPackets()) if (announcesEntity(child, id)) return true;
    return false;
  }

  @Override
  public void close() {
    try {
      if (observers != null) observers.close();
      while (warmHolders > 0) {
        warmLoading.portalix$releaseColumn(destination.pack());
        warmHolders--;
      }
      tracker.close();
    } finally {
      try {
        registry.clearAll();
      } finally {
        world.player.setNoGravity(noGravity);
      }
    }
  }
}
