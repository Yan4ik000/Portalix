package dev.portalix.test.client;

import static dev.portalix.test.client.ClientAssertions.check;

import com.google.gson.GsonBuilder;
import dev.portalix.portal.PortalTopology;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

final class WorldUpdatesSystemScene extends PortalPreparedScene {
  private enum Step {
    PREPARE, RED, REPLACE, GREEN, REMOVE, REMOVED, ROOM_SETUP, ROOM, DARK, LIGHT, LIT,
    ACTOR_SETUP, ACTOR_ROOM, ACTOR_BASELINE, SPAWN, ENTITY_GREEN, RECOLOR, ENTITY_MAGENTA,
    DESPAWN, ENTITY_GONE, CLOSE, CLOSED, DONE
  }
  private record Actor(int id, UUID uuid, AABB bounds) {}
  private record LightState(long gameTick, String lampState, String powerState, String probeState, boolean powered,
                            int emission, int lampBlockLight, int probeBlockLight, int probeSkyLight,
                            boolean pendingLightWork) {}
  private final List<String> passed = new ArrayList<>();
  private Step step = Step.PREPARE;
  private int age;
  private PortalTerrainUploadProbe uploads;
  private final Map<BlockPos, BlockState> expectedTerrain = new LinkedHashMap<>();
  private final List<Map<String, Object>> terrainProof = new ArrayList<>();
  private BlockPos marker;
  private BlockPos lamp;
  private BlockPos power;
  private BlockPos receiver;
  private BlockPos actorLightProbe;
  private CompletableFuture<Void> mutation;
  private CompletableFuture<Actor> creation;
  private Actor tracked;
  private Sheep actor;
  private CompletableFuture<FrameCapture.Frame> screenshot;
  private FrameCapture.Frame red;
  private FrameCapture.Frame dark;
  private FrameCapture.Frame entityBefore;
  private FrameCapture.Frame entityColored;
  private FrameCapture.Frame beforeClose;
  private CompletableFuture<LightState> timeoutServerLight;

  WorldUpdatesSystemScene(Minecraft client, ScenarioFrames frames) { super(client, frames); }

