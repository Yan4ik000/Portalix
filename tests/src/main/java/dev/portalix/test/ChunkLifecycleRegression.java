package dev.portalix.test;

import static dev.portalix.test.ServerRegressionSuite.check;

import dev.portalix.api.PortalColor;
import dev.portalix.item.ModItems;
import dev.portalix.item.PortalixIdentity;
import dev.portalix.item.PortalixVariant;
import dev.portalix.portal.PortalTopology;
import dev.portalix.portal.query.PlayerPortalVisibility;
import dev.portalix.server.portal.GunPortalRegistry;
import dev.portalix.server.portal.PortalChunkTracker;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.block.Blocks;
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

  ChunkLifecycleRegression(RegressionWorld world) {
    this.world = world;
    noGravity = world.player.isNoGravity();
    registry = new GunPortalRegistry(world.level.getServer());
    tracker = new PortalChunkTracker(registry);
    var stack = new ItemStack(ModItems.PORTALIX.get());
    gun = PortalixIdentity.ensure(stack);
  }

  @Override
  public boolean tick() {
    int now = world.level.getServer().getTickCount();
    if (started < 0) {
      world.player.setNoGravity(true);
      var stack = new ItemStack(ModItems.PORTALIX.get());
      stack.set(PortalixIdentity.ID.get(), gun);
      world.player.setItemSlot(EquipmentSlot.MAINHAND, stack);
      var entrance = world.endpoint(Direction.NORTH, new Vec3(144, 104, 144));
      var exit = world.endpoint(Direction.SOUTH, new Vec3(1032, 104, 1032));
      world.surface(entrance, Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState());
      world.surface(exit, Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState());
      world.block(marker, Blocks.GOLD_BLOCK.defaultBlockState());
      world.player.snapTo(entrance.center().add(0, -world.player.getEyeHeight(), -2), 0, 0);
      world.player.setDeltaMovement(Vec3.ZERO);
      check(registry.apply(world.player, gun, PortalColor.BLUE, entrance, PortalixVariant.STANDARD),
          "Could not place the chunk course entrance");
      check(registry.apply(world.player, gun, PortalColor.ORANGE, exit, PortalixVariant.STANDARD),
          "Could not place the distant chunk course exit");
      started = now;
    }
    check(now - started < 1000, "Portal terrain did not finish its load and release lifecycle");
    tracker.tick(world.level.getServer());
    if (!released) {
      if (!world.level.areEntitiesActuallyLoadedAndTicking(destination)) return false;
      check(world.level.getBlockState(marker).is(Blocks.GOLD_BLOCK), "Remote terrain lost its block edit");
      check(!PlayerPortalVisibility.of(world.player).eyes().isEmpty(), "Linked portal did not observe its exit terrain");
      verifySoundDelivery();
      released = true;
      return false;
    }
    check(PortalTopology.links(world.level).isEmpty(), "Reset left a portal pair active");
    if (!PlayerPortalVisibility.of(world.player).chunks().isEmpty()
        || world.level.getChunkSource().getChunkNow(destination.x(), destination.z()) != null) return false;
    world.level.getChunk(destination.x(), destination.z());
    check(world.level.getBlockState(marker).is(Blocks.GOLD_BLOCK), "Chunk unload and reload lost the world edit");
    return true;
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
    int viewRadius = visibility.viewRadius();
    visibility.replace(new LongOpenHashSet(), List.of());
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

    visibility.observe(chunks, observers, simulation, viewRadius);
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
