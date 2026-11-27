package dev.portalix.client.portal.terrain;

import com.mojang.blaze3d.vertex.CompactVectorArray;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public final class PortalDistanceSort {
  private PortalDistanceSort() {}

  public static Result sort(MeshData.SortState state, Vector3fc camera, Result previous) {
    if (previous != null && ordered(state.centroids(), camera, previous.order)) return previous;
    int[] order = VertexSorting.byDistance(camera).sort(state.centroids());
    byte[] indices = new byte[Math.multiplyExact(order.length, 6 * state.indexType().bytes)];
    var target = ByteBuffer.wrap(indices).order(ByteOrder.nativeOrder());
    for (int quad : order) {
      int vertex = quad * 4;
      if (state.indexType() == VertexFormat.IndexType.SHORT) {
        target.putShort((short) vertex).putShort((short) (vertex + 1)).putShort((short) (vertex + 2))
            .putShort((short) (vertex + 2)).putShort((short) (vertex + 3)).putShort((short) vertex);
      } else {
        target.putInt(vertex).putInt(vertex + 1).putInt(vertex + 2)
            .putInt(vertex + 2).putInt(vertex + 3).putInt(vertex);
      }
    }
    return new Result(order, indices);
  }

  private static boolean ordered(CompactVectorArray centers, Vector3fc camera, int[] order) {
    var point = new Vector3f();
    float previousDistance = Float.POSITIVE_INFINITY;
    int previousIndex = -1;
    for (int index : order) {
      float distance = camera.distanceSquared(centers.get(index, point));
      int comparison = Float.compare(distance, previousDistance);
      if (comparison > 0 || comparison == 0 && index < previousIndex) return false;
      previousDistance = distance;
      previousIndex = index;
    }
    return true;
  }

  public record Result(int[] order, byte[] indices) {}
}
