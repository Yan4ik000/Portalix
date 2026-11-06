package dev.portalix.client.compat.sodium;

import dev.portalix.client.mixin.compat.sodium.NormalPlanesAccessor;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.quad.TQuad;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.trigger.GeometryPlanes;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.trigger.NormalPlanes;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/** Immutable ordering cells shared by the cameras observing a mesh. */
public final class SodiumSortPlanes {
  private final Plane[] planes;

  public SodiumSortPlanes(GeometryPlanes geometry) {
    this(geometry, null);
  }

  public SodiumSortPlanes(GeometryPlanes geometry, TQuad[] topology) {
    var result = new ArrayList<Plane>();
    if (geometry.getAligned() != null)
      for (var normal : geometry.getAligned()) if (normal != null) result.add(plane(normal));
    if (geometry.getUnaligned() != null)
      for (var normal : geometry.getUnaligned()) result.add(plane(normal));
    if (topology != null) {
      var accurate = new HashMap<Vector3fc, FloatArrayList>();
      for (var quad : topology)
        accurate
            .computeIfAbsent(quad.getAccurateNormal(), ignored -> new FloatArrayList())
            .add(quad.getAccurateDotProduct());
      for (var entry : accurate.entrySet()) {
        float[] distances = entry.getValue().toFloatArray();
        Arrays.sort(distances);
        result.add(new Plane(new Vector3f(entry.getKey()), distances));
      }
    }
    planes = result.toArray(Plane[]::new);
  }

  private static Plane plane(NormalPlanes planes) {
    var access = (NormalPlanesAccessor) planes;
    return new Plane(access.portalix$normal(), access.portalix$distances());
  }

  public boolean crossed(Vector3fc from, Vector3fc to) {
    return crossed(from, to.x(), to.y(), to.z());
  }

  public boolean crossed(Vector3fc from, float x, float y, float z) {
    for (var plane : planes) {
      float a = plane.normal.dot(from), b = plane.normal.dot(x, y, z);
      if (a == b) continue;
      float min = Math.min(a, b), max = Math.max(a, b);
      int index = Arrays.binarySearch(plane.distances, min);
      if (index < 0) index = -index - 1;
      if (index < plane.distances.length && plane.distances[index] <= max) return true;
    }
    return false;
  }

  private record Plane(Vector3fc normal, float[] distances) {}
}
