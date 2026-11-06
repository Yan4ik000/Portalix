package dev.portalix.test.client;

import static dev.portalix.test.client.ClientAssertions.check;

import dev.portalix.client.PortalixClient;
import dev.portalix.client.portal.scene.PortalSceneManager;
import dev.portalix.portal.PortalTopology;
import dev.portalix.portal.traversal.PortalItemRotation;
import dev.portalix.portal.traversal.EntityPortalState;
import dev.portalix.portal.geometry.PortalAperture;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;

final class EntityTraversalSystemScene implements ClientSystemScene {
  private enum Step { START, PREPARE, EMPTY, SPAWN, TRACKED, BEFORE, LAUNCH, CROSSING, EXIT, AFTER, REMOVE, REMOVED, ABSENT, DONE }
  private static final EntitySystemFixture.Kind[] KINDS = {EntitySystemFixture.Kind.ITEM,
      EntitySystemFixture.Kind.CHICKEN, EntitySystemFixture.Kind.SNOWBALL,
      EntitySystemFixture.Kind.ARROW, EntitySystemFixture.Kind.TRIDENT};
  private final Minecraft client;
  private final ScenarioFrames frames;
  private final UUID playerId;
  private final CameraType camera;
  private final EntitySceneView view;
  private final List<String> passed = new ArrayList<>();
  private Step step = Step.START;
  private int age;
  private int index;
  private CompletableFuture<EntitySystemFixture> preparation;
  private CompletableFuture<EntitySystemFixture.Ref> spawn;
  private CompletableFuture<Void> mutation;
  private CompletableFuture<EntitySystemFixture.Observation> observation;
  private CompletableFuture<FrameCapture.Frame> capture;
  private EntitySystemFixture fixture;
  private EntitySystemFixture.Ref actor;
  private EntitySystemFixture.Observation crossed;
  private FrameCapture.Frame empty;
  private AABB captureBounds;
  private FrameCapture.Frame before;
  private FrameCapture.Frame after;
  private AABB beforeBounds;
  private AABB afterBounds;
  private Map<String, Object> afterPresentation;

  EntityTraversalSystemScene(Minecraft client, ScenarioFrames frames) {
    this.client = client;
    this.frames = frames;
    playerId = client.player.getUUID();
    camera = client.options.getCameraType();
    view = new EntitySceneView(client, frames);
  }