  public boolean tick() {
    age++;
    if (age == 580 && lamp != null) timeoutServerLight = originalServer.submit(() -> { return lightState(originalServer.overworld()); });
    if (age >= 600) throw new AssertionError("Live remote world updates timed out at " + step
        + (lamp == null ? "" : "; lamp=" + lamp + "; probe=" + lamp.west()
            + "; client=" + lightState(client.level) + "; server="
            + (timeoutServerLight != null && timeoutServerLight.isDone() ? timeoutServerLight.join() : "snapshot pending")));
    switch (step) {
      case PREPARE -> {
        if (!prepare(736, 512)) return false;
        marker = BlockPos.containing(arrangement.markers().getFirst().bounds().getCenter());
        next(Step.RED);
      }
      case RED -> {
        if (!capture("world-block-before", image -> {
          var region = region(image, arrangement.markers().getFirst().bounds());
          image.assertMaterial(region, FrameCapture.Material.RED, .05, 1);
          red = image;
        })) return false;
        observeTerrain(new AABB(marker), Map.of(marker, Blocks.CONCRETE.pick(DyeColor.LIME).defaultBlockState()));
        mutation = originalServer.submit(() -> fixture.block(marker, Blocks.CONCRETE.pick(DyeColor.LIME).defaultBlockState()));
        next(Step.REPLACE);
      }
      case REPLACE -> {
        if (!mutated() || !terrainReady()) return false;
        next(Step.GREEN);
      }
      case GREEN -> {
        if (!capture("world-block-replaced", image -> {
          var region = region(image, new AABB(marker));
          image.assertMaterial(region, FrameCapture.Material.GREEN, .05, 1);
          image.assertMaterial(region, FrameCapture.Material.RED, 0, .01);
          check(image.difference(red, region, 12).changedFraction() > .1, "The remote terrain renderer retained the replaced block");
        })) return false;
        observeTerrain(new AABB(marker), Map.of(marker, Blocks.AIR.defaultBlockState()));
        mutation = originalServer.submit(() -> fixture.block(marker, Blocks.AIR.defaultBlockState()));
        next(Step.REMOVE);
      }
      case REMOVE -> {
        if (!mutated() || !terrainReady()) return false;
        next(Step.REMOVED);
      }
      case REMOVED -> {
        if (!capture("world-block-removed", image -> image.assertMaterial(region(image, new AABB(marker)),
            FrameCapture.Material.GREEN, 0, .01))) return false;
        passed.add("world_updates/real remote block replacement and removal rebuild the visible portal terrain");
        next(Step.ROOM_SETUP);
      }
      case ROOM_SETUP -> {
        if (!frames.pause()) return false;
        var center = BlockPos.containing(arrangement.exit().center());
        receiver = center.offset(0, 0, 7);
        lamp = center.offset(2, 1, 6);
        power = lamp.east();
        var expected = new LinkedHashMap<BlockPos, BlockState>();
        for (BlockPos position : BlockPos.betweenClosed(receiver.offset(-1, -1, 0), receiver.offset(1, 1, 0)))
          expected.put(position.immutable(), Blocks.QUARTZ_BLOCK.defaultBlockState());
        expected.put(lamp, Blocks.REDSTONE_LAMP.defaultBlockState());
        expected.put(power, Blocks.AIR.defaultBlockState());
        expected.put(lamp.west(), Blocks.AIR.defaultBlockState());
        observeTerrain(new AABB(receiver).inflate(1, 1, 0), expected);
        mutation = originalServer.submit(this::buildLightRoom);
        next(Step.ROOM);
      }
      case ROOM -> {
        if (!mutated() || !terrainReady()) return false;
        var state = client.level.getBlockState(lamp);
        if (!state.is(Blocks.REDSTONE_LAMP) || state.getValue(BlockStateProperties.LIT)
            || !client.level.getBlockState(lamp.west()).isAir()
            || client.level.getBrightness(LightLayer.BLOCK, lamp.west()) != 0
            || client.level.getBrightness(LightLayer.SKY, lamp.west()) != 0) return false;
        if (client.level.getLightEngine().hasLightWork()) return false;
        frames.monitor(true);
        next(Step.DARK);
      }
      case DARK -> {
        if (!capture("world-light-before", image -> dark = image)) return false;
        mutation = originalServer.submit(() -> {
          var level = originalServer.overworld();
          check(level.setBlockAndUpdate(power, Blocks.REDSTONE_BLOCK.defaultBlockState()),
              "Native light power block placement failed");
          check(level.hasNeighborSignal(lamp) && level.getBlockState(lamp).getValue(BlockStateProperties.LIT),
              "Native redstone neighbor updates did not power the lamp: " + lightState(level));
        });
        next(Step.LIGHT);
      }
      case LIGHT -> {
        if (!mutated() || !client.level.getBlockState(power).equals(Blocks.REDSTONE_BLOCK.defaultBlockState())
            || !client.level.getBlockState(lamp).equals(Blocks.REDSTONE_LAMP.defaultBlockState().setValue(BlockStateProperties.LIT, true))
            || !client.level.getBlockState(lamp.west()).isAir()
            || client.level.getBrightness(LightLayer.BLOCK, lamp.west()) < 13
            || client.level.getLightEngine().hasLightWork() || !portalReady()) return false;
        next(Step.LIT);
      }
      case LIT -> {
        if (!capture("world-light-updated", image -> {
          var region = region(image, new AABB(receiver));
          double before = dark.statistics(region, pixel -> true).meanLuminance();
          double after = image.statistics(region, pixel -> true).meanLuminance();
          check(after > before + 2 && image.difference(dark, region, 8).changedFraction() > .1,
              "Real block-light propagation did not illuminate the same remote white receiver: " + before + " -> " + after);
        })) return false;
        passed.add("world_updates/native light propagation updates the actual portal lightmap and remote surface pixels");
        next(Step.ACTOR_SETUP);
      }
      case ACTOR_SETUP -> {
        if (!frames.pause()) return false;
        var center = BlockPos.containing(arrangement.exit().center());
        var first = center.offset(-4, 3, 0);
        var last = center.offset(4, 3, 8);
        actorLightProbe = center.offset(0, 0, 4);
        var expected = new LinkedHashMap<BlockPos, BlockState>();
        for (BlockPos position : BlockPos.betweenClosed(first, last))
          expected.put(position.immutable(), Blocks.AIR.defaultBlockState());
        expected.put(power, Blocks.AIR.defaultBlockState());
        expected.put(lamp, Blocks.REDSTONE_LAMP.defaultBlockState());
        expected.put(actorLightProbe, Blocks.AIR.defaultBlockState());
        expected.put(actorLightProbe.north(), Blocks.AIR.defaultBlockState());
        observeTerrain(new AABB(first).minmax(new AABB(last)), expected);
        mutation = originalServer.submit(() -> {
          fixture.fill(first, last, Blocks.AIR.defaultBlockState());
          check(originalServer.overworld().setBlockAndUpdate(power, Blocks.AIR.defaultBlockState()),
              "Native actor stage did not remove the lamp power source");
        });
        next(Step.ACTOR_ROOM);
      }
      case ACTOR_ROOM -> {
        if (!mutated() || !terrainReady() || client.level.getLightEngine().hasLightWork()) return false;
        for (BlockPos position : List.of(actorLightProbe, actorLightProbe.above(),
            actorLightProbe.north(), actorLightProbe.north().above())) {
          if (!client.level.getBlockState(position).isAir()
              || client.level.getBrightness(LightLayer.SKY, position) != 15
              || client.level.getBrightness(LightLayer.BLOCK, position) != 0) return false;
        }
        frames.monitor(true);
        next(Step.ACTOR_BASELINE);
      }
      case ACTOR_BASELINE -> {
        if (!capture("world-entity-before", image -> entityBefore = image)) return false;
        creation = originalServer.submit(this::spawnActor);
        next(Step.SPAWN);
      }
      case SPAWN -> {
        if (!creation.isDone()) return false;
        tracked = creation.join();
        if (!(client.level.getEntity(tracked.id) instanceof Sheep sheep
            && sheep.getUUID().equals(tracked.uuid) && sheep.getColor() == DyeColor.LIME) || !portalReady()) return false;
        next(Step.ENTITY_GREEN);
      }
      case ENTITY_GREEN -> {
        if (!capture("world-entity-spawned", image -> {
          var region = region(image, tracked.bounds);
          image.assertMaterial(region, FrameCapture.Material.GREEN, .03, 1);
          check(image.difference(entityBefore, region, 12).changedFraction() > .05,
              "The tracked remote entity was absent from the actual portal frame");
        })) return false;
        mutation = originalServer.submit(() -> actor.setColor(DyeColor.MAGENTA));
        next(Step.RECOLOR);
      }
      case RECOLOR -> {
        if (!mutated() || !(client.level.getEntity(tracked.id) instanceof Sheep sheep
            && sheep.getUUID().equals(tracked.uuid) && sheep.getColor() == DyeColor.MAGENTA) || !portalReady()) return false;
        next(Step.ENTITY_MAGENTA);
      }
      case ENTITY_MAGENTA -> {
        if (!capture("world-entity-updated", image -> {
          image.assertMaterial(region(image, tracked.bounds), FrameCapture.Material.MAGENTA, .03, 1);
          entityColored = image;
        })) return false;
        mutation = originalServer.submit(actor::discard);
        next(Step.DESPAWN);
      }
      case DESPAWN -> {
        if (!mutated() || client.level.getEntity(tracked.id) != null || !portalReady()) return false;
        next(Step.ENTITY_GONE);
      }
      case ENTITY_GONE -> {
        if (!capture("world-entity-removed", image -> {
          var region = region(image, tracked.bounds);
          image.assertMaterial(region, FrameCapture.Material.MAGENTA, 0, .01);
          check(image.difference(entityColored, region, 12).changedFraction() > .05,
              "Removing the real tracked actor left stale portal entity pixels");
          beforeClose = image;
        })) return false;
        passed.add("world_updates/remote entity spawn metadata changes and removal update actual portal entity rendering");
        mutation = originalServer.submit(fixture::clearPortals);
        next(Step.CLOSE);
      }
      case CLOSE -> {
        if (!mutated() || PortalTopology.links(client.level).stream()
            .anyMatch(link -> link.gun().equals(fixture.gunId()))) return false;
        next(Step.CLOSED);
      }
      case CLOSED -> {
        if (!capture("world-portal-closed", image -> {
          var region = region(image, new AABB(receiver));
          check(image.difference(beforeClose, region, 12).changedFraction() > .1,
              "The closed aperture retained its remote lit world image");
        })) return false;
        passed.add("world_updates/closing the actual portal removes its changing remote world image");
        writeUploadProof();
        next(Step.DONE);
      }
      default -> { return true; }
    }
    return false;
  }

