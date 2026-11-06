package dev.portalix.client.portal.terrain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.vertex.CompactVectorArray;
import com.mojang.blaze3d.vertex.MeshData;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class PortalDistanceSortTest {
  @Test
  void translucentQuadsRemainFarToNearWithValidTriangles() {
    var centers = new CompactVectorArray(5);
    float[] positions = {-3, 2, 2, 10, -20};
    for (int index = 0; index < positions.length; index++) centers.set(index, positions[index], 0, 0);
    for (var indexType : IndexType.values()) {
      var state = new MeshData.SortState(centers, indexType);
      var camera = new Vector3f();
      var result = PortalDistanceSort.sort(state, camera, null);
      assertTrue(Arrays.equals(result.order(), new int[] {4, 3, 0, 1, 2}), "terrain sorted far to near, stable for equal distances");
      var indices = ByteBuffer.wrap(result.indices()).order(ByteOrder.nativeOrder());
      assertTrue(indices.remaining() == 5 * 6 * indexType.bytes, "complete quad index buffer for " + indexType);
      for (int quad : result.order()) {
        for (int corner : new int[] {0, 1, 2, 2, 3, 0}) {
          int vertex = indexType == IndexType.SHORT ? Short.toUnsignedInt(indices.getShort()) : indices.getInt();
          assertEquals(quad * 4 + corner, vertex, "two nondegenerate triangles preserve native winding");
        }
      }
      var reverse = PortalDistanceSort.sort(state, new Vector3f(40, 0, 0), result);
      assertTrue(Arrays.equals(reverse.order(), new int[] {4, 0, 1, 2, 3}),
          "crossing distance order rebuilds translucent indices");
    }
  }
}
