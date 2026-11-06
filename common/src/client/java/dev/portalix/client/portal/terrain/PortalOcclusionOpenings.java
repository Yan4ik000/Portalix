package dev.portalix.client.portal.terrain;

import dev.portalix.portal.PortalLink;
import dev.portalix.portal.geometry.PortalAperture;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import java.util.List;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class PortalOcclusionOpenings {
  private static List<PortalLink> previous = List.of();
  private static ResourceKey<Level> dimension;
  private static volatile LongSet sections = LongSets.emptySet();

  private PortalOcclusionOpenings() {}

  public static boolean update(ResourceKey<Level> nextDimension, List<PortalLink> links) {
    if (dimension == nextDimension && previous == links) return false;
    dimension = nextDimension;
    previous = links;
    var next = new LongOpenHashSet();
    for (var link : links) {
      var portal = link.entrance().endpoint();
      if (!portal.dimension().equals(nextDimension)) continue;
      var right = portal.right().scale(portal.width() / 2 + .5);
      var up = portal.up().scale(portal.height() / 2 + .5);
      var depth = portal.normal().scale(PortalAperture.CAVITY_DEPTH);
      double rx = Math.abs(right.x) + Math.abs(up.x) + Math.abs(depth.x);
      double ry = Math.abs(right.y) + Math.abs(up.y) + Math.abs(depth.y);
      double rz = Math.abs(right.z) + Math.abs(up.z) + Math.abs(depth.z);
      var center = portal.center();
      for (int x = SectionPos.posToSectionCoord(center.x - rx);
          x <= SectionPos.posToSectionCoord(center.x + rx);
          x++)
        for (int y = SectionPos.posToSectionCoord(center.y - ry);
            y <= SectionPos.posToSectionCoord(center.y + ry);
            y++)
          for (int z = SectionPos.posToSectionCoord(center.z - rz);
              z <= SectionPos.posToSectionCoord(center.z + rz);
              z++) next.add(SectionPos.asLong(x, y, z));
    }
    if (sections.equals(next)) return false;
    sections = next;
    return true;
  }

  public static boolean contains(long section) {
    return sections.contains(section);
  }

  public static LongSet sections() {
    return sections;
  }
}