  private void buildLightRoom() {
    var center = BlockPos.containing(arrangement.exit().center());
    var black = Blocks.CONCRETE.pick(DyeColor.BLACK).defaultBlockState();
    fixture.fill(center.offset(-3, -1, 1), center.offset(3, 2, 7), Blocks.AIR.defaultBlockState());
    fixture.fill(center.offset(-4, -2, 1), center.offset(4, -2, 8), black);
    fixture.fill(center.offset(-4, 3, 0), center.offset(4, 3, 8), black);
    fixture.fill(center.offset(-4, -1, 0), center.offset(-4, 2, 8), black);
    fixture.fill(center.offset(4, -1, 0), center.offset(4, 2, 8), black);
    fixture.fill(center.offset(-4, -1, -1), center.offset(-4, 2, -1), black);
    fixture.fill(center.offset(4, -1, -1), center.offset(4, 2, -1), black);
    fixture.fill(center.offset(-4, -1, 8), center.offset(4, 2, 8), black);
    fixture.fill(receiver.offset(-1, -1, 0), receiver.offset(1, 1, 0), Blocks.QUARTZ_BLOCK.defaultBlockState());
    fixture.block(lamp, Blocks.REDSTONE_LAMP.defaultBlockState());
    fixture.block(power, Blocks.AIR.defaultBlockState());
    check(originalServer.overworld().getBlockState(lamp.west()).isAir(),
        "The native light propagation probe must be inside the room air volume");
  }

