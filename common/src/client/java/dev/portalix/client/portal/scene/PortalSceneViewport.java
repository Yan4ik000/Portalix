package dev.portalix.client.portal.scene;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

record PortalSceneViewport(int x, int y, int width, int height, int screenWidth, int screenHeight) {
  boolean isFullScreen() {
    return x == 0 && y == 0 && width == screenWidth && height == screenHeight;
  }

  static Matrix4f cullingProjection(
      Matrix4fc projection, float near, float far, boolean zeroToOne) {
    float scale = (zeroToOne ? far : far + near) / (far - near);
    float offset = -(zeroToOne ? far : 2 * far) * near / (far - near);
    var depth = projection.getRow(3, new Vector4f()).mul(scale);
    depth.w += offset;
    return new Matrix4f(projection).setRow(2, depth);
  }

  static PortalSceneViewport of(PortalScreenVisibility.Bounds bounds, int width, int height) {
    int x = Math.max(0, (int) Math.floor((bounds.left() + 1) * .5 * width - 1));
    int y = Math.max(0, (int) Math.floor((bounds.bottom() + 1) * .5 * height - 1));
    int right = Math.min(width, (int) Math.ceil((bounds.right() + 1) * .5 * width + 1));
    int top = Math.min(height, (int) Math.ceil((bounds.top() + 1) * .5 * height + 1));
    int w = Math.min(width, ((Math.max(1, right - x) + 63) / 64) * 64);
    int h = Math.min(height, ((Math.max(1, top - y) + 63) / 64) * 64);
    return new PortalSceneViewport(
        Math.min(x, width - w), Math.min(y, height - h), w, h, width, height);
  }

  PortalSceneViewport retainSize(int previousWidth, int previousHeight) {
    int w = allocationSize(width, previousWidth, screenWidth);
    int h = allocationSize(height, previousHeight, screenHeight);
    return new PortalSceneViewport(
        Math.min(x, screenWidth - w),
        Math.min(y, screenHeight - h),
        w,
        h,
        screenWidth,
        screenHeight);
  }

  private static int allocationSize(int required, int previous, int maximum) {
    if (required > previous) return Math.min(maximum, Math.max(required, previous * 2));
    if (required <= previous / 4) return Math.min(maximum, Integer.highestOneBit(required) * 2);
    return Math.min(maximum, previous);
  }

  Matrix4f projection(Matrix4fc original) {
    return new Matrix4f()
        .scaling((float) screenWidth / width, (float) screenHeight / height, 1)
        .m30((float) (screenWidth - 2 * x - width) / width)
        .m31((float) (screenHeight - 2 * y - height) / height)
        .mul(original);
  }

  Vector4f from(PortalSceneViewport parent) {
    return new Vector4f(
        (float) parent.width / width,
        (float) parent.height / height,
        (float) (parent.x - x) / width,
        (float) (parent.y - y) / height);
  }
}