  @Override public boolean tick() {
    if (++age >= 400) {
      view.record("timeout");
      throw new AssertionError("Native entity client scene timed out at " + step + "/" + index + ": " + view.diagnostics());
    }
    var server = client.getSingleplayerServer();
    switch (step) {
      case START -> {
        client.options.setCameraType(CameraType.FIRST_PERSON);
        preparation = server.submit(() -> new EntitySystemFixture(server.getPlayerList().getPlayer(playerId), true));
        next(Step.PREPARE);
      }
      case PREPARE -> {
        if (!preparation.isDone()) return false;
        fixture = preparation.join();
        if (client.player.position().distanceTo(EntitySystemFixture.OBSERVER) > .1
            || PortalixClient.portals().activeMask(fixture.gunId) != 3 || !view.ready()) return false;
        var links = PortalTopology.links(client.level).stream().filter(link -> link.gun().equals(fixture.gunId)
            && link.generation() == fixture.portalGeneration).toList();
        if (links.size() != 2 || links.stream().noneMatch(PortalSceneManager::hasRenderedView)
            || links.stream().anyMatch(link -> !PortalOpeningReadiness.fullyOpen(link))) return false;
        frames.monitor(true);
        next(Step.EMPTY);
      }
      case EMPTY -> {
        if (capture == null) capture = frames.checkpoint("entities-" + name() + "-empty");
        if (!capture.isDone()) return false;
        empty = capture.join();
        capture = null;
        spawn = server.submit(() -> fixture.spawn(KINDS[index], EntitySystemFixture.BLUE.add(0, 0, -1.5)));
        next(Step.SPAWN);
      }
      case SPAWN -> {
        if (!spawn.isDone()) return false;
        actor = spawn.join();
        next(Step.TRACKED);
      }
      case TRACKED -> {
        if (entity() == null || entity().position().distanceTo(EntitySystemFixture.BLUE) > 2 || age < 8) return false;
        if (entity() instanceof Projectile projectile)
          check(projectile.getOwner() == client.player, "Native projectile tracking lost the launch owner: " + name());
        next(Step.BEFORE);
      }
      case BEFORE -> {
        if (!capture("entities-" + name() + "-before")) return false;
        capture = frames.reservedCheckpoint("entities-" + name() + "-after", this::exitPresented);
        next(Step.LAUNCH);
      }
      case LAUNCH -> {
        if (mutation == null) {
          if (!frames.reservedReady()) return false;
          mutation = server.submit(() -> fixture.launch(actor));
        }
        if (!mutation.isDone()) return false;
        mutation.join(); mutation = null;
        next(Step.CROSSING);
      }
      case CROSSING -> {
        if (observation == null) observation = server.submit(() -> {
          var observed = fixture.observe(actor);
          if (observed.crossed()) {
            Entity entity = fixture.entity(actor);
            var crossing = EntityPortalState.of(entity).lastCrossing();
            check(crossing.link().gun().equals(fixture.gunId)
                    && crossing.link().generation() == fixture.portalGeneration
                    && crossing.link().entrance().endpoint().center().distanceTo(EntitySystemFixture.BLUE) < .001
                    && crossing.link().exit().endpoint().center().distanceTo(EntitySystemFixture.ORANGE) < .001,
                "Native server actor crossed a different portal generation or direction: " + name());
            if (entity instanceof Projectile projectile)
              check(projectile.getOwner() != null && projectile.getOwner().getUUID().equals(playerId),
                  "Native projectile crossing lost the authoritative launch owner: " + name());
          }
          return observed;
        });
        if (!observation.isDone()) return false;
        var observed = observation.join();
        observation = null;
        if (!observed.crossed()) return false;
        check(observed.alive() && observed.velocity().z < 0,
            "Native server crossing removed the entity or lost exit-side velocity: " + name());
        crossed = observed;
        next(Step.EXIT);
      }
      case EXIT -> {
        Entity entity = entity();
        if (entity == null || Math.abs(entity.getX() - EntitySystemFixture.ORANGE.x) > 1) return false;
        check(entity.position().distanceTo(EntitySystemFixture.BLUE) > 10,
            "Tracked entity remained in the entrance scene: " + name());
        if (entity instanceof ItemEntity item) {
          check(item.getItem().getCount() == 7, "Tracked item crossing changed its stack");
          if (Math.abs(PortalItemRotation.rotation(item).dot(crossed.rotation())) < .99999) return false;
          check(Math.abs(PortalItemRotation.rotation(item).dot(new Quaternionf())) < .1,
              "Tracked item did not receive the nonidentity portal rotation");
        }
        next(Step.AFTER);
      }
      case AFTER -> {
        if (!capture.isDone()) return false;
        after = capture.join(); capture = null;
        EntityScenePixels.visible(after, afterBounds, actor.kind(), false, empty);
        mutation = server.submit(() -> fixture.remove(actor));
        next(Step.REMOVE);
      }
      case REMOVE -> {
        if (!mutation.isDone()) return false;
        mutation.join(); mutation = null;
        next(Step.REMOVED);
      }
      case REMOVED -> {
        if (client.level.getEntity(actor.id()) != null) return false;
        next(Step.ABSENT);
      }
      case ABSENT -> {
        if (capture == null) capture = frames.checkpoint("entities-" + name() + "-removed-empty");
        if (!capture.isDone()) return false;
        var removed = capture.join();
        capture = null;
        var proofPath = frames.directory().resolve("entities-" + name() + "-visibility-proof.json");
        Map<String, Object> proof = new LinkedHashMap<>(Map.of(
            "before", EntityScenePixels.visible(before, beforeBounds, actor.kind(), false, empty, removed),
            "after", EntityScenePixels.visible(after, afterBounds, actor.kind(), false, empty, removed),
            "initialAbsence", empty.metadata(), "nativeRemovalAbsence", removed.metadata(), "actorUuid", actor.uuid(),
            "afterPresentation", afterPresentation));
        passed.add("entity-traversal/" + name() + " native ticks, observer identity, outgoing motion and visible seam");
        actor = null;
        EntityScenePixels.save(proofPath, proof);
        next(++index == KINDS.length ? Step.DONE : Step.EMPTY);
      }
      case DONE -> { return true; }
    }
    return step == Step.DONE;
  }

