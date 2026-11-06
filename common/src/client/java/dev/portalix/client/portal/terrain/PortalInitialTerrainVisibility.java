package dev.portalix.client.portal.terrain;

import dev.portalix.client.mixin.terrain.ViewAreaAccessor;
import dev.portalix.client.mixin.terrain.vanilla.FrustumAccessor;
import dev.portalix.client.portal.clip.PortalCullPlane;
import dev.portalix.client.portal.clip.PortalFrustumState;
import it.unimi.dsi.fastutil.ints.IntArrays;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.renderer.Octree;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkTrackingView;
import org.joml.FrustumIntersection;

/** Immediate connectivity without materializing render sections for the air between meshes. */
public final class PortalInitialTerrainVisibility {
  private static final byte UNSEEN = 0;
  private static final byte OUTSIDE_FRUSTUM = 1;
  private static final byte IN_FRUSTUM = 2;
  private static final byte REACHED = 3;
  private static final Direction[] DIRECTIONS = Direction.values();
  private byte[] states = new byte[0];
  private byte[] incomingDirections = new byte[0];
  private byte[] pathDirections = new byte[0];
  private int[] queue = new int[0];
  private long[][] columns = new long[0][];

  public void collect(
      ViewArea area,
      PortalTerrainColumns terrain,
      CameraRenderState camera,
      PortalCullPlane plane,
      Frustum frustum,
      List<RenderSection> visible,
      List<RenderSection> nearby) {
    var center = SectionPos.of(camera.pos);
    int radius = area.getViewDistance();
    int diameter = radius * 2 + 1;
    int height = area.sectionCount();
    int minX = center.x() - radius;
    int minZ = center.z() - radius;
    int minY = area.minSectionY();
    int columnCount = diameter * diameter;
    int count = columnCount * height;
    if (states.length < count) {
      states = new byte[count];
      incomingDirections = new byte[count];
      pathDirections = new byte[count];
      queue = new int[count];
    } else Arrays.fill(states, 0, count, UNSEEN);
    if (columns.length < columnCount) columns = new long[columnCount][];
    for (int x = 0; x < diameter; x++)
      for (int z = 0; z < diameter; z++)
        columns[x * diameter + z] =
            ChunkTrackingView.isInViewDistance(center.x(), center.z(), radius, minX + x, minZ + z)
                ? terrain.get(minX + x, minZ + z)
                : null;

    var cull = new Frustum(frustum).offsetToFullyIncludeCameraCube(8);
    var window = new Frustum(cull);
    ((PortalFrustumState) window).portalix$exitPlane(null);
    var tree = new Octree(center, radius, height, area.minY());
    var sections = ((ViewAreaAccessor) area).portalix$sections();
    int tail = 0;
    int cameraColumn = radius * diameter + radius;
    if (center.y() >= minY && center.y() <= area.maxSectionY() && columns[cameraColumn] != null) {
      int index = cameraColumn * height + center.y() - minY;
      states[index] = REACHED;
      incomingDirections[index] = 0;
      pathDirections[index] = 0;
      queue[tail++] = index;
    } else {
      boolean below = center.y() < minY;
      int y = below ? 0 : height - 1;
      var source = below ? Direction.UP : Direction.DOWN;
      for (int x = 0; x < diameter; x++)
        for (int z = 0; z < diameter; z++) {
          int column = x * diameter + z;
          if (columns[column] == null) continue;
          int index = column * height + y;
          incomingDirections[index] = (byte) (1 << source.ordinal());
          int mask = incomingDirections[index];
          if (x != radius) mask |= 1 << (x > radius ? Direction.EAST : Direction.WEST).ordinal();
          if (z != radius) mask |= 1 << (z > radius ? Direction.SOUTH : Direction.NORTH).ordinal();
          pathDirections[index] = (byte) mask;
          states[index] = REACHED;
          queue[tail++] = index;
        }
      IntArrays.quickSort(
          queue,
          0,
          tail,
          (first, second) -> {
            double firstDistance = seedDistance(first, diameter, height, minX, minY, minZ, camera);
            double secondDistance =
                seedDistance(second, diameter, height, minX, minY, minZ, camera);
            int comparison = Double.compare(firstDistance, secondDistance);
            return comparison != 0 ? comparison : Integer.compare(first, second);
          });
    }

    for (int head = 0; head < tail; head++) {
      int index = queue[head];
      int column = index / height;
      int x = column / diameter;
      int z = column % diameter;
      int y = index % height;
      int blockX = (minX + x) * 16;
      int blockY = (minY + y) * 16;
      int blockZ = (minZ + z) * 16;
      long[] occupied = columns[column];
      int sectionIndex = minY + y - terrain.minY();
      boolean empty = (occupied[sectionIndex / Long.SIZE] & 1L << (sectionIndex % Long.SIZE)) == 0;
      SectionMesh mesh = null;
      boolean open = empty;
      if (!empty) {
        long node = SectionPos.asLong(minX + x, minY + y, minZ + z);
        var section = sections.getValue(node);
        mesh = section.getSectionMesh();
        open =
            PortalOcclusionOpenings.contains(node)
                || plane != null
                    && !plane.intersects(
                        blockX, blockY, blockZ, blockX + 16, blockY + 16, blockZ + 16);
        tree.add(section);
      }
      for (var direction : DIRECTIONS) {
        int nextX = x + direction.getStepX();
        int nextY = y + direction.getStepY();
        int nextZ = z + direction.getStepZ();
        if (nextX < 0
            || nextX >= diameter
            || nextZ < 0
            || nextZ >= diameter
            || nextY < 0
            || nextY >= height
            || Math.abs(minY + nextY - center.y()) > radius) continue;
        int nextColumn = nextX * diameter + nextZ;
        if (columns[nextColumn] == null) continue;
        int next = nextColumn * height + nextY;
        if (states[next] == UNSEEN) {
          int bx = (minX + nextX) * 16;
          int by = (minY + nextY) * 16;
          int bz = (minZ + nextZ) * 16;
          int intersection =
              ((FrustumAccessor) window)
                  .portalix$cubeInFrustum(bx, by, bz, bx + 16, by + 16, bz + 16);
          states[next] =
              intersection == FrustumIntersection.INSIDE || intersection == FrustumIntersection.INTERSECT
                  ? IN_FRUSTUM
                  : OUTSIDE_FRUSTUM;
        }
        if (states[next] == OUTSIDE_FRUSTUM) continue;
        if (camera.smartCull) {
          if ((pathDirections[index] & 1 << direction.getOpposite().ordinal()) != 0) continue;
          if (!open && incomingDirections[index] != 0) {
            boolean connected = false;
            for (var source : DIRECTIONS)
              if ((incomingDirections[index] & 1 << source.ordinal()) != 0
                  && mesh.facesCanSeeEachother(source.getOpposite(), direction)) {
                connected = true;
                break;
              }
            if (!connected) continue;
          }
        }
        int bit = 1 << direction.ordinal();
        if (states[next] == REACHED) incomingDirections[next] |= (byte) bit;
        else {
          states[next] = REACHED;
          incomingDirections[next] = (byte) bit;
          pathDirections[next] = (byte) (pathDirections[index] | bit);
          queue[tail++] = next;
        }
      }
    }
    tree.visitNodes(
        (node, fullyVisible, depth, close) -> {
          var section = node.getSection();
          if (section != null) {
            visible.add(section);
            if (close) nearby.add(section);
          }
        },
        cull,
        32);
    Arrays.fill(columns, 0, columnCount, null);
  }

  private static double seedDistance(
      int index, int diameter, int height, int minX, int minY, int minZ, CameraRenderState camera) {
    int column = index / height;
    double x = (minX + column / diameter) * 16 + 8 - camera.blockPos.getX();
    double y = (minY + index % height) * 16 + 8 - camera.blockPos.getY();
    double z = (minZ + column % diameter) * 16 + 8 - camera.blockPos.getZ();
    return x * x + y * y + z * z;
  }
}
