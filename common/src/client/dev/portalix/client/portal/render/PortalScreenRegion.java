package dev.portalix.client.portal.render;

import java.util.List;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

public record PortalScreenRegion(int x, int y, int width, int height) {
  public static PortalScreenRegion full(int width, int height) {
    return new PortalScreenRegion(0, 0, width, height);
  }

  static PortalScreenRegion project(
      List<PortalSurfaceMesh.Vertex> mesh, Matrix4fc projection, int width, int height) {
    float left = 1, bottom = 1, right = -1, top = -1;
    var clip = new Vector4f();
    for (var vertex : mesh) {
      var point = vertex.point();
      projection.transform(clip.set((float) point.x, (float) point.y, (float) point.z, 1));
      if (clip.w <= 1.0e-5F) return full(width, height);
      float x = clip.x / clip.w, y = clip.y / clip.w;
      left = Math.min(left, x);
      right = Math.max(right, x);
      bottom = Math.min(bottom, y);
      top = Math.max(top, y);
    }
    // Keep neighbouring texels for linear filtering and round the raster bounds outwards.
    int x = Math.clamp((int) Math.floor((left + 1) * .5 * width) - 1, 0, width - 1);
    int y = Math.clamp((int) Math.floor((bottom + 1) * .5 * height) - 1, 0, height - 1);
    int endX = Math.clamp((int) Math.ceil((right + 1) * .5 * width) + 1, x + 1, width);
    int endY = Math.clamp((int) Math.ceil((top + 1) * .5 * height) + 1, y + 1, height);
    return new PortalScreenRegion(x, y, endX - x, endY - y);
  }

  public PortalScreenRegion union(PortalScreenRegion other) {
    if (other == null || other == this) return this;
    int left = Math.min(x, other.x);
    int bottom = Math.min(y, other.y);
    int right = Math.max(x + width, other.x + other.width);
    int top = Math.max(y + height, other.y + other.height);
    return new PortalScreenRegion(left, bottom, right - left, top - bottom);
  }

  public PortalScreenRegion clip(int width, int height) {
    int right = x + this.width, top = y + this.height;
    if (right <= width && top <= height) return this;
    right = Math.min(right, width);
    top = Math.min(top, height);
    return right > x && top > y ? new PortalScreenRegion(x, y, right - x, top - y) : null;
  }
}