  private boolean capture(String name) {
    if (capture == null) capture = frames.reservedCheckpoint(name, () -> {
      var visible = entity();
      check(visible != null, "Tracked entity disappeared before its native frame copy: " + actor);
      float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
      captureBounds = visible.getBoundingBox().move(visible.getPosition(partial).subtract(visible.position())).inflate(.15);
      return true;
    });
    if (!capture.isDone()) return false;
    var image = capture.join();
    capture = null;
    var entity = entity();
    check(entity != null, "Tracked entity disappeared before its visible checkpoint: " + actor);
    EntityScenePixels.visible(image, captureBounds, actor.kind(), false, empty);
    before = image; beforeBounds = captureBounds;
    return true;
  }

  private boolean exitPresented() {
    Entity entity = entity();
    if (entity == null) return false;
    var crossing = EntityPortalState.of(entity).lastCrossing();
    if (crossing == null || !crossing.link().gun().equals(fixture.gunId)
        || crossing.link().generation() != fixture.portalGeneration
        || crossing.link().entrance().endpoint().center().distanceTo(EntitySystemFixture.BLUE) >= .001
        || crossing.link().exit().endpoint().center().distanceTo(EntitySystemFixture.ORANGE) >= .001
        || Math.abs(entity.getX() - EntitySystemFixture.ORANGE.x) > 1) return false;
    float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
    var renderFeet = entity.getPosition(partial);
    if (Math.abs(renderFeet.x - EntitySystemFixture.ORANGE.x) > 1
        || renderFeet.distanceTo(EntitySystemFixture.BLUE) <= 10) return false;
    var renderedBounds = entity.getBoundingBox().move(renderFeet.subtract(entity.position())).inflate(.15);
    var exit = crossing.link().exit().endpoint();
    var normal = exit.normal();
    double clearance = (normal.x >= 0 ? renderedBounds.minX - exit.center().x : renderedBounds.maxX - exit.center().x) * normal.x
        + (normal.y >= 0 ? renderedBounds.minY - exit.center().y : renderedBounds.maxY - exit.center().y) * normal.y
        + (normal.z >= 0 ? renderedBounds.minZ - exit.center().z : renderedBounds.maxZ - exit.center().z) * normal.z;
    if (clearance <= PortalAperture.PLANE_EPSILON) return false;
    if (entity instanceof Projectile projectile)
      check(projectile.getOwner() == client.player && projectile.getOwner().getUUID().equals(playerId),
          "The presented native projectile lost its original owner: " + name());
    afterBounds = renderedBounds;
    afterPresentation = Map.of("gun", crossing.link().gun(), "generation", crossing.link().generation(),
        "eventId", crossing.eventId(), "entityTick", entity.tickCount, "partialTick", partial,
        "physicalFeet", entity.position(), "renderFeet", renderFeet, "renderBounds", afterBounds,
        "actorUuid", entity.getUUID(), "exitPlaneClearance", clearance);
    return true;
  }

  private Entity entity() {
    Entity entity = client.level.getEntity(actor.id());
    if (entity == null) return null;
    check(entity.getUUID().equals(actor.uuid()), "Native client tracking reused an actor id with the wrong UUID");
    return entity;
  }

  private String name() { return KINDS[index].name().toLowerCase(java.util.Locale.ROOT); }
  private void next(Step step) { this.step = step; age = 0; }
  @Override public List<String> passed() { return List.copyOf(passed); }
  @Override public CompletableFuture<Void> cleanup() {
    client.options.setCameraType(camera);
    if (preparation == null) return CompletableFuture.completedFuture(null);
    var server = client.getSingleplayerServer();
    return preparation.handle((owned, failure) -> owned).thenCompose(owned -> server.submit(() -> {
      if (owned != null) owned.close();
    }));
  }
}