  private LightState lightState(Level level) {
    var state = level.getBlockState(lamp);
    return new LightState(level.getGameTime(), state.toString(), level.getBlockState(power).toString(),
        level.getBlockState(lamp.west()).toString(),
        level.hasNeighborSignal(lamp), level.getLightEmission(lamp), level.getBrightness(LightLayer.BLOCK, lamp),
        level.getBrightness(LightLayer.BLOCK, lamp.west()), level.getBrightness(LightLayer.SKY, lamp.west()),
        level.getLightEngine().hasLightWork());
  }

  private Actor spawnActor() {
    actor = EntityTypes.SHEEP.create(originalServer.overworld(), EntitySpawnReason.COMMAND);
    check(actor != null, "The native sheep factory rejected the remote update actor");
    var position = arrangement.exit().center().add(arrangement.exit().normal().scale(4)).add(0, -1, 0);
    actor.setPos(position.x, position.y, position.z);
    actor.setYRot(180);
    actor.setColor(DyeColor.LIME);
    actor.setNoAi(true);
    actor.setNoGravity(true);
    actor.setSilent(true);
    check(originalServer.overworld().addFreshEntity(actor), "The native level rejected the real remote actor");
    return new Actor(actor.getId(), actor.getUUID(), actor.getBoundingBox());
  }

  private boolean mutated() { if (!mutation.isDone()) return false; mutation.join(); return true; }

  private void observeTerrain(AABB bounds, Map<BlockPos, BlockState> expected) {
    check(uploads == null, "Previous world mutation is still under observation");
    expectedTerrain.clear();
    expectedTerrain.putAll(expected);
    uploads = new PortalTerrainUploadProbe(bounds);
  }

  private boolean terrainReady() {
    return portalReady() && expectedTerrain.entrySet().stream()
        .allMatch(entry -> client.level.getBlockState(entry.getKey()).equals(entry.getValue()))
        && PortalTopology.links(client.level).stream().anyMatch(link -> link.gun().equals(fixture.gunId())
            && link.generation() == arrangement.generation() && link.entrance().endpoint().equals(arrangement.entrance())
            && uploads.complete(link));
  }

  private boolean portalReady() {
    return PortalTopology.links(client.level).stream().anyMatch(link -> link.gun().equals(fixture.gunId())
        && link.generation() == arrangement.generation() && link.entrance().endpoint().equals(arrangement.entrance())
        && PortalOpeningReadiness.fullyOpen(link) && PortalViewTerrainReadiness.complete(link));
  }

  private boolean capture(String name, Consumer<FrameCapture.Frame> assertion) {
    if (screenshot == null) {
      screenshot = frames.reservedCheckpoint(name, () -> uploads == null || terrainReady());
      return false;
    }
    if (!screenshot.isDone()) return false;
    var frame = screenshot.join();
    assertion.accept(frame);
    if (uploads != null) {
      terrainProof.add(Map.of("checkpoint", name, "frameId", frame.id(), "expectedBlocks", expectedTerrain.size(),
          "completedUploads", uploads.diagnostics()));
      uploads.close();
      uploads = null;
      expectedTerrain.clear();
    }
    screenshot = null;
    return true;
  }

  private FrameCapture.Region region(FrameCapture.Frame image, AABB bounds) {
    return image.project(arrangement.imageBounds(bounds)).orElseThrow(() ->
        new AssertionError("The authored remote update region is outside the main window: " + bounds));
  }

  private void writeUploadProof() {
    try {
      Files.createDirectories(frames.directory());
      Files.writeString(frames.directory().resolve("terrain-upload-proof.json"),
          new GsonBuilder().setPrettyPrinting().create().toJson(terrainProof));
    } catch (IOException failure) { throw new UncheckedIOException(failure); }
  }

  private void next(Step next) { step = next; age = 0; }
  public List<String> passed() { return List.copyOf(passed); }

  @Override public CompletableFuture<Void> cleanup() {
    if (uploads != null) uploads.close();
    if (originalServer.isShutdown()) return super.cleanup();
    var remove = originalServer.submit(() -> { if (actor != null) actor.discard(); });
    return CompletableFuture.allOf(remove, super.cleanup());
  }
}
