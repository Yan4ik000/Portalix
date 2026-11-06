package dev.portalix.test;

import static dev.portalix.test.ServerRegressionSuite.check;

import dev.portalix.portal.PortalLink;
import dev.portalix.portal.PortalTopology;
import dev.portalix.portal.traversal.EntityPortalState;
import dev.portalix.portal.traversal.PortalCollisionWorld;
import dev.portalix.portal.traversal.PortalEntityCollisions;
import dev.portalix.portal.geometry.PortalAperture;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Observes normal level ticking; no entity tick or movement method is called by this test. */
final class EntityTickRegression implements ServerRegressionSuite.Scenario {
  private record Actor(ItemEntity item, UUID identity, PortalLink link) {}
  private final RegressionWorld world;
  private final List<Actor> actors = new ArrayList<>();
  private int started = -1;

  EntityTickRegression(RegressionWorld world) { this.world = world; }

  @Override
  public boolean tick() {
    int now = world.level.getServer().getTickCount();
    if (started < 0) {
      var links = new ArrayList<PortalLink>();
      for (Direction face : new Direction[] {Direction.EAST, Direction.UP}) {
        for (double speed : new double[] {.06, 2}) {
          int z = 144 + actors.size() * 16;
          var entrance = world.endpoint(Direction.NORTH, new Vec3(144, 104, z));
          var exit = world.endpoint(face, new Vec3(192, 104, z));
          world.surface(entrance, Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState());
          world.surface(exit, Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState());
          var link = world.link(entrance, exit);
          links.add(link);
          links.add(link.inverse());
          var item = world.item(entrance.center().add(entrance.normal().scale(.1)));
          item.getItem().setCount(23);
          item.setDeltaMovement(entrance.normal().scale(-speed));
          actors.add(new Actor(item, item.getUUID(), link));
        }
      }
      PortalTopology.updateLinks(world.level, links);
      started = now;
      return false;
    }
    check(now > started, "Native entity scenario did not advance the server clock");
    boolean complete = true;
    for (var actor : actors) {
      var item = actor.item();
      var crossing = EntityPortalState.of(item).lastCrossing();
      if (crossing == null) { complete = false; continue; }
      check(crossing.link().equals(actor.link()), "Naturally ticking item used the wrong portal pair");
      check(item.isAlive() && item.getUUID().equals(actor.identity()) && item.getItem().getCount() == 23
          && world.level.getEntity(actor.identity()) == item, "Normal level ticking lost or replaced the item");
      check(item.getDeltaMovement().dot(actor.link().exit().endpoint().normal()) > 0,
          "Normal level ticking lost the outgoing movement direction");
      var hull = item.getBoundingBox().deflate(1e-6);
      var intersecting = PortalEntityCollisions.intersectingPortal(item, hull, Vec3.ZERO);
      check(intersecting == null ? world.level.noCollision(item, hull)
              : !PortalCollisionWorld.query(item, intersecting, hull).intersects(hull),
          "Naturally ticking item arrived inside the exit backing wall");
      var exit = actor.link().exit().endpoint();
      if (PortalAperture.signedDistance(exit, hull.getCenter()) <= PortalAperture.supportRadius(hull, exit.normal()))
        complete = false;
    }
    check(complete || now - started < 20, "Normal server ticking never moved every item through its portal");
    return complete;
  }
}
